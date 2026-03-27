# SPI 扩展开发指南

zRPC 的所有核心组件均通过 SPI（Service Provider Interface）机制实现可插拔扩展。
本文介绍如何开发自定义扩展并接入框架。

---

## SPI 机制概述

zRPC 自研了一套类 Dubbo 的 SPI 机制（区别于 Java 标准 `ServiceLoader`）：

- **配置文件位置**：`META-INF/zrpc/{接口全限定名}`
- **配置格式**：`name=com.example.MyImplementation`（`name` 为小写，无连字符）
- **加载方式**：`ExtensionLoader.getLoader(InterfaceClass.class).getExtension("name")`
- **单例缓存**：同名扩展只实例化一次，线程安全

### 与 Java SPI 的区别

| 特性 | Java SPI | zRPC SPI |
|---|---|---|
| 配置目录 | `META-INF/services/` | `META-INF/zrpc/` |
| 配置格式 | 每行一个类名 | `name=className` |
| 默认实现 | 无 | `@SPI("defaultName")` |
| 实例化 | 每次加载新实例 | 单例缓存 |
| 按名获取 | ❌ | ✅ |

---

## 可扩展点列表

| 扩展点接口 | SPI 文件名后缀 | 默认实现 | 说明 |
|---|---|---|---|
| `Serializer` | `...codec.Serializer` | `json` | 序列化/反序列化 |
| `Protocol` | `...protocol.Protocol` | `zrpc` | 通信协议 |
| `Registry` | `...registry.Registry` | `memory` | 服务注册中心 |
| `LoadBalance` | `...loadbalance.LoadBalance` | `roundrobin` | 负载均衡策略 |
| `Filter` | `...filter.Filter` | — | 调用拦截器 |
| `ProxyFactory` | `...proxy.ProxyFactory` | `bytebuddy` | 动态代理生成 |

---

## 自定义序列化

### 1. 实现接口

```java
package com.example.codec;

import com.github.tgo18.zrpc.core.codec.Serializer;
import com.github.tgo18.zrpc.core.codec.SerializationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

/**
 * CBOR 二进制 JSON 序列化器，比 JSON 更紧凑。
 */
public class CborSerializer implements Serializer {

    private static final ObjectMapper MAPPER = new ObjectMapper(new CBORFactory());

    @Override
    public byte[] serialize(Object obj) throws SerializationException {
        try {
            return MAPPER.writeValueAsBytes(obj);
        } catch (Exception e) {
            throw new SerializationException("CBOR serialize failed", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws SerializationException {
        try {
            return MAPPER.readValue(bytes, clazz);
        } catch (Exception e) {
            throw new SerializationException("CBOR deserialize failed for " + clazz, e);
        }
    }

    @Override
    public String name() { return "cbor"; }
}
```

### 2. 注册到 SPI

在你的 jar 中创建文件：
`META-INF/zrpc/com.github.tgo18.zrpc.core.codec.Serializer`

```
cbor=com.example.codec.CborSerializer
```

### 3. 启用

```yaml
zrpc:
  protocol:
    serialization: cbor
```

---

## 自定义负载均衡

### 1. 继承 AbstractLoadBalance

```java
package com.example.loadbalance;

import com.github.tgo18.zrpc.loadbalance.AbstractLoadBalance;
import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.protocol.Invoker;

import java.util.List;

/**
 * IP Hash 负载均衡：根据调用方 IP 固定路由。
 */
public class IpHashLoadBalance extends AbstractLoadBalance {

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, RpcRequest request) {
        // 从 attachment 获取调用方 IP（由框架写入）
        String clientIp = request.getAttachment("client-ip", "0.0.0.0");
        int hash = Math.abs(clientIp.hashCode());
        return invokers.get(hash % invokers.size());
    }
}
```

### 2. 注册到 SPI

`META-INF/zrpc/com.github.tgo18.zrpc.core.loadbalance.LoadBalance`：

```
iphash=com.example.loadbalance.IpHashLoadBalance
```

### 3. 启用

```java
@ZRpcReference(version = "1.0", loadbalance = "iphash")
private UserService userService;
```

---

## 自定义注册中心

### 1. 实现 Registry 接口

```java
package com.example.registry;

import com.github.tgo18.zrpc.core.registry.*;
import redis.clients.jedis.*;

import java.util.*;

/**
 * Redis 注册中心（基于 Set 存储实例，PubSub 推送变更）。
 */
public class RedisRegistry implements Registry {

    private static final String PREFIX = "zrpc:instances:";
    private final JedisPool pool;

    public RedisRegistry(String host, int port) {
        this.pool = new JedisPool(host, port);
    }

    @Override
    public void register(ServiceInstance instance) {
        try (Jedis jedis = pool.getResource()) {
            String key = PREFIX + instance.getServiceName();
            jedis.sadd(key, toJson(instance));
            jedis.publish(key, "CHANGE");          // 通知订阅者
        }
    }

    @Override
    public void deregister(ServiceInstance instance) {
        try (Jedis jedis = pool.getResource()) {
            String key = PREFIX + instance.getServiceName();
            jedis.srem(key, toJson(instance));
            jedis.publish(key, "CHANGE");
        }
    }

    @Override
    public void subscribe(String serviceName, RegistryListener listener) {
        // 快照
        listener.onChanged(serviceName, getInstances(serviceName));
        // 订阅变更（生产代码应在独立线程中运行）
        new Thread(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        listener.onChanged(serviceName, getInstances(serviceName));
                    }
                }, PREFIX + serviceName);
            }
        }, "zrpc-redis-subscriber").start();
    }

    @Override
    public void unsubscribe(String serviceName, RegistryListener listener) { /* ... */ }

    @Override
    public List<ServiceInstance> getInstances(String serviceName) {
        try (Jedis jedis = pool.getResource()) {
            Set<String> members = jedis.smembers(PREFIX + serviceName);
            List<ServiceInstance> result = new ArrayList<>();
            for (String json : members) result.add(fromJson(json));
            return result;
        }
    }

    @Override
    public boolean isAvailable() {
        try (Jedis jedis = pool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) { return false; }
    }

    @Override
    public void destroy() { pool.close(); }

    private String toJson(ServiceInstance si) { /* JSON 序列化 */ return ""; }
    private ServiceInstance fromJson(String json) { /* JSON 反序列化 */ return null; }
}
```

### 2. 注册到 SPI

`META-INF/zrpc/com.github.tgo18.zrpc.core.registry.Registry`：

```
redis=com.example.registry.RedisRegistry
```

> **注意**：外部依赖（如 `jedis`）应在 `pom.xml` 中标记为 `<optional>true</optional>`，
> 避免强制引入不需要的依赖。

### 3. 使用

```yaml
zrpc:
  registry:
    type: redis
    address: 127.0.0.1:6379
```

> 当前 `ZRpcAutoConfiguration` 直接调用 `ExtensionLoader`，自定义注册中心的构造函数参数
> 需要通过 `address` 参数传入。若需更复杂的初始化，可参考 `ZookeeperRegistry` 的实现模式，
> 并在自定义 Spring Boot Starter 中覆盖 `ZRpcAutoConfiguration` 中相关 Bean。

---

## 自定义过滤器

```java
package com.example.filter;

import com.github.tgo18.zrpc.core.filter.Filter;
import com.github.tgo18.zrpc.core.common.*;
import com.github.tgo18.zrpc.core.protocol.Invoker;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单限流过滤器：基于令牌桶，每秒最多 1000 次调用。
 */
public class RateLimitFilter implements Filter {

    private final Semaphore semaphore = new Semaphore(1000);

    @Override
    public CompletableFuture<RpcResponse> invoke(Invoker<?> invoker, RpcRequest request) {
        if (!semaphore.tryAcquire()) {
            RpcResponse limited = RpcResponse.error(
                request.getRequestId(),
                RpcResponse.Status.CLIENT_ERROR,
                new RuntimeException("Rate limit exceeded")
            );
            return CompletableFuture.completedFuture(limited);
        }

        // 1 秒后释放令牌（简化实现，生产可用 Guava RateLimiter）
        return invoker.invoke(request).whenComplete((r, e) ->
            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
                             .execute(semaphore::release)
        );
    }
}
```

注册：`META-INF/zrpc/com.github.tgo18.zrpc.core.filter.Filter`：

```
ratelimit=com.example.filter.RateLimitFilter
```

---

## SPI 扩展开发清单

新增扩展时，需完成以下步骤：

- [ ] 实现对应 SPI 接口（或继承基类）
- [ ] 创建 `META-INF/zrpc/{接口FQCN}` 文件，写入 `name=className`
- [ ] SPI 名称使用**小写无连字符**格式
- [ ] 外部依赖设置为 `<optional>true</optional>`（避免传递依赖）
- [ ] 编写单元测试
- [ ] 在 `CLAUDE.md` 或相关文档中补充说明

---

## SPI 加载原理

```
ExtensionLoader.getExtension("json")
    │
    ├── 检查 instances 缓存（ConcurrentHashMap）
    │       ↓ 未命中
    ├── getExtensionClasses()
    │       ├── 读取 ClassLoader.getResources("META-INF/zrpc/{interface}")
    │       ├── 解析每行 name=className
    │       └── Class.forName(className) → 存入 extensionClasses Map
    │
    └── clazz.getDeclaredConstructor().newInstance()
            └── 存入 instances 缓存，返回
```

扩展类必须有**无参构造函数**。依赖注入可通过框架提供的工厂方法或 setter 手动完成。
