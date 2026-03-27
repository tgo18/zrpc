# 快速开始

本文带你在 5 分钟内运行第一个 zRPC 服务。

---

## 前置条件

| 工具 | 版本 |
|---|---|
| JDK | 17+（推荐 21，以启用虚拟线程） |
| Maven | 3.9+ |

---

## Step 1：克隆并构建

```bash
git clone https://github.com/tgo18/zrpc.git
cd zrpc
mvn clean install -DskipTests
```

---

## Step 2：定义服务接口

接口通常放在独立的 API 模块（本示例直接复用 `zrpc-example`）：

```java
// com.github.tgo18.zrpc.example.api.GreeterService
public interface GreeterService {
    String sayHello(String name);
    String sayGoodbye(String name);
}
```

> **规范**：接口包名建议与 Provider 和 Consumer 分开，方便独立打包发布。

---

## Step 3：实现 Provider

```java
@ZRpcService(interfaceClass = GreeterService.class, version = "1.0")
public class GreeterServiceImpl implements GreeterService {

    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }

    @Override
    public String sayGoodbye(String name) {
        return "Goodbye, " + name + "!";
    }
}
```

Provider 的 `application.yml`：

```yaml
zrpc:
  application:
    name: greeter-provider
  protocol:
    name: zrpc        # 使用自定义二进制 TCP 协议
    port: 20880
    serialization: json
  registry:
    type: memory      # 本地内存注册中心（单机测试用）
```

启动 Provider：

```java
@SpringBootApplication
public class ProviderApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProviderApplication.class, args);
    }
}
```

---

## Step 4：实现 Consumer

```java
@Component
public class GreeterConsumer {

    // 框架自动注入代理，无需手动创建
    @ZRpcReference(version = "1.0", timeout = 5000)
    private GreeterService greeterService;

    public void run() {
        String result = greeterService.sayHello("Alice");
        System.out.println(result); // Hello, Alice!
    }
}
```

Consumer 的 `application.yml`：

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

---

## Step 5：运行

```bash
# 终端 1：启动 Provider
mvn spring-boot:run -pl zrpc-example \
  -Dspring-boot.run.mainClass=com.github.tgo18.zrpc.example.provider.ProviderApplication

# 终端 2：启动 Consumer
mvn spring-boot:run -pl zrpc-example \
  -Dspring-boot.run.mainClass=com.github.tgo18.zrpc.example.consumer.ConsumerApplication
```

Consumer 控制台预期输出：

```
INFO  - Response: Hello, Alice! (from zRPC provider)
INFO  - Response: Goodbye, Alice! See you next time.
INFO  - Response: Hello, Bob! (from zRPC provider)
...
```

---

## 下一步

- [配置参考](configuration.md) — 所有配置项的完整说明
- [Provider 开发详解](provider.md) — 版本、分组、权重、过滤器
- [Consumer 开发详解](consumer.md) — 重试、超时、负载均衡、异步调用
- [SPI 扩展开发](../api/spi.md) — 自定义序列化、注册中心、负载均衡策略
