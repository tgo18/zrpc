# 核心接口 API 参考

本文描述 `zrpc-core` 模块的核心接口及其使用方式。

---

## URL

**包路径**：`com.github.tgo18.zrpc.core.common.URL`

服务地址的不可变值对象，格式为：
```
protocol://host:port/path?key=value&key2=value2
```

### 构建方式

```java
// Builder（推荐，用于新建 URL）
URL url = URL.builder()
    .protocol("zrpc")
    .host("127.0.0.1")
    .port(20880)
    .path("com.example.FooService")
    .parameter(URLKeys.VERSION, "1.0")
    .parameter(URLKeys.TIMEOUT, "3000")
    .build();

// 从字符串解析
URL url = URL.valueOf("zrpc://127.0.0.1:20880/com.example.Foo?version=1.0");
```

### 主要方法

| 方法 | 返回值 | 说明 |
|---|---|---|
| `getProtocol()` | `String` | 协议名 |
| `getHost()` | `String` | 主机地址 |
| `getPort()` | `int` | 端口 |
| `getPath()` | `String` | 路径（通常为接口全限定名） |
| `getAddress()` | `String` | `host:port` |
| `getParameter(key)` | `String` | 获取参数值 |
| `getParameter(key, default)` | `String` | 带默认值 |
| `getIntParameter(key, default)` | `int` | 获取整数参数 |
| `getBooleanParameter(key, default)` | `boolean` | 获取布尔参数 |
| `addParameter(key, value)` | `URL` | 返回新 URL（不可变） |
| `addParameters(map)` | `URL` | 批量添加参数，返回新 URL |
| `getServiceKey()` | `String` | `[group/]interface[:version]` |

### 注意事项

- `URL` 是**不可变对象**，所有修改操作返回新实例
- 新增 URL 参数 key 时，必须在 `URLKeys` 中定义常量，不允许使用字符串字面量

---

## URLKeys

**包路径**：`com.github.tgo18.zrpc.core.common.URLKeys`

URL 参数名常量类，避免散落的字符串字面量：

```java
public final class URLKeys {
    public static final String INTERFACE     = "interface";
    public static final String VERSION       = "version";
    public static final String GROUP         = "group";
    public static final String TIMEOUT       = "timeout";
    public static final String RETRIES       = "retries";
    public static final String LOADBALANCE   = "loadbalance";
    public static final String SERIALIZATION = "serialization";
    public static final String WEIGHT        = "weight";
    public static final String SIDE          = "side";
    public static final String PROVIDER_SIDE = "provider";
    public static final String CONSUMER_SIDE = "consumer";
    // ...
}
```

---

## RpcRequest

**包路径**：`com.github.tgo18.zrpc.core.common.RpcRequest`

表示一次 RPC 调用请求，贯穿整个过滤器链。

### 关键字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `requestId` | `long` | 全局唯一自增 ID，用于请求-响应关联 |
| `serviceName` | `String` | 接口全限定名 |
| `version` | `String` | 服务版本 |
| `group` | `String` | 服务分组 |
| `methodName` | `String` | 方法名 |
| `parameterTypes` | `Class<?>[]` | 参数类型（用于重载方法的精确匹配） |
| `arguments` | `Object[]` | 实际参数值 |
| `attachments` | `Map<String,String>` | 附加元数据（透传字段） |
| `oneWay` | `boolean` | 是否单向调用（fire-and-forget） |

### 服务键

```java
// 格式：[group/]interface[:version]
request.getServiceKey();
// 示例：payment/com.example.PaymentService:2.0
```

---

## RpcResponse

**包路径**：`com.github.tgo18.zrpc.core.common.RpcResponse`

表示一次 RPC 调用的结果。

### 状态枚举

```java
public enum Status {
    OK(200),
    CLIENT_ERROR(400),
    SERVER_ERROR(500),
    TIMEOUT(408),
    SERVICE_NOT_FOUND(404),
    SERIALIZATION_ERROR(600),
    NETWORK_ERROR(700);
}
```

### 工厂方法

```java
// 成功
RpcResponse.success(requestId, result);

// 失败（使用 SERVER_ERROR 状态）
RpcResponse.error(requestId, exception);

// 失败（自定义状态）
RpcResponse.error(requestId, Status.CLIENT_ERROR, exception);
```

### 判断结果

```java
if (response.isSuccess()) {
    Object result = response.getResult();
}
if (response.hasException()) {
    throw response.getException();
}
```

---

## RpcContext

**包路径**：`com.github.tgo18.zrpc.core.common.RpcContext`

基于 ThreadLocal 的调用上下文，在过滤器链和业务代码中共享数据。

### 获取与清理

```java
RpcContext ctx = RpcContext.getContext();  // 当前线程的上下文

// Provider 侧：处理完请求后必须清理（框架自动处理）
RpcContext.removeContext();
```

### 常用操作

```java
// 透传 attachment（Consumer → Provider）
RpcContext.getContext().setAttachment("trace-id", traceId);

// 读取 attachment（Provider 侧）
String traceId = RpcContext.getContext().getAttachment("trace-id");

// 存取任意对象（框架内部使用）
ctx.setAttribute("start-time", System.currentTimeMillis());
long start = ctx.getAttribute("start-time", Long.class);

// 获取调用方信息
URL remoteAddr = ctx.getRemoteAddress();
```

---

## Invoker&lt;T&gt;

**包路径**：`com.github.tgo18.zrpc.core.protocol.Invoker`

框架最核心的抽象，代表一个可调用的端点（远程 Stub 或本地实现）。

### 接口定义

```java
public interface Invoker<T> {
    Class<T> getInterface();
    URL getUrl();
    boolean isAvailable();
    CompletableFuture<RpcResponse> invoke(RpcRequest request);
    void destroy();
}
```

### 角色说明

| 场景 | 实现类 | 说明 |
|---|---|---|
| Provider 本地实现 | `AbstractProxyInvoker` | 反射调用 POJO 方法 |
| Consumer 远程存根（zrpc） | `NettyInvoker` | 通过 Netty 发送请求 |
| Consumer 远程存根（gRPC） | `GrpcInvoker` | 通过 gRPC Channel 发送请求 |
| Consumer 集群路由 | `RegistryDirectoryInvoker` | 从注册中心获取节点，负载均衡后分发 |
| 过滤器包装 | `FilterChain` 内联匿名类 | 拦截 invoke()，插入横切逻辑 |

### 调用示例

```java
// 直接使用 Invoker（通常不需要，通过代理调用更方便）
RpcRequest request = new RpcRequest();
request.setServiceName(UserService.class.getName());
request.setMethodName("getUser");
request.setParameterTypes(new Class[]{long.class});
request.setArguments(new Object[]{123L});

CompletableFuture<RpcResponse> future = invoker.invoke(request);
RpcResponse response = future.get();
UserInfo user = (UserInfo) response.getResult();
```

---

## Protocol

**包路径**：`com.github.tgo18.zrpc.core.protocol.Protocol`

SPI 接口，负责服务的导出（Provider）和引用（Consumer）。

```java
@SPI("zrpc")
public interface Protocol {
    int getDefaultPort();
    <T> Exporter<T> export(Invoker<T> invoker);
    <T> Invoker<T> refer(Class<T> type, URL url);
    void destroy();
}
```

### 获取实例

```java
Protocol protocol = ExtensionLoader.getLoader(Protocol.class).getExtension("grpc");
```

### 手动导出服务

```java
URL url = URL.builder().protocol("zrpc").host("0.0.0.0").port(20880)
    .path(UserService.class.getName()).build();

Invoker<UserService> invoker = proxyFactory.getInvoker(impl, UserService.class, url);
Exporter<UserService> exporter = protocol.export(invoker);

// 下线服务
exporter.unexport();
```

---

## Filter

**包路径**：`com.github.tgo18.zrpc.core.filter.Filter`

拦截器 SPI 接口，实现横切关注点（日志、限流、熔断等）。

```java
@SPI
public interface Filter {
    CompletableFuture<RpcResponse> invoke(Invoker<?> invoker, RpcRequest request);
}
```

### 内置过滤器

| SPI 名 | 类 | 功能 |
|---|---|---|
| `exception` | `ExceptionFilter` | 捕获异常，转为 RpcResponse.error |
| `accesslog` | `AccessLogFilter` | 记录每次调用的耗时和状态 |
| `metrics` | `MetricsFilter` | 写入 Micrometer 指标（延迟直方图、错误计数） |
| `timeout` | `TimeoutFilter` | 超过 URL 中 timeout 参数时强制完成 Future |

### 编写自定义过滤器

```java
public class TraceFilter implements Filter {

    @Override
    public CompletableFuture<RpcResponse> invoke(Invoker<?> invoker, RpcRequest request) {
        String traceId = request.getAttachment("trace-id",
                                                UUID.randomUUID().toString());
        MDC.put("traceId", traceId);

        return invoker.invoke(request).whenComplete((resp, ex) -> MDC.remove("traceId"));
    }
}
```

注册（`META-INF/zrpc/com.github.tgo18.zrpc.core.filter.Filter`）：
```
trace=com.example.filter.TraceFilter
```

### 过滤器链顺序

```
FilterChain.buildChain(invoker, [A, B, C])
→ A.invoke → B.invoke → C.invoke → invoker.invoke
               ←return  ←return    ←return
```

过滤器列表中**越靠前的越先执行、越后恢复**（类似 Servlet Filter）。

---

## LoadBalance

**包路径**：`com.github.tgo18.zrpc.core.loadbalance.LoadBalance`

```java
@SPI("roundrobin")
public interface LoadBalance {
    <T> Invoker<T> select(List<Invoker<T>> invokers, RpcRequest request);
}
```

### 使用示例

```java
LoadBalance lb = ExtensionLoader.getLoader(LoadBalance.class).getExtension("leastactive");
Invoker<UserService> chosen = lb.select(availableInvokers, request);
```

---

## Registry

**包路径**：`com.github.tgo18.zrpc.core.registry.Registry`

```java
@SPI("memory")
public interface Registry {
    void register(ServiceInstance instance);
    void deregister(ServiceInstance instance);
    void subscribe(String serviceName, RegistryListener listener);
    void unsubscribe(String serviceName, RegistryListener listener);
    List<ServiceInstance> getInstances(String serviceName);
    boolean isAvailable();
    void destroy();
}
```

### 订阅变更

```java
registry.subscribe("com.example.UserService", (serviceName, instances) -> {
    // 每当节点列表变化时触发（包括初次调用时的快照）
    log.info("Service {} has {} instances", serviceName, instances.size());
    refreshInvokerList(instances);
});
```

---

## ExtensionLoader&lt;T&gt;

**包路径**：`com.github.tgo18.zrpc.core.extension.ExtensionLoader`

zRPC 自研 SPI 加载器，加载 `META-INF/zrpc/{interface-fqcn}` 中的扩展。

```java
// 获取 Loader
ExtensionLoader<Serializer> loader = ExtensionLoader.getLoader(Serializer.class);

// 获取具名扩展（单例缓存）
Serializer json = loader.getExtension("json");

// 获取 @SPI 默认扩展
Serializer defaultSer = loader.getDefaultExtension();

// 列出所有注册扩展名
Set<String> names = loader.getSupportedExtensions();

// 检查扩展是否存在
boolean exists = loader.hasExtension("protobuf");
```

### SPI 文件格式

路径：`META-INF/zrpc/com.github.tgo18.zrpc.core.codec.Serializer`

```
# 注释以 # 开头
json=com.github.tgo18.zrpc.codec.JsonSerializer
protobuf=com.github.tgo18.zrpc.codec.ProtobufSerializer
```

---

## RpcException

**包路径**：`com.github.tgo18.zrpc.core.exception.RpcException`

框架内所有异常的基类，携带错误码：

```java
public class RpcException extends RuntimeException {
    public static final int NETWORK_EXCEPTION        = 1;
    public static final int TIMEOUT_EXCEPTION        = 2;
    public static final int BIZ_EXCEPTION            = 3;
    public static final int SERIALIZATION_EXCEPTION  = 5;
    public static final int NO_INVOKER_AVAILABLE     = 6;
    // ...
}
```

```java
try {
    userService.getUser(id);
} catch (RpcException e) {
    if (e.isTimeout())    { /* 超时处理 */ }
    if (e.isNetwork())    { /* 网络中断 */ }
    if (e.isNoInvoker())  { /* 无可用节点 */ }
}
```
