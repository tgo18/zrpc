# CLAUDE.md — zRPC Codebase Guide

This file describes the codebase structure, development conventions, and workflows for the **zRPC** project. It is the primary reference for AI assistants working in this repository.

---

## Project Overview

**zRPC** is a high-performance Java RPC framework inspired by Alibaba Dubbo and ByteDance Kitex. It supports multiple transports (Netty TCP, HTTP/2, gRPC), pluggable serialization, service discovery, and load balancing, with seamless Spring Boot integration.

- **Language**: Java 21 (virtual threads used in server handler)
- **Build**: Maven multi-module
- **Min Java version**: 17 (uses sealed classes, pattern matching, records)
- **Framework version**: 1.0.0-SNAPSHOT

---

## Module Map

```
zrpc/
├── pom.xml                      # Parent POM — dependency management for all modules
├── zrpc-core/                   # Core abstractions and SPI engine
├── zrpc-codec/                  # Serialization: JSON, Protobuf, Hessian, JDK
├── zrpc-transport/              # Network layer: Netty TCP, HTTP/2, gRPC bridge
├── zrpc-registry/               # Service registry: in-memory, ZooKeeper, Nacos
├── zrpc-loadbalance/            # Load balancing: RoundRobin, Random, ConsistentHash, LeastActive
├── zrpc-filter/                 # Built-in filters: AccessLog, Metrics, Timeout, Exception
├── zrpc-spring-boot-starter/    # Spring Boot auto-configuration
└── zrpc-example/                # Usage examples (provider + consumer)
```

**Dependency order** (each module only depends on modules above it):
```
zrpc-core
  └── zrpc-codec
  └── zrpc-transport (depends on zrpc-codec)
  └── zrpc-registry
  └── zrpc-loadbalance
  └── zrpc-filter
       └── zrpc-spring-boot-starter (depends on all above)
                └── zrpc-example
```

---

## Architecture

### Layer Model

```
Application Code
      │
  Proxy (ByteBuddy)
      │
  Filter Chain  ←── AccessLog, Metrics, Timeout, Exception, custom...
      │
  Cluster Invoker  ←── Registry directory + LoadBalance
      │
  Protocol (zrpc | http2 | grpc)
      │
  Transport (Netty Channel)
      │
  Codec  ←── ZRpcEncoder/ZRpcDecoder (custom binary framing)
      │
  Serializer  ←── JSON | Protobuf | Hessian
```

### Core Abstractions (zrpc-core)

| Class/Interface | Role |
|---|---|
| `URL` | Immutable value object representing a service address + parameters |
| `URLKeys` | Constants for standard URL parameter keys |
| `RpcRequest` | A single RPC invocation (service, method, args, attachments) |
| `RpcResponse` | The result of an invocation (result or exception, status code) |
| `RpcContext` | Thread-local context propagated through filter chains |
| `Invoker<T>` | Central abstraction: callable endpoint (local impl or remote stub) |
| `Protocol` | SPI: exports services and creates remote Invokers |
| `Exporter<T>` | Handle to an exported service, can be un-exported |
| `Filter` | SPI: intercepts invocations (middleware pattern) |
| `FilterChain` | Builds a chain of Filters wrapping an Invoker |
| `ProxyFactory` | SPI: creates consumer proxies and wraps impls into Invokers |
| `LoadBalance` | SPI: selects one Invoker from a list |
| `Registry` | SPI: service registration and subscription |
| `Serializer` | SPI: byte[] ↔ object conversion |
| `ExtensionLoader<T>` | SPI loader, loads from `META-INF/zrpc/{interface-fqn}` |

### SPI System

zRPC uses its own SPI mechanism (not Java's `ServiceLoader`). Extension files live at:
```
src/main/resources/META-INF/zrpc/{fully.qualified.InterfaceName}
```
Format: `name=com.example.Implementation`

To get a named extension:
```java
Serializer json = ExtensionLoader.getLoader(Serializer.class).getExtension("json");
Protocol grpc  = ExtensionLoader.getLoader(Protocol.class).getExtension("grpc");
```

The default is specified by `@SPI("defaultName")` on the interface.

---

## Key Conventions

### Naming

- **SPI interface files**: match the exact FQCN of the interface
- **SPI names**: lowercase, no hyphens (`roundrobin`, `zrpc`, `json`, not `round-robin`)
- **URL parameter keys**: all defined as constants in `URLKeys`
- **Module packages**: `com.github.tgo18.zrpc.{module}.*`

### Immutability

- `URL` is immutable. Use `url.addParameter(k, v)` which returns a new instance.
- `RpcRequest` and `RpcResponse` are mutable DTOs — passed by reference through the filter chain.

### Thread Safety

- `RpcContext` is thread-local. Always call `RpcContext.removeContext()` after an invocation on the server side.
- `ExtensionLoader` caches singletons in a `ConcurrentHashMap` — safe for concurrent access.
- `InMemoryRegistry` uses `CopyOnWriteArrayList` for listeners — writes are expensive, reads are cheap.

### Error Handling

- Wrap framework errors in `RpcException` with an appropriate error code constant.
- Never swallow `InterruptedException` — always re-interrupt the thread.
- Filter implementations should use `CompletableFuture.exceptionally` for async error handling.

### Async

- All `Invoker.invoke()` methods return `CompletableFuture<RpcResponse>`.
- Server-side handlers use Java 21 virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`).
- Do not block virtual threads with `synchronized`; prefer `ReentrantLock` or lock-free structures.

---

## Build and Test

### Prerequisites

- Java 21+
- Maven 3.9+

### Common Commands

```bash
# Build all modules (skip tests)
mvn clean install -DskipTests

# Build and run all tests
mvn clean verify

# Run tests for a specific module
mvn test -pl zrpc-core

# Run the example provider
mvn spring-boot:run -pl zrpc-example \
  -Dspring-boot.run.mainClass=com.github.tgo18.zrpc.example.provider.ProviderApplication

# Run the example consumer
mvn spring-boot:run -pl zrpc-example \
  -Dspring-boot.run.mainClass=com.github.tgo18.zrpc.example.consumer.ConsumerApplication
```

### Test Layout

- Unit tests live in `src/test/java` mirroring the source package.
- Tests use JUnit 5 (`@Test`, `@BeforeEach`, `assertXxx`) — no JUnit 4.
- Use Mockito for mocking; avoid PowerMock.
- Integration tests (requiring real ZooKeeper/Nacos) should be tagged `@Tag("integration")` and are skipped by default.

---

## Transport Details

### Custom Binary Protocol (zrpc)

Frame layout (17-byte fixed header):
```
[0-1]  magic    = 0xCAFE
[2]    version  = 1
[3]    msgType  (1=request, 2=response, 3=heartbeat-req, 4=heartbeat-resp)
[4]    codec    (1=json, 2=protobuf, 3=hessian, 4=jdk)
[5-12] requestId (long, 8 bytes)
[13-16] bodyLen (int, 4 bytes)
[17+]  body
```

Implemented by `ZRpcEncoder` and `ZRpcDecoder` in `zrpc-transport`.

### HTTP/2

- Uses Netty's `Http2FrameCodec` + `Http2MultiplexHandler`
- TLS enabled (self-signed cert in dev via Netty's `SelfSignedCertificate`)
- Each RPC call → one HTTP/2 stream
- Stream IDs are generated as odd integers (per RFC 7540)

### gRPC Bridge

- Uses grpc-java `ServerBuilder`/`ManagedChannelBuilder`
- No Protobuf stubs required — uses a generic byte-array `MethodDescriptor`
- Service name = Java interface FQCN; method name = Java method name
- Allows any gRPC client to call zRPC services without code generation

---

## Adding a New Extension

### New Serializer

1. Implement `com.github.tgo18.zrpc.core.codec.Serializer` in `zrpc-codec`
2. Add entry to `zrpc-codec/src/main/resources/META-INF/zrpc/com.github.tgo18.zrpc.core.codec.Serializer`
3. Write a unit test

### New Load Balancing Strategy

1. Extend `AbstractLoadBalance` in `zrpc-loadbalance`, override `doSelect`
2. Add entry to `META-INF/zrpc/com.github.tgo18.zrpc.core.loadbalance.LoadBalance`

### New Registry Backend

1. Implement `com.github.tgo18.zrpc.core.registry.Registry` in `zrpc-registry`
2. Add entry to `META-INF/zrpc/com.github.tgo18.zrpc.core.registry.Registry`
3. Add the optional dependency to `zrpc-registry/pom.xml` marked `<optional>true</optional>`

### New Filter

1. Implement `com.github.tgo18.zrpc.core.filter.Filter`
   - Can live in `zrpc-core` (no extra deps) or `zrpc-filter` (has Micrometer)
2. Add entry to `META-INF/zrpc/com.github.tgo18.zrpc.core.filter.Filter`
3. Register in the Spring Boot starter's default filter list if it should run by default

---

## Spring Boot Integration

Add to `pom.xml`:
```xml
<dependency>
    <groupId>com.github.tgo18</groupId>
    <artifactId>zrpc-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

`application.yml` configuration:
```yaml
zrpc:
  application:
    name: my-service
  protocol:
    name: zrpc          # zrpc | grpc | http2
    port: 20880
    serialization: json # json | protobuf | hessian
  registry:
    type: memory        # memory | zookeeper | nacos
    address: ""         # 127.0.0.1:2181 for ZooKeeper, 127.0.0.1:8848 for Nacos
  consumer:
    timeout: 3000
    retries: 2
    loadbalance: roundrobin
```

Annotate provider implementations:
```java
@ZRpcService(interfaceClass = FooService.class, version = "1.0")
public class FooServiceImpl implements FooService { ... }
```

Inject consumer references:
```java
@ZRpcReference(version = "1.0", loadbalance = "roundrobin", timeout = 5000)
private FooService fooService;
```

---

## Key Files Reference

| File | Purpose |
|---|---|
| `pom.xml` (root) | All dependency versions — update here only |
| `zrpc-core/.../common/URL.java` | URL model — immutable, builder-based |
| `zrpc-core/.../common/URLKeys.java` | All URL parameter key constants |
| `zrpc-core/.../protocol/Invoker.java` | Primary framework abstraction |
| `zrpc-core/.../extension/ExtensionLoader.java` | SPI loader — understand this before adding extensions |
| `zrpc-transport/.../netty/NettyProtocol.java` | Default TCP protocol wiring |
| `zrpc-transport/.../common/ZRpcMessage.java` | Wire frame definition |
| `zrpc-spring-boot-starter/.../bean/RegistryDirectoryInvoker.java` | Cluster + registry-aware invoker |
| `zrpc-spring-boot-starter/.../config/ZRpcProperties.java` | All Spring Boot config properties |

---

## Common Pitfalls

1. **Do not add new URL parameter keys as inline strings** — add to `URLKeys` and reference the constant.
2. **SPI extension names are case-sensitive** — always lowercase.
3. **`RpcContext` leaks**: always remove on provider side after handling a request.
4. **`CompletableFuture` chaining**: do not call `.get()` inside a Netty I/O thread — always dispatch to the business executor.
5. **Module isolation**: `zrpc-core` must not depend on any transport/registry/codec module. Violations break the SPI decoupling.
6. **`URL` parameters vs builder**: prefer `URL.builder()` for new URLs; use `url.addParameter()` for one-off overrides.
