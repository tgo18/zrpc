# zRPC

> 高性能 Java RPC 框架，灵感来自 Alibaba Dubbo 与 ByteDance Kitex，原生支持 HTTP/2 与 gRPC 协议。

---

## 特性

| 特性 | 说明 |
|---|---|
| **多协议** | 自定义二进制 TCP（`zrpc`）、HTTP/2、gRPC Bridge（无需 Protobuf Stub） |
| **多序列化** | JSON（Jackson）、Protobuf、Hessian、JDK |
| **服务注册/发现** | 内存、ZooKeeper（Curator）、Nacos |
| **负载均衡** | 平滑加权轮询、加权随机、一致性哈希、最少活跃调用数 |
| **过滤器链** | AccessLog、Metrics（Micrometer）、Timeout、Exception，支持自定义扩展 |
| **动态代理** | ByteBuddy 字节码生成，性能优于 JDK 动态代理 |
| **Spring Boot** | 一行注解完成服务发布与引用，自动配置 |
| **SPI 扩展** | 仿 Dubbo 的 SPI 机制，所有核心组件均可替换 |
| **虚拟线程** | 服务端 Handler 使用 Java 21 Virtual Thread |

---

## 模块结构

```
zrpc/
├── zrpc-core/               # 核心抽象：URL、Invoker、Protocol、Filter、SPI
├── zrpc-codec/              # 序列化实现：JSON / Protobuf / Hessian / JDK
├── zrpc-transport/          # 传输层：Netty TCP、HTTP/2、gRPC
├── zrpc-registry/           # 注册中心：内存 / ZooKeeper / Nacos
├── zrpc-loadbalance/        # 负载均衡：RoundRobin / Random / Hash / LeastActive
├── zrpc-filter/             # 内置过滤器 SPI 注册
├── zrpc-spring-boot-starter/# Spring Boot 自动配置
└── zrpc-example/            # 示例：GreeterService Provider + Consumer
```

---

## 快速开始

### 1. 环境要求

- Java 17+（运行于 Java 21 可启用虚拟线程）
- Maven 3.9+

### 2. 引入依赖

```xml
<dependency>
    <groupId>com.github.tgo18</groupId>
    <artifactId>zrpc-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 3. 定义服务接口

```java
// 放在共享 API 模块中
public interface GreeterService {
    String sayHello(String name);
}
```

### 4. 实现并发布服务（Provider）

```java
@ZRpcService(interfaceClass = GreeterService.class, version = "1.0")
public class GreeterServiceImpl implements GreeterService {
    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }
}
```

`application.yml`：

```yaml
zrpc:
  application:
    name: greeter-provider
  protocol:
    name: zrpc
    port: 20880
    serialization: json
  registry:
    type: memory
```

### 5. 引用服务（Consumer）

```java
@Component
public class OrderService {

    @ZRpcReference(version = "1.0", timeout = 5000)
    private GreeterService greeterService;

    public void process(String user) {
        String msg = greeterService.sayHello(user);
        System.out.println(msg);
    }
}
```

`application.yml`：

```yaml
zrpc:
  application:
    name: greeter-consumer
  protocol:
    name: zrpc
  registry:
    type: memory
  consumer:
    timeout: 3000
    retries: 2
    loadbalance: roundrobin
```

### 6. 运行示例

```bash
# 构建
mvn clean install -DskipTests

# 启动 Provider
mvn spring-boot:run -pl zrpc-example \
  -Dspring-boot.run.mainClass=com.github.tgo18.zrpc.example.provider.ProviderApplication

# 启动 Consumer（新终端）
mvn spring-boot:run -pl zrpc-example \
  -Dspring-boot.run.mainClass=com.github.tgo18.zrpc.example.consumer.ConsumerApplication
```

---

## 切换协议

只需修改 `zrpc.protocol.name`，无需改动任何业务代码：

| 值 | 协议 | 默认端口 |
|---|---|---|
| `zrpc` | 自定义二进制 TCP（Netty） | 20880 |
| `http2` | HTTP/2 + TLS（Netty） | 20881 |
| `grpc` | gRPC（grpc-java） | 50051 |

---

## 切换注册中心

```yaml
# ZooKeeper
zrpc:
  registry:
    type: zookeeper
    address: 127.0.0.1:2181

# Nacos
zrpc:
  registry:
    type: nacos
    address: 127.0.0.1:8848
```

---

## 文档目录

| 文档 | 说明 |
|---|---|
| [用户手册 — 快速开始](docs/user-guide/getting-started.md) | 5 分钟上手 |
| [用户手册 — 配置参考](docs/user-guide/configuration.md) | 所有配置项说明 |
| [用户手册 — Provider 开发](docs/user-guide/provider.md) | 服务发布详解 |
| [用户手册 — Consumer 开发](docs/user-guide/consumer.md) | 服务引用详解 |
| [API 参考 — 核心接口](docs/api/core-api.md) | Invoker / Protocol / Filter 等接口 |
| [API 参考 — 注解](docs/api/annotations.md) | @ZRpcService / @ZRpcReference |
| [API 参考 — SPI 扩展](docs/api/spi.md) | 如何开发自定义扩展 |
| [架构文档 — 总览](docs/architecture/overview.md) | 分层模型、调用链路 |
| [架构文档 — 传输层](docs/architecture/transport.md) | 自定义协议帧、HTTP/2、gRPC |
| [架构文档 — 注册中心](docs/architecture/registry.md) | 注册/订阅模型 |
| [架构文档 — 负载均衡](docs/architecture/loadbalance.md) | 四种策略算法详解 |

---

## 构建与测试

```bash
# 编译（跳过测试）
mvn clean install -DskipTests

# 运行全部测试
mvn clean verify

# 运行单个模块测试
mvn test -pl zrpc-core
mvn test -pl zrpc-loadbalance
mvn test -pl zrpc-registry
```

---

## License

Apache License 2.0
