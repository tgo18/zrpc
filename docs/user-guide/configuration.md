# 配置参考

zRPC 的所有配置均通过 Spring Boot 的 `application.yml`（或 `application.properties`）注入，前缀为 `zrpc`。

---

## 完整配置示例

```yaml
zrpc:
  application:
    name: my-service              # 应用名，注册到注册中心的标识

  protocol:
    name: zrpc                    # 协议：zrpc | http2 | grpc
    host: 0.0.0.0                 # 服务监听地址（Provider 端）
    port: 20880                   # 服务端口
    serialization: json           # 序列化：json | protobuf | hessian | jdk
    threads: 16                   # 业务线程数（默认 CPU核数×2）

  registry:
    type: memory                  # 注册中心类型：memory | zookeeper | nacos
    address: ""                   # 注册中心地址（memory 时留空）

  consumer:
    timeout: 3000                 # 全局调用超时（ms），可被 @ZRpcReference 覆盖
    retries: 2                    # 失败重试次数（不含首次调用）
    loadbalance: roundrobin       # 负载均衡：roundrobin | random | consistenthash | leastactive
```

---

## 配置项说明

### `zrpc.application`

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `name` | String | `"default"` | 应用名称，写入注册中心 metadata |

---

### `zrpc.protocol`

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `name` | String | `"zrpc"` | 通信协议，可选 `zrpc`、`http2`、`grpc` |
| `host` | String | `"0.0.0.0"` | Provider 绑定地址 |
| `port` | int | `20880` | Provider 监听端口 |
| `serialization` | String | `"json"` | 序列化方式，影响编解码性能 |
| `threads` | int | `CPU核数×2` | 业务线程池大小（`zrpc` 协议使用虚拟线程，此项预留） |

**协议对应默认端口：**

| 协议 | 默认端口 |
|---|---|
| `zrpc` | 20880 |
| `http2` | 20881 |
| `grpc` | 50051 |

---

### `zrpc.registry`

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `type` | String | `"memory"` | 注册中心类型 |
| `address` | String | `""` | 注册中心连接地址 |

**各注册中心的 address 格式：**

| 类型 | address 示例 | 说明 |
|---|---|---|
| `memory` | `""` | 进程内注册，不需要外部服务 |
| `zookeeper` | `127.0.0.1:2181` | 支持集群：`host1:2181,host2:2181` |
| `nacos` | `127.0.0.1:8848` | 支持鉴权：`host:port?username=nacos&password=nacos` |

---

### `zrpc.consumer`

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `timeout` | int | `3000` | 调用超时（ms），`@ZRpcReference.timeout` 可覆盖 |
| `retries` | int | `2` | 失败重试次数（仅对幂等操作建议开启） |
| `loadbalance` | String | `"roundrobin"` | 默认负载均衡策略，`@ZRpcReference.loadbalance` 可覆盖 |

---

## `@ZRpcService` 属性

服务发布端，标注在 Provider 实现类上：

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `interfaceClass` | Class | 第一个接口 | 要发布的服务接口 |
| `version` | String | `""` | 服务版本号，用于版本隔离 |
| `group` | String | `""` | 服务分组，用于灰度/环境隔离 |
| `weight` | int | `100` | 负载均衡权重（值越大被选中概率越高） |
| `timeout` | int | `3000` | Provider 端方法超时（ms） |
| `register` | boolean | `true` | 是否向注册中心注册 |

---

## `@ZRpcReference` 属性

服务引用端，标注在 Consumer 字段上：

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `interfaceClass` | Class | 字段类型 | 要引用的服务接口 |
| `version` | String | `""` | 要匹配的服务版本（须与 Provider 一致） |
| `group` | String | `""` | 要匹配的服务分组 |
| `timeout` | int | `0`（继承全局） | 调用超时（ms），0 表示使用全局配置 |
| `retries` | int | `2` | 失败重试次数 |
| `loadbalance` | String | `""` | 负载均衡策略（空则继承全局） |
| `async` | boolean | `false` | 是否以异步方式调用（返回 `CompletableFuture`） |

---

## 序列化性能对比

| 序列化 | 速度 | 大小 | 跨语言 | 推荐场景 |
|---|---|---|---|---|
| `json` | 中 | 中 | 是 | 开发调试、异构系统 |
| `protobuf` | 快 | 小 | 是 | 高性能生产环境 |
| `hessian` | 中 | 小 | 是（Java 优先） | Java 同构系统 |
| `jdk` | 慢 | 大 | 否 | 仅内部测试，不推荐生产 |

---

## 多环境配置示例

使用 Spring Profile 区分 Provider 和 Consumer：

```yaml
# application.yml 公共配置
zrpc:
  application:
    name: greeter
  protocol:
    name: zrpc
    serialization: json
  registry:
    type: zookeeper
    address: 127.0.0.1:2181

---
spring:
  config:
    activate:
      on-profile: provider
zrpc:
  protocol:
    port: 20880

---
spring:
  config:
    activate:
      on-profile: consumer
zrpc:
  consumer:
    timeout: 5000
    loadbalance: leastactive
```

启动时指定 Profile：

```bash
java -jar app.jar --spring.profiles.active=provider
java -jar app.jar --spring.profiles.active=consumer
```
