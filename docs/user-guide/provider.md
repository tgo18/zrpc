# Provider 开发指南

本文详细介绍如何使用 zRPC 开发服务提供方（Provider）。

---

## 基础用法

### 1. 标注实现类

用 `@ZRpcService` 标注服务实现类。框架会在 Spring 容器启动后自动导出服务：

```java
@ZRpcService(interfaceClass = UserService.class, version = "1.0")
public class UserServiceImpl implements UserService {

    @Override
    public User getUser(long userId) {
        return userRepository.findById(userId);
    }
}
```

> `interfaceClass` 若省略，框架会自动取实现类的**第一个接口**。建议显式指定，避免歧义。

---

## 版本与分组

### 版本隔离

同一接口可以发布多个版本，Consumer 通过版本号选择对应实现：

```java
// v1 实现
@ZRpcService(interfaceClass = PaymentService.class, version = "1.0")
public class PaymentServiceV1Impl implements PaymentService { ... }

// v2 实现（新增功能，平行运行）
@ZRpcService(interfaceClass = PaymentService.class, version = "2.0")
public class PaymentServiceV2Impl implements PaymentService { ... }
```

Consumer 端：
```java
@ZRpcReference(version = "2.0")
private PaymentService paymentService; // 只会路由到 v2
```

### 分组隔离

分组用于环境/灰度隔离：

```java
@ZRpcService(interfaceClass = OrderService.class, group = "gray", version = "1.0")
public class OrderServiceGrayImpl implements OrderService { ... }
```

Consumer：
```java
@ZRpcReference(group = "gray", version = "1.0")
private OrderService orderService;
```

---

## 权重配置

权重影响负载均衡时被选中的概率（仅对 `roundrobin` 和 `random` 有效）：

```java
@ZRpcService(
    interfaceClass = SearchService.class,
    version = "1.0",
    weight = 200    // 该节点被选中概率是 weight=100 节点的 2 倍
)
public class SearchServiceImpl implements SearchService { ... }
```

---

## 超时配置

Provider 端可以为每个服务设置默认超时，Consumer 端的 timeout 优先级更高：

```java
@ZRpcService(
    interfaceClass = ReportService.class,
    version = "1.0",
    timeout = 10000   // 报表服务允许最长 10 秒
)
public class ReportServiceImpl implements ReportService { ... }
```

---

## 异步服务实现

Provider 实现方法可以直接返回 `CompletableFuture`，框架会正确处理异步结果：

```java
@ZRpcService(interfaceClass = AsyncDataService.class, version = "1.0")
public class AsyncDataServiceImpl implements AsyncDataService {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public CompletableFuture<Data> fetchData(String key) {
        return CompletableFuture.supplyAsync(() -> {
            // 耗时 IO 操作
            return dataClient.query(key);
        }, executor);
    }
}
```

> 接口定义需相应声明返回 `CompletableFuture<T>`。

---

## 不注册到注册中心

在某些场景下，你可能想启动服务但不向注册中心注册（如内部直连测试）：

```java
@ZRpcService(
    interfaceClass = InternalService.class,
    register = false   // 不写入注册中心
)
public class InternalServiceImpl implements InternalService { ... }
```

---

## 自定义过滤器（Provider 端）

实现 `Filter` 接口，在 SPI 文件中注册，即可插入 Provider 端调用链：

```java
// 1. 实现 Filter
public class AuthFilter implements Filter {

    @Override
    public CompletableFuture<RpcResponse> invoke(Invoker<?> invoker, RpcRequest request) {
        String token = request.getAttachment("auth-token");
        if (!tokenVerifier.verify(token)) {
            RpcResponse denied = RpcResponse.error(
                request.getRequestId(),
                RpcResponse.Status.CLIENT_ERROR,
                new SecurityException("Invalid token")
            );
            return CompletableFuture.completedFuture(denied);
        }
        return invoker.invoke(request);
    }
}
```

```
# 2. 注册到 SPI（META-INF/zrpc/com.github.tgo18.zrpc.core.filter.Filter）
auth=com.example.AuthFilter
```

---

## Provider 端上下文

在服务实现内部，可通过 `RpcContext` 获取调用方信息：

```java
@ZRpcService(interfaceClass = UserService.class, version = "1.0")
public class UserServiceImpl implements UserService {

    @Override
    public UserInfo getUser(long userId) {
        RpcContext ctx = RpcContext.getContext();
        String callerIp = ctx.getRemoteAddress() != null
            ? ctx.getRemoteAddress().getHost() : "unknown";
        String traceId  = ctx.getAttachment("trace-id");

        log.info("Called by {} traceId={}", callerIp, traceId);
        // ... 业务逻辑
    }
}
```

> **重要**：Provider 处理完请求后，框架会自动清理 RpcContext。若你在业务逻辑中手动操作，
> 确保最终调用 `RpcContext.removeContext()` 防止内存泄漏。

---

## Provider 启动流程（内部机制）

```
Spring 容器启动
    └── ZRpcServiceExporter.afterSingletonsInstantiated()
            ├── 扫描 @ZRpcService Bean
            ├── ProxyFactory.getInvoker(impl, type, url)  → AbstractProxyInvoker
            ├── Protocol.export(invoker)                   → 启动 Netty Server / gRPC Server
            └── Registry.register(instance)                → 写入注册中心
```

导出的服务可通过 `Exporter.unexport()` 在运行时下线。Spring 容器关闭时 `ZRpcServiceExporter.destroy()` 会自动调用。

---

## 常见问题

**Q：`@ZRpcService` 不生效，服务没有启动**

- 确认类被 Spring 管理（有 `@Component` 或被 `@Bean` 创建）
- 检查 `zrpc-spring-boot-starter` 是否在 classpath 上
- 检查 `application.yml` 是否有 `zrpc.protocol.port` 配置

**Q：服务在注册中心没出现**

- 若使用 `type: memory`，Provider 和 Consumer 必须在**同一个 JVM 进程**内
- 若使用 ZooKeeper，检查 ZK 连接地址和网络

**Q：多个接口如何只发布其中一个**

```java
@ZRpcService(interfaceClass = OrderService.class) // 明确指定，不发布其他接口
public class OrderServiceImpl implements OrderService, InternalHelper { ... }
```
