# 架构总览

本文描述 zRPC 的整体分层模型、核心模块职责与一次完整 RPC 调用的链路。

---

## 设计原则

1. **层次清晰**：传输、协议、注册、负载均衡各层职责单一，不跨层依赖
2. **SPI 解耦**：所有核心组件均面向接口编程，实现可替换
3. **异步优先**：`Invoker.invoke()` 返回 `CompletableFuture`，链路全程非阻塞
4. **模块隔离**：`zrpc-core` 不依赖任何 transport/registry/codec 实现，违反此约束会破坏 SPI 解耦

---

## 分层模型

```
┌─────────────────────────────────────────────┐
│              应用代码（Spring Bean）           │
└───────────────────┬─────────────────────────┘
                    │ @ZRpcReference / @ZRpcService
┌───────────────────▼─────────────────────────┐
│            动态代理（ByteBuddy）               │  zrpc-core
│   将方法调用转换为 RpcRequest                   │
└───────────────────┬─────────────────────────┘
                    │
┌───────────────────▼─────────────────────────┐
│              过滤器链（FilterChain）            │  zrpc-core / zrpc-filter
│  AccessLog → Metrics → Timeout → Exception  │
└───────────────────┬─────────────────────────┘
                    │
┌───────────────────▼─────────────────────────┐
│         集群 Invoker（RegistryDirectoryInvoker）│  zrpc-spring-boot-starter
│  订阅注册中心 → 维护 Invoker 列表 → 负载均衡    │
└─────────┬─────────────────────────┬──────────┘
          │ 选出一个 Invoker         │
┌─────────▼──────────────┐   ┌──────▼──────────────────┐
│  NettyInvoker          │   │  GrpcInvoker             │  zrpc-transport
│  Http2Invoker          │   │                          │
└─────────┬──────────────┘   └──────┬───────────────────┘
          │                         │
┌─────────▼──────────────┐   ┌──────▼───────────────────┐
│  Netty Channel（TCP）   │   │  gRPC ManagedChannel      │
│  Netty Channel（HTTP/2）│   │                           │
└─────────┬──────────────┘   └──────┬───────────────────┘
          │  wire bytes              │  wire bytes
          │ ◄──────────────── 网络 ─────────────────► │
┌─────────▼──────────────────────────────────────────┐
│               编解码（Encoder/Decoder）              │  zrpc-transport
│      ZRpcEncoder/ZRpcDecoder（自定义帧）              │
│      HTTP/2 Frame Codec / gRPC Frame                │
└─────────┬──────────────────────────────────────────┘
          │
┌─────────▼──────────────────────────────────────────┐
│               序列化（Serializer）                   │  zrpc-codec
│         JSON / Protobuf / Hessian / JDK             │
└─────────────────────────────────────────────────────┘
```

---

## 模块职责

### zrpc-core

框架的基础，不依赖任何外部通信或存储库：

| 组件 | 职责 |
|---|---|
| `URL` | 服务地址的不可变值对象，携带所有参数 |
| `RpcRequest / RpcResponse` | 调用请求和响应的 DTO，贯穿调用链 |
| `RpcContext` | ThreadLocal 上下文，传递 traceId、attachment 等跨层信息 |
| `Invoker<T>` | 核心抽象，代表可调用的端点 |
| `Protocol` | SPI：导出服务 / 创建远程 Invoker |
| `Filter / FilterChain` | 中间件链，实现 AOP 式横切关注点 |
| `ExtensionLoader` | SPI 加载器，从 META-INF/zrpc 发现并缓存扩展 |
| `ProxyFactory` | SPI：生成消费端代理 / 包装实现类为 Invoker |

### zrpc-codec

序列化实现，与传输层解耦：

- `JsonSerializer`：Jackson，可读性好，适合调试
- `ProtobufSerializer`：体积小、速度快，跨语言
- `HessianSerializer`：Java 原生支持良好
- `JdkSerializer`：兜底，不推荐生产使用

### zrpc-transport

网络传输实现，一个协议对应一组 Server/Client/Encoder/Decoder：

| 协议 | 实现类 | 特点 |
|---|---|---|
| `zrpc` | `NettyProtocol` + `NettyServer/Client` | 自定义二进制帧，低延迟 |
| `http2` | `Http2Protocol` + Netty HTTP/2 codec | 标准 HTTP/2，TLS，流多路复用 |
| `grpc` | `GrpcProtocol` + grpc-java | 无 Stub，通用字节数组 MethodDescriptor |

### zrpc-registry

注册中心适配层：

| 实现 | 依赖 | 特点 |
|---|---|---|
| `InMemoryRegistry` | 无 | 进程内，适合单机测试 |
| `ZookeeperRegistry` | Apache Curator | 分布式，强一致性 |
| `NacosRegistry` | Nacos Client | 分布式，支持动态配置 |

### zrpc-loadbalance

四种负载均衡算法，详见[负载均衡架构文档](loadbalance.md)。

### zrpc-filter

SPI 注册文件，将 `zrpc-core` 中的内置过滤器（`ExceptionFilter`、`AccessLogFilter`、
`MetricsFilter`、`TimeoutFilter`）注册到 SPI 体系。

### zrpc-spring-boot-starter

框架与 Spring Boot 的桥梁：

| Bean | 职责 |
|---|---|
| `ZRpcAutoConfiguration` | 条件装配，创建 Exporter 和 Injector |
| `ZRpcProperties` | `@ConfigurationProperties` 绑定所有 `zrpc.*` 配置 |
| `ZRpcServiceExporter` | `SmartInitializingSingleton`，扫描 `@ZRpcService` 并导出 |
| `ZRpcReferenceInjector` | `BeanPostProcessor`，注入 `@ZRpcReference` 代理 |
| `RegistryDirectoryInvoker` | 集群 Invoker，订阅注册中心，执行负载均衡 |

---

## 完整调用链路

### Consumer 发起调用

```
1. 应用代码调用接口方法
        greeterService.sayHello("Alice")

2. ByteBuddy 代理拦截 → RpcInvocationHandler.invoke()
        RpcRequest {
            requestId = 42
            serviceName = "com.example.GreeterService"
            methodName = "sayHello"
            arguments = ["Alice"]
        }

3. RegistryDirectoryInvoker.invoke(request)
        └── 从 invokerList 中经负载均衡选出 NettyInvoker(10.0.0.1:20880)

4. FilterChain（若配置）：TimeoutFilter → MetricsFilter → AccessLogFilter

5. NettyInvoker.invoke(request)
        └── JsonSerializer.serialize(request) → byte[]
        └── NettyClient.send(ZRpcMessage{type=REQUEST, body=bytes})

6. Netty Channel → TCP → 服务端

7. 等待 CompletableFuture<RpcResponse> 完成

8. 返回 response.getResult() 给调用方
```

### Provider 处理请求

```
1. Netty I/O 线程接收到 ZRpcMessage
        NettyServerHandler.channelRead0()

2. 提交到 Virtual Thread Executor（Netty I/O 线程立即返回）
        BUSINESS_EXECUTOR.execute(() -> handleRequest(ctx, msg))

3. 反序列化请求体
        JsonSerializer.deserialize(body, RpcRequest.class)

4. 查找 Invoker（serviceMap.get(serviceKey)）

5. FilterChain.invoke(request)
        ExceptionFilter → AccessLogFilter → AbstractProxyInvoker

6. AbstractProxyInvoker.invoke()
        └── 反射调用 GreeterServiceImpl.sayHello("Alice")
        └── 返回 RpcResponse.success(42, "Hello, Alice!")

7. 序列化 RpcResponse → byte[]
8. 写回 ZRpcMessage{type=RESPONSE} 到 Channel
```

---

## 关键设计决策

### 为什么用 CompletableFuture 而不是同步阻塞？

- Netty 是异步事件驱动模型，强制同步会占用 I/O 线程
- `CompletableFuture` 允许在服务端使用 Virtual Thread，实现高并发
- 方便实现超时（`orTimeout`）、重试（`exceptionally`）等链式操作

### 为什么 zrpc-core 不能依赖 transport/registry？

```
zrpc-core 定义 Protocol 接口
    ↑ 依赖方向
zrpc-transport 实现 NettyProtocol

若 zrpc-core 依赖 zrpc-transport：
  → ExtensionLoader 在 zrpc-core 加载时已直接 import NettyProtocol
  → SPI 的"按需加载不同实现"失去意义
  → 强制引入 Netty 依赖，破坏模块边界
```

### 为什么使用 ByteBuddy 而不是 JDK 动态代理？

- JDK 动态代理只能代理接口，反射调用开销较大
- ByteBuddy 在运行时生成真实字节码（编译成 `.class`），第一次调用后 JIT 可进一步优化
- 生成的代理类可被 GC 回收（使用 `WeakReference` ClassLoader 时），不会造成永久代泄漏

---

## 扩展性设计

新增协议、注册中心或序列化时，只需：

1. 在对应模块实现 SPI 接口
2. 添加 `META-INF/zrpc/` 配置文件
3. 修改 `application.yml` 中的 `name`/`type` 字段

无需修改任何框架核心代码，这是"开闭原则"的体现。
