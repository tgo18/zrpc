# 注册中心架构

本文描述 zRPC 服务注册/发现的设计模型及三种注册中心实现。

---

## 注册/订阅模型

zRPC 采用**推模式**（Push-based）服务发现：注册中心主动通知订阅者，而非 Consumer 轮询。

```
Provider                 Registry                  Consumer
   │                        │                         │
   ├─ register(instance) ──►│                         │
   │                        │                         │
   │                        │◄── subscribe(service) ──┤
   │                        ├─── onChanged(instances) ►│（即时快照）
   │                        │                         │
   ├─ register(instance2) ──►│                         │
   │                        ├─── onChanged(instances) ►│（增量推送）
   │                        │                         │
   ├─ deregister(instance) ─►│                         │
   │                        ├─── onChanged(instances) ►│（节点下线）
```

### 核心接口

```java
public interface RegistryListener {
    // 每次调用传入完整实例列表（非增量），简化 Consumer 侧逻辑
    void onChanged(String serviceName, List<ServiceInstance> instances);
}
```

Consumer 收到 `onChanged` 后，`RegistryDirectoryInvoker` 会：
1. 对新增实例调用 `Protocol.refer()` 创建 Invoker
2. 销毁已消失实例对应的 Invoker（调用 `invoker.destroy()`）
3. 原子替换 `invokerList`

---

## 服务实例模型

```java
public class ServiceInstance {
    String serviceName;   // 接口全限定名
    String host;          // Provider IP
    int    port;          // Provider 端口
    String protocol;      // 使用的协议（zrpc / grpc / http2）
    String version;       // 服务版本
    String group;         // 服务分组
    int    weight;        // 负载均衡权重（默认 100）
    boolean enabled;      // 是否可用（false 时不参与路由）
    Map<String,String> metadata;  // 扩展元数据（序列化方式、应用名等）
}
```

`ServiceInstance.getId()` = `host:port`，用于去重。

---

## InMemoryRegistry

**适用场景**：单元测试、单机演示、不需要外部依赖的场景。

```
JVM 内部

┌────────────────────────────────────┐
│  store: ConcurrentHashMap          │
│    "com.Foo" → [instance1, ...]    │
│    "com.Bar" → [instance2, ...]    │
│                                    │
│  listeners: ConcurrentHashMap      │
│    "com.Foo" → [listener1, ...]    │
└────────────────────────────────────┘
```

**特性**：
- `CopyOnWriteArrayList` 存储监听器：写少读多，线程安全
- 注册/注销时同步触发 `notifyListeners()`
- 订阅时立即触发一次当前快照（避免消费端启动时错过已注册实例）

**局限性**：
- 仅限同一 JVM 进程，不支持多进程/分布式部署
- 重启后注册信息丢失

---

## ZookeeperRegistry

**适用场景**：生产分布式部署，强一致性要求。

### ZNode 结构

```
/zrpc（Curator namespace）
  └── /{serviceName}
        └── /providers
              ├── /{host:port}  [EPHEMERAL] → 序列化的 ServiceInstance JSON
              └── /{host:port}  [EPHEMERAL]
```

- **EPHEMERAL 节点**：Provider 会话断开时自动删除，实现服务自动下线
- **Curator namespace**：所有路径自动加 `/zrpc` 前缀，隔离不同框架数据

### 变更监听

使用 Curator 的 `PathChildrenCache`：

```
PathChildrenCache 监听 /providers 下的子节点增删改
    └── CHILD_ADDED   → 新实例上线
    └── CHILD_REMOVED → 实例下线（Provider 崩溃或主动注销）
    └── CHILD_UPDATED → 实例元数据更新（如权重调整）
```

每次变更触发完整实例列表重新加载（`getInstances(serviceName)`），通知所有订阅者。

### 连接配置

```java
CuratorFrameworkFactory.builder()
    .connectString("127.0.0.1:2181")
    .sessionTimeoutMs(30_000)     // 会话超时：Provider 连接断开后，EPHEMERAL 节点存活 30s
    .connectionTimeoutMs(10_000)  // 连接超时
    .retryPolicy(new ExponentialBackoffRetry(1000, 3)) // 断线自动重连
    .namespace("zrpc")
    .build();
```

### 脑裂处理

ZooKeeper 保证 CP（一致性 + 分区容忍）：
- ZK 集群 Leader 选举期间（通常 < 200ms），注册中心短暂不可用
- 此时 Consumer 使用**本地缓存**的 invokerList 继续提供服务
- `RegistryDirectoryInvoker.invoke()` 先检查 `invokerList.isEmpty()`，空时抛 `NO_INVOKER_AVAILABLE`

---

## NacosRegistry

**适用场景**：已使用 Nacos 做配置中心的微服务架构，统一运维。

### 与 ZooKeeper 的区别

| 维度 | ZooKeeper | Nacos |
|---|---|---|
| 一致性模型 | CP（强一致） | AP（最终一致，默认） |
| 健康检查 | 依赖 EPHEMERAL 节点 + 会话 | 心跳上报（5s 间隔） |
| 推送机制 | Watch + 事件通知 | 长轮询 + 推送 |
| 控制台 | 无（需第三方 zkUI） | 内置 Web 控制台 |

### 实例映射

| zRPC `ServiceInstance` | Nacos `Instance` |
|---|---|
| `host` | `ip` |
| `port` | `port` |
| `weight` | `weight` |
| `enabled` | `enabled` |
| `version`, `group`, `protocol` | `metadata["version"]` 等 |

### 分组（Group）

zRPC 使用固定 Group `"ZRPC"` 隔离框架数据与其他 Nacos 服务：

```java
namingService.registerInstance(serviceName, "ZRPC", nacosInstance);
namingService.selectInstances(serviceName, "ZRPC", true); // true=只选健康实例
```

### 订阅实现

```java
namingService.subscribe(serviceName, "ZRPC", event -> {
    if (event instanceof NamingEvent ne) {
        // Nacos 推送完整实例列表（过滤不健康实例）
        List<ServiceInstance> instances = ne.getInstances()
            .stream().map(this::fromNacosInstance).collect(toList());
        notifyListeners(serviceName, instances);
    }
});
```

---

## Consumer 端注册中心感知流程

```
Consumer 启动
    │
    ▼
ZRpcReferenceInjector 创建 RegistryDirectoryInvoker
    │
    ▼
registry.subscribe(serviceName, this)
    │
    ├── 即时回调 onChanged(serviceName, [instance1, instance2])
    │       └── getOrCreateInvoker(instance1) → protocol.refer() → NettyInvoker
    │       └── getOrCreateInvoker(instance2) → protocol.refer() → NettyInvoker
    │       └── invokerList = [nettyInvoker1, nettyInvoker2]
    │
    ├── （后续）Provider 上线 → onChanged([instance1, instance2, instance3])
    │       └── 创建 nettyInvoker3，加入 invokerList
    │
    └── （后续）Provider 下线 → onChanged([instance2, instance3])
            └── 销毁 nettyInvoker1（调用 invoker.destroy()）
            └── invokerList = [nettyInvoker2, nettyInvoker3]
```

整个更新过程通过 `volatile` + `CopyOnWrite` 保证无锁读：
- `invokerList` 声明为 `volatile List<Invoker<T>>`
- `onChanged()` 中构建完整新列表后一次性赋值（不是 add/remove）

---

## 注册中心高可用

### ZooKeeper 集群

```yaml
zrpc:
  registry:
    type: zookeeper
    address: zk1:2181,zk2:2181,zk3:2181   # 多地址逗号分隔
```

Curator 自动处理 Leader 切换和断线重连。

### Nacos 集群

```yaml
zrpc:
  registry:
    type: nacos
    address: nacos1:8848,nacos2:8848,nacos3:8848
```

Nacos Client 内置 VIP 负载均衡，自动切换可用节点。

### 注册中心故障时的行为

| 注册中心状态 | Consumer 行为 |
|---|---|
| 正常 | 使用实时 invokerList，实时感知节点变化 |
| 短暂断连（< sessionTimeout） | 继续使用最后一次有效的 invokerList |
| 长时间断连 | invokerList 可能过时（包含已下线节点），调用失败时 `NettyClient` 会触发重连尝试 |
| 完全不可用 + Provider 未重启 | Consumer 仍可用缓存列表提供服务（降级为静态路由） |
