# 负载均衡架构

本文详述 zRPC 四种负载均衡策略的算法原理与适用场景。

---

## 统一接口

```java
public interface LoadBalance {
    <T> Invoker<T> select(List<Invoker<T>> invokers, RpcRequest request);
}
```

所有策略继承 `AbstractLoadBalance`，后者负责空列表检查和单节点快速返回：

```java
public abstract class AbstractLoadBalance implements LoadBalance {
    public final <T> Invoker<T> select(List<Invoker<T>> invokers, RpcRequest request) {
        if (invokers.size() == 1) return invokers.get(0);  // 快速路径
        return doSelect(invokers, request);
    }
    protected abstract <T> Invoker<T> doSelect(List<Invoker<T>> invokers, RpcRequest request);
}
```

权重通过 URL 参数 `weight`（`URLKeys.WEIGHT`）配置，默认 `100`。

---

## 平滑加权轮询（RoundRobin）

**SPI 名**：`roundrobin`（默认）

### 算法原理

参考 Nginx 的平滑加权轮询（Smooth Weighted Round-Robin, SWRR）：

每个节点维护一个"当前权重"（`currentWeight`），初始为 0：
1. 每轮开始，所有节点的 `currentWeight += weight`
2. 选出 `currentWeight` 最大的节点
3. 被选中节点的 `currentWeight -= totalWeight`

**示例**（权重 [5, 1, 1]，total = 7）：

| 轮次 | 前 current | 选中 | 后 current |
|---|---|---|---|
| 1 | [5, 1, 1] | A(5) | [-2, 1, 1] |
| 2 | [3, 2, 2] | A(3) | [-4, 2, 2] |
| 3 | [1, 3, 3] | B(3) | [1, -4, 3] |
| 4 | [6, -3, 4] | A(6) | [-1, -3, 4] |
| 5 | [4, -2, 5] | C(5) | [4, -2, -2] |
| 6 | [9, -1, -1] | A(9) | [2, -1, -1] |
| 7 | [7, 0, 0] | A(7) | [0, 0, 0] |

7 轮中 A 被选 5 次、B/C 各 1 次，与权重 5:1:1 完全一致，且分布平滑（避免 A 连续 5 次）。

### 代码关键路径

```java
// 每个 (serviceKey, addr) 对应一个 WeightedRoundRobin 对象
for (Invoker<T> invoker : invokers) {
    WeightedRoundRobin wrr = map.computeIfAbsent(addr, k -> new WeightedRoundRobin(weight));
    int current = wrr.increaseCurrent();   // currentWeight += weight（AtomicInteger）
    if (current > maxCurrent) {
        maxCurrent = current;
        selected = invoker;
    }
}
selected.wrr.sel(totalWeight);   // currentWeight -= totalWeight
```

### 适用场景

- **通用默认选择**，节点配置相似时效果最佳
- 权重差异较大时也能保证平滑分布，不会出现突发流量

---

## 加权随机（Random）

**SPI 名**：`random`

### 算法原理

以权重为概率分布，随机落点决定选中哪个节点：

```
节点 A(weight=3), B(weight=1), C(weight=2), totalWeight=6

[0, 3) → A
[3, 4) → B
[4, 6) → C

random.nextInt(6) = 2 → A
random.nextInt(6) = 3 → B
random.nextInt(6) = 5 → C
```

### 代码关键路径

```java
int offset = ThreadLocalRandom.current().nextInt(totalWeight);
for (int i = 0; i < invokers.size(); i++) {
    offset -= weights[i];
    if (offset < 0) return invokers.get(i);
}
```

等权重时退化为纯随机（`nextInt(size)`），无额外开销。

### 适用场景

- 节点**权重差异很大**时（如新旧机器性能差异显著）
- 对分布精确性要求不高，追求实现简单
- **不适合**请求量极少时（样本少，随机误差较大）

---

## 一致性哈希（ConsistentHash）

**SPI 名**：`consistenthash`

### 算法原理

使用虚拟节点的一致性哈希环（Ketama 算法）：

1. 每个真实节点生成 **160 个虚拟节点**（40 个 MD5 × 每个 MD5 取 4 个 hash 值）
2. 虚拟节点均匀分布在 long 范围的哈希环上
3. 请求的哈希值（默认取 `methodName + args[0]`）在环上**顺时针**找最近的虚拟节点

```
哈希环（0 ~ Long.MAX_VALUE）：

    0
    │
   A:0         （虚拟节点 A-node-0 的 hash 0）
    │
   B:12         （虚拟节点 B-node-0 的 hash 12）
    │
   A:31         （虚拟节点 A-node-1 的 hash 31）
    │
  ...

请求 hash=20 → 顺时针找 → A:31 → 路由到节点 A
```

### 虚拟节点计算

```java
for (int i = 0; i < 40; i++) {
    byte[] digest = md5(addr + "-node-" + i);
    for (int j = 0; j < 4; j++) {
        long hash = ((long)(digest[3 + j*4] & 0xFF) << 24) | ...;
        ring.put(hash, invoker);   // TreeMap 保持有序
    }
}
```

160 个虚拟节点可以保证在节点增删时，平均只有 `1/n` 的请求重新路由（n = 节点数）。

### 哈希键构建

```java
private String buildHashKey(RpcRequest request) {
    return request.getMethodName() +
           (request.getArguments()[0] != null ? request.getArguments()[0].toString() : "");
}
```

> 可以自定义 `buildHashKey` 逻辑（如使用 userId、sessionId）以实现更精确的粘性路由。

### 节点变更时的影响

| 事件 | 影响范围 |
|---|---|
| 新增节点 N | 只有 `predecessor(N)` 到 `N` 之间的请求重新路由到 N |
| 移除节点 N | 只有路由到 N 的请求转移到 N 的后继节点 |

相比取模哈希（`hash % n`），增删节点时**只影响约 1/n 的请求**，而非全量重散列。

### 适用场景

- **缓存服务**：同一 key 始终路由到同一节点，提升缓存命中率
- **有状态服务**：需要会话亲和性（Session Affinity）
- **不适合**节点权重差异大的场景（标准一致性哈希不感知权重）

---

## 最少活跃调用数（LeastActive）

**SPI 名**：`leastactive`

### 算法原理

跟踪每个节点正在处理中（in-flight）的请求数，将新请求发往**最空闲**的节点：

```
节点 A：活跃数 = 5   （正在处理 5 个请求）
节点 B：活跃数 = 1   ← 选择此节点
节点 C：活跃数 = 3
```

当多个节点活跃数相同时，在这些节点中进行**加权随机**选择。

### 计数维护

```java
// 调用前（由框架或调用方手动触发）
LeastActiveLoadBalance.beginInvoke(serviceKey, addr);   // count++

// 调用后
LeastActiveLoadBalance.endInvoke(serviceKey, addr);     // count--
```

计数存储在 `ConcurrentHashMap<serviceKey, ConcurrentHashMap<addr, AtomicInteger>>` 中，支持多服务并发访问。

> **注意**：在当前 `RegistryDirectoryInvoker` 实现中，`beginInvoke/endInvoke` 尚未自动调用，
> 需在调用链中手动集成（或通过过滤器实现）。可以参考 `AccessLogFilter` 的模式添加计数过滤器。

### 适用场景

- 节点**处理能力不均**（如新旧机器性能差异、GC 导致某节点短暂变慢）
- 对**响应时间敏感**的服务（LeastActive 自动规避慢节点）
- 不适合只需均匀分发、不关心单节点负载的场景

---

## 策略选择指南

```
你的服务是否需要「粘性路由」（同一参数始终路由到同一节点）？
    是 → consistenthash

节点处理能力是否差异较大，或存在慢节点需要自动规避？
    是 → leastactive

是否追求最简实现，流量分配可以有一定随机性？
    是 → random

其他情况（通用默认）
    → roundrobin  ← 大多数场景首选
```

---

## 自定义负载均衡策略

参见 [SPI 扩展开发指南 — 自定义负载均衡](../api/spi.md#自定义负载均衡)。

核心只需继承 `AbstractLoadBalance` 并实现 `doSelect` 方法，
无需关心空列表检查和单节点快速路径，框架已处理。
