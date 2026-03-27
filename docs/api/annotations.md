# 注解 API 参考

zRPC 提供两个核心注解，位于 `com.github.tgo18.zrpc.core.annotation` 包。

---

## @ZRpcService

标注在服务实现类上，声明该类为 zRPC 服务提供方。
Spring Boot Starter 在容器启动后会自动扫描、导出并注册。

### 完整定义

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ZRpcService {
    Class<?> interfaceClass() default void.class;
    String   version()        default "";
    String   group()          default "";
    int      weight()         default 100;
    int      timeout()        default 3000;
    boolean  register()       default true;
}
```

### 属性说明

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `interfaceClass` | `Class<?>` | `void.class`（自动推断） | 要发布的服务接口。若省略则取实现类的第一个接口 |
| `version` | `String` | `""` | 服务版本号。Consumer 端 `@ZRpcReference.version` 须匹配 |
| `group` | `String` | `""` | 服务分组，用于环境/灰度隔离 |
| `weight` | `int` | `100` | 负载均衡权重（0–Integer.MAX_VALUE），值越大被选中概率越高 |
| `timeout` | `int` | `3000` | Provider 端单次调用的超时时间（ms） |
| `register` | `boolean` | `true` | 是否向注册中心注册。`false` 时服务只启动不注册（可用于直连测试） |

### 示例

```java
// 基础用法
@ZRpcService(interfaceClass = UserService.class, version = "1.0")
public class UserServiceImpl implements UserService { ... }

// 灰度发布
@ZRpcService(
    interfaceClass = SearchService.class,
    version = "2.0",
    group = "gray",
    weight = 10,       // 灰度流量权重小
    timeout = 5000
)
public class SearchServiceV2Impl implements SearchService { ... }

// 不注册（仅本地可用 / 直连测试）
@ZRpcService(interfaceClass = InternalService.class, register = false)
public class InternalServiceImpl implements InternalService { ... }
```

### 与 Spring 注解组合

`@ZRpcService` 本身不包含 Spring `@Component` 语义，需配合 Spring 管理注解使用：

```java
@ZRpcService(interfaceClass = OrderService.class, version = "1.0")
@Component   // 或 @Service，确保 Spring 创建 Bean
public class OrderServiceImpl implements OrderService { ... }
```

> Spring Boot Starter 会在 `ZRpcServiceExporter.afterSingletonsInstantiated()` 中扫描
> 带有 `@ZRpcService` 的所有 Bean，因此 Bean 必须先被 Spring 管理。

---

## @ZRpcReference

标注在字段（或方法参数）上，声明该字段需要注入 zRPC 远程服务代理。
Spring Boot Starter 通过 `BeanPostProcessor` 在 Bean 初始化前自动注入。

### 完整定义

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface ZRpcReference {
    Class<?> interfaceClass() default void.class;
    String   version()        default "";
    String   group()          default "";
    int      timeout()        default 0;
    int      retries()        default 2;
    String   loadbalance()    default "";
    boolean  async()          default false;
}
```

### 属性说明

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `interfaceClass` | `Class<?>` | `void.class`（取字段类型） | 服务接口类型 |
| `version` | `String` | `""` | 服务版本，须与 Provider `@ZRpcService.version` 一致 |
| `group` | `String` | `""` | 服务分组，须与 Provider `@ZRpcService.group` 一致 |
| `timeout` | `int` | `0`（继承全局 `zrpc.consumer.timeout`） | 调用超时（ms） |
| `retries` | `int` | `2` | 失败重试次数（0 = 不重试）。**写操作应设为 0** |
| `loadbalance` | `String` | `""`（继承全局 `zrpc.consumer.loadbalance`） | 负载均衡策略名 |
| `async` | `boolean` | `false` | 是否异步调用（方法返回 `CompletableFuture` 时自动生效） |

### 示例

```java
// 最简用法（版本、超时继承全局配置）
@ZRpcReference
private UserService userService;

// 指定版本和超时
@ZRpcReference(version = "1.0", timeout = 5000)
private UserService userService;

// 高并发查询接口：一致性哈希 + 不重试
@ZRpcReference(
    version = "1.0",
    loadbalance = "consistenthash",
    retries = 0
)
private CacheService cacheService;

// 支付接口：禁止重试、最短超时
@ZRpcReference(version = "2.0", retries = 0, timeout = 1000)
private PaymentService paymentService;

// 异步调用
@ZRpcReference(version = "1.0", async = true)
private ReportService reportService;
```

### 版本匹配规则

| Provider `@ZRpcService.version` | Consumer `@ZRpcReference.version` | 是否匹配 |
|---|---|---|
| `"1.0"` | `"1.0"` | ✅ |
| `"1.0"` | `""` | ✅（空字符串不做版本过滤） |
| `"1.0"` | `"2.0"` | ❌ |
| `""` | `""` | ✅ |
| `""` | `"1.0"` | ❌ |

### 注意事项

1. **字段访问修饰符**：`@ZRpcReference` 字段无需 `public`，框架通过反射注入，但不要声明为 `final` 或 `static`。

2. **代理缓存**：同一个 `(interfaceClass + version + group)` 组合只会创建一个代理实例，多个字段共享同一代理。

3. **写操作禁止重试**：下单、支付、扣款等**非幂等操作**务必设置 `retries = 0`，避免重复执行。

4. **接口类型推断**：若字段类型是接口，`interfaceClass` 可省略；若字段类型是抽象类或具体类，必须显式指定 `interfaceClass`。
