# Consumer 开发指南

本文详细介绍如何使用 zRPC 开发服务消费方（Consumer）。

---

## 基础用法

在 Spring Bean 的字段上标注 `@ZRpcReference`，框架会自动注入代理对象：

```java
@Service
public class OrderService {

    @ZRpcReference(version = "1.0")
    private UserService userService;

    public Order createOrder(long userId, List<Long> itemIds) {
        UserInfo user = userService.getUser(userId);   // 像调用本地方法一样
        // ...
    }
}
```

---

## 超时控制

### 全局超时

在 `application.yml` 中设置全局默认超时：

```yaml
zrpc:
  consumer:
    timeout: 3000  # 3 秒
```

### 单个引用超时

`@ZRpcReference.timeout` 优先级高于全局配置：

```java
@ZRpcReference(version = "1.0", timeout = 10000)  // 该引用独立使用 10 秒超时
private ReportService reportService;
```

超时后会抛出 `RpcException`（errorCode = `TIMEOUT_EXCEPTION`）：

```java
try {
    report = reportService.generate(params);
} catch (RpcException e) {
    if (e.isTimeout()) {
        // 超时降级处理
        return Report.empty();
    }
    throw e;
}
```

---

## 重试机制

默认重试 2 次（共发起 3 次请求）。仅在**幂等操作**（查询、初始化等）上开启重试：

```java
@ZRpcReference(version = "1.0", retries = 3)  // 查询接口可以重试
private SearchService searchService;

@ZRpcReference(version = "1.0", retries = 0)  // 支付/下单等写操作禁用重试
private PaymentService paymentService;
```

> 重试逻辑由 `RegistryDirectoryInvoker` 负责：每次重试会重新走负载均衡，优先选择不同节点。

---

## 负载均衡

### 全局策略

```yaml
zrpc:
  consumer:
    loadbalance: roundrobin
```

### 单个引用策略

```java
// 粘性路由：同一参数始终路由到同一节点
@ZRpcReference(version = "1.0", loadbalance = "consistenthash")
private CacheService cacheService;

// 最少活跃：将请求分配给当前最空闲的节点
@ZRpcReference(version = "1.0", loadbalance = "leastactive")
private HeavyComputeService computeService;
```

| 策略 | 名称 | 适用场景 |
|---|---|---|
| 平滑加权轮询 | `roundrobin` | 通用，默认推荐 |
| 加权随机 | `random` | 流量均衡，权重差异较大时 |
| 一致性哈希 | `consistenthash` | 需要粘性路由（缓存、会话） |
| 最少活跃 | `leastactive` | 节点处理能力不均，避免积压 |

---

## 异步调用

### 方式一：接口返回 `CompletableFuture`

定义接口时直接返回 `CompletableFuture`，Consumer 拿到的就是异步 Future：

```java
// 接口定义
public interface DataService {
    CompletableFuture<List<Data>> queryAsync(String query);
}

// 使用
@ZRpcReference(version = "1.0")
private DataService dataService;

public void process() {
    CompletableFuture<List<Data>> future = dataService.queryAsync("SELECT ...");
    future.thenAccept(data -> {
        // 在回调里处理结果，不阻塞调用线程
        processData(data);
    });
}
```

### 方式二：通过 `RpcContext` 获取 Future

对于同步接口，也可以通过 `@ZRpcReference(async = true)` 发起异步调用，
方法调用立即返回 `null`，真实结果通过 `RpcContext` 获取：

```java
@ZRpcReference(version = "1.0", async = true)
private UserService userService;

public void asyncDemo() {
    // 发起调用，不阻塞（返回 null）
    userService.getUser(123L);

    // 拿到 Future（在同一线程内调用才有效）
    CompletableFuture<UserInfo> future =
        RpcContext.getContext().getAttribute("future", CompletableFuture.class);

    future.thenAccept(user -> System.out.println("Got user: " + user.getName()));
}
```

---

## 传递附加信息（Attachments）

通过 `RpcContext` 可以向 Provider 传递额外的 key-value 元数据（如 traceId、用户信息）：

```java
// Consumer 端：在调用前设置 attachment
RpcContext.getContext().setAttachment("trace-id", UUID.randomUUID().toString());
RpcContext.getContext().setAttachment("user-id", currentUser.getId());

UserInfo user = userService.getUser(123L);  // attachment 随请求发送到 Provider
```

```java
// Provider 端：读取 attachment
public UserInfo getUser(long userId) {
    String traceId = RpcContext.getContext().getAttachment("trace-id");
    log.info("traceId={}", traceId);
    // ...
}
```

---

## 直连模式（不走注册中心）

在开发/测试时，可以绕过注册中心直接连接 Provider：

```java
// 方式：通过 URL 直连
@ZRpcReference(version = "1.0")
private UserService userService;
```

```yaml
# application.yml
zrpc:
  registry:
    type: memory
# 注册中心中手动写入地址，或使用以下方式：
```

或者直接在代码里构建 Invoker：

```java
URL directUrl = URL.builder()
    .protocol("zrpc")
    .host("192.168.1.10")
    .port(20880)
    .path(UserService.class.getName())
    .parameter(URLKeys.VERSION, "1.0")
    .build();

Protocol protocol = ExtensionLoader.getLoader(Protocol.class).getExtension("zrpc");
Invoker<UserService> invoker = protocol.refer(UserService.class, directUrl);

ProxyFactory proxyFactory = ExtensionLoader.getLoader(ProxyFactory.class).getDefaultExtension();
UserService userService = proxyFactory.getProxy(invoker, UserService.class);
```

---

## Consumer 调用链路（内部机制）

```
@ZRpcReference 字段
    │
ZRpcReferenceInjector.postProcessBeforeInitialization()
    └── RegistryDirectoryInvoker（订阅注册中心）
            │  onChanged() 回调
            │  ↓ 维护 invokerList（每个实例对应一个 NettyInvoker）
            │
ByteBuddyProxyFactory.getProxy(clusterInvoker, type)
    └── 动态代理（实现服务接口）
            │ 方法调用
            ↓
    RpcInvocationHandler.invoke()
            ├── 构建 RpcRequest
            ├── clusterInvoker.invoke(request)
            │       ├── loadBalance.select(invokerList, request)
            │       └── selectedInvoker.invoke(request)
            │               └── NettyClient.send(request)
            │                       └── CompletableFuture<RpcResponse>
            └── 返回结果 / 抛出异常
```

---

## 常见问题

**Q：注入字段为 `null`，调用时 NPE**

- 确认 Consumer 类是 Spring Bean（`@Component`/`@Service` 等）
- 确认字段不是 `final` 或 `static`
- 确认 Provider 已注册到同一注册中心

**Q：调用总是超时**

- 增大 `@ZRpcReference(timeout = ...)` 的值
- 检查 Provider 端业务逻辑是否有死循环/慢查询
- 检查网络连通性：`telnet {provider-host} {port}`

**Q：负载均衡总是路由到同一节点**

- `consistenthash` 策略默认对第一个参数做哈希，相同参数会固定路由
- 改用 `roundrobin` 或 `leastactive` 以实现均匀分发
