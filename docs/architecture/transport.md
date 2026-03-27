# 传输层架构

本文详述 zRPC 三种传输协议的设计与实现。

---

## 协议对比

| 维度 | zrpc（自定义 TCP） | http2 | grpc |
|---|---|---|---|
| 底层实现 | Netty NIO | Netty HTTP/2 | grpc-java |
| 帧格式 | 自定义二进制 | HTTP/2 Data Frame | gRPC Length-Prefixed Message |
| TLS | 可选 | 默认启用（开发用自签名证书） | 可选（默认 PlainText） |
| 多路复用 | 否（连接复用，请求串行） | 是（HTTP/2 Stream） | 是（HTTP/2 Stream） |
| 需要 Stub | 否 | 否 | 否（泛型字节数组 MethodDescriptor） |
| 跨语言 | 有限 | 是 | 是 |
| 适用场景 | Java 内部调用，最低延迟 | 标准 HTTP/2 客户端互通 | 与 gRPC 生态对接 |

---

## 自定义二进制协议（zrpc）

### 帧格式

```
 0       1       2       3       4       5       6       7
 ┌───────────────┬───────┬───────┬───────┬───────────────────────────┐
 │ magic (2B)    │ ver   │ type  │ codec │   requestId (8B)           │
 │ 0xCAFE        │ 0x01  │       │       │                           │
 ├───────────────┴───────┴───────┴───────┴───────────────────────────┤
 │              bodyLen (4B)                                          │
 ├────────────────────────────────────────────────────────────────────┤
 │              body (bodyLen bytes)                                  │
 └────────────────────────────────────────────────────────────────────┘

总 Header = 2 + 1 + 1 + 1 + 8 + 4 = 17 bytes
最大帧大小 = 8 MiB（可配置）
```

### 字段说明

| 字段 | 大小 | 取值 | 说明 |
|---|---|---|---|
| magic | 2 bytes | `0xCAFE` | 协议标识，用于检测非法连接 |
| version | 1 byte | `0x01` | 协议版本号 |
| msgType | 1 byte | `1`=REQUEST, `2`=RESPONSE, `3`=HEARTBEAT_REQ, `4`=HEARTBEAT_RESP | 消息类型 |
| codec | 1 byte | `1`=JSON, `2`=Protobuf, `3`=Hessian, `4`=JDK | 序列化类型 |
| requestId | 8 bytes | long，自增 | 请求唯一 ID，用于多路复用时的响应关联 |
| bodyLen | 4 bytes | int | body 字节数，0 表示无 body（如心跳帧） |
| body | bodyLen bytes | 序列化后的 RpcRequest / RpcResponse | 实际载荷 |

### 服务端处理流程

```
Netty Pipeline（Provider）：

  ChannelPipeline
    ├── IdleStateHandler (60s reader idle → close)
    ├── ZRpcDecoder        (ByteToMessageDecoder)
    │       ├── 验证 magic = 0xCAFE
    │       ├── 等待完整帧（根据 bodyLen 判断）
    │       └── 输出 ZRpcMessage
    ├── ZRpcEncoder        (MessageToByteEncoder)
    │       └── 将 ZRpcMessage 编码为字节流
    └── NettyServerHandler (SimpleChannelInboundHandler<ZRpcMessage>)
            ├── 心跳：直接回写 HEARTBEAT_RESP
            └── 请求：提交到 Virtual Thread Executor
                        └── 反序列化 → 查找 Invoker → 异步调用 → 写回响应
```

### 客户端多路复用

```
NettyClient 维护单一 Channel（持久连接）

pendingRequests: ConcurrentHashMap<Long requestId, CompletableFuture<RpcResponse>>

发送：
  send(request)
    ├── pendingRequests.put(requestId, future)
    ├── channel.writeAndFlush(ZRpcMessage{REQUEST})
    └── return future

接收（NettyClientHandler）：
  channelRead0(ZRpcMessage{RESPONSE})
    ├── future = pendingRequests.remove(requestId)
    └── future.complete(response)
```

### 心跳保活

- 客户端：30 秒无写操作时发送 `HEARTBEAT_REQ`（`IdleStateHandler` 触发）
- 服务端：60 秒无读操作时主动关闭连接
- 客户端检测到连接关闭时，3 秒后自动重连（`channel.closeFuture` 监听）

### 断线重连

```java
// NettyClient.java
channel.closeFuture().addListener(f -> {
    if (!closed) {
        SHARED_GROUP.schedule(() -> connect(), 3, TimeUnit.SECONDS);
    }
});
```

重连期间，所有 `pendingRequests` 中等待中的 Future 会立即以 `RpcException(NETWORK_EXCEPTION)` 完成。

---

## HTTP/2 协议

### 连接建立

```
Client                                    Server
  │                                         │
  ├─── TLS ClientHello ───────────────────► │
  │ ◄── TLS ServerHello (自签名证书) ────── │
  ├─── HTTP/2 Connection Preface ─────────► │
  │ ◄── SETTINGS Frame ─────────────────── │
  ├─── SETTINGS ACK ──────────────────────► │
  │                                         │  连接建立完成
```

### 请求/响应流

每个 RPC 调用对应一个 HTTP/2 Stream（客户端发起的 Stream ID 为奇数）：

```
Stream N:

CLIENT → HEADERS frame
  :method = POST
  :path = /com.example.UserService/getUser
  :scheme = https
  content-type = application/zrpc

CLIENT → DATA frame (endStream=true)
  [序列化的 RpcRequest 字节]

SERVER → HEADERS frame
  :status = 200
  content-type = application/zrpc

SERVER → DATA frame (endStream=true)
  [序列化的 RpcResponse 字节]
```

### 多路复用

不同 RPC 调用使用不同 Stream ID，在同一 TCP 连接上并行传输：

```
TCP Connection
  ├── Stream 1: UserService.getUser(1)    → response
  ├── Stream 3: OrderService.getOrder(1) → response
  └── Stream 5: PaymentService.pay(...)  → response
```

### TLS 配置

开发环境使用 Netty 内置的自签名证书：

```java
SelfSignedCertificate cert = new SelfSignedCertificate();
SslContext sslCtx = SslContextBuilder
    .forServer(cert.certificate(), cert.privateKey())
    .build();
```

生产环境替换为真实证书：

```java
SslContext sslCtx = SslContextBuilder
    .forServer(new File("/etc/ssl/cert.pem"), new File("/etc/ssl/key.pem"))
    .build();
```

---

## gRPC 协议桥接

### 设计思路

标准 gRPC 需要 `.proto` 文件生成 Java Stub，才能调用服务。
zRPC 的 gRPC Bridge 通过**泛型字节数组 MethodDescriptor**绕过这个限制：

- **Service Name**：Java 接口全限定名（如 `com.example.UserService`）
- **Method Name**：Java 方法名（如 `getUser`）
- **Request/Response Marshaller**：直接传输 `byte[]`，由 zRPC 的 Serializer 负责编解码

```
gRPC 标准流程：
  Proto → protoc 生成 Java Stub → 编译 → 调用

zRPC gRPC Bridge：
  Java Interface → 运行时反射生成 ServerServiceDefinition → 调用
```

### 服务端注册流程

```java
// GrpcServiceBridge.buildServiceDefinition()

for (Method method : interface.getMethods()) {
    MethodDescriptor<byte[], byte[]> descriptor = MethodDescriptor
        .newBuilder()
        .setFullMethodName(interfaceName + "/" + methodName)
        .setRequestMarshaller(BYTE_MARSHALLER)   // byte[] 透传
        .setResponseMarshaller(BYTE_MARSHALLER)
        .build();

    // 注册 Unary 处理器
    builder.addMethod(descriptor, ServerCalls.asyncUnaryCall(
        (requestBytes, observer) -> {
            RpcRequest request = serializer.deserialize(requestBytes, RpcRequest.class);
            invoker.invoke(request).whenComplete((response, ex) -> {
                observer.onNext(serializer.serialize(response));
                observer.onCompleted();
            });
        }
    ));
}
```

### 客户端调用流程

```java
// GrpcInvoker.invoke()

String fullMethod = type.getName() + "/" + request.getMethodName();
MethodDescriptor<byte[], byte[]> descriptor = /* 与服务端匹配 */;

byte[] requestBytes = serializer.serialize(request);
ClientCall<byte[], byte[]> call = channel.newCall(descriptor, CallOptions.DEFAULT);

ClientCalls.asyncUnaryCall(call, requestBytes, new StreamObserver<byte[]>() {
    onNext(responseBytes) → future.complete(serializer.deserialize(responseBytes))
    onError(t)            → future.completeExceptionally(t)
});
```

### 与标准 gRPC 客户端互通

任何标准 gRPC 客户端（Go/Python/Node.js）都可以调用 zRPC 服务，只需：

1. 知道服务名（接口 FQCN）和方法名
2. 将 RpcRequest 序列化为 JSON（或 Protobuf）字节发送
3. 解析返回的 RpcResponse 字节

```python
# Python gRPC 客户端示例
import grpc

channel = grpc.insecure_channel('localhost:50051')
stub = channel.unary_unary(
    '/com.example.UserService/getUser',
    request_serializer=lambda r: r,     # bytes passthrough
    response_deserializer=lambda r: r   # bytes passthrough
)
response_bytes = stub(request_bytes)
```

---

## Netty 线程模型

```
BossGroup (1 thread)      WorkerGroup (CPU×2 threads)
    │                              │
    │ accept 新连接                 │ 处理 I/O 事件
    └─────────────────────────────►│
                                   │
                    ┌──────────────┴──────────────────┐
                    │ Pipeline 处理（I/O 线程中）        │
                    │  IdleStateHandler                │
                    │  ZRpcDecoder                     │
                    │  ZRpcEncoder                     │
                    │  NettyServerHandler.channelRead0 │
                    │         │ 提交任务                │
                    └─────────┼───────────────────────┘
                              │
                    ┌─────────▼─────────────────────────┐
                    │ Virtual Thread Executor (Java 21)  │
                    │  反序列化 + 业务逻辑 + 序列化响应     │
                    └────────────────────────────────────┘
```

**关键规则**：
- **不能**在 Netty I/O 线程（WorkerGroup）中执行阻塞操作
- **不能**在 Virtual Thread 中使用 `synchronized`（会 pin 住载体线程），改用 `ReentrantLock`
- `CompletableFuture.get()` 不能在 I/O 线程中调用
