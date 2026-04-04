# Java 21 代码规范

## 命名规范

### 类和记录类
- 使用有意义的名词，首字母大写
- 记录类使用 `record` 关键字，优先使用 compact constructor

```java
public record UserRecord(String name, int age) {
    public UserRecord {
        // compact constructor - 隐式参数，无需重复声明
        if (age < 0) {
            throw new IllegalArgumentException("Age must be positive");
        }
    }
}
```

### 接口和方法
- 接口名使用形容词或名词
- 方法名使用动词开头，首字母小写
- 私有方法可以简短但需表意清晰

## 记录类 (Records)

### 推荐写法

```java
// 简洁的记录类定义
public record Point(int x, int y) {}

// 带验证的记录类
public record Email(String address) {
    public Email {
        if (address == null || !address.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
    }
    
    // 实例方法直接定义在记录体内
    public String domain() {
        return address.substring(address.indexOf("@") + 1);
    }
}
```

### Accessor 方法
- 记录类默认生成与字段同名的 accessor 方法
- 不要添加 `get` 前缀

```java
public record Config(String url, int timeout) {}

// 使用：config.url() 而不是 config.getUrl()
```

## Switch 表达式

### 使用箭头语法和模式匹配

```java
// 推荐：switch 表达式 + 模式匹配
String result = switch (obj) {
    case Integer i -> "Number: " + i;
    case String s -> "String: " + s.length();
    case null -> "Null";
    default -> "Unknown";
};

// 推荐：增强的 switch 语句
void process(Object obj) {
    switch (obj) {
        case Integer i when i > 0 -> log.info("Positive: {}", i);
        case Integer i -> log.info("Non-positive: {}", i);
        default -> log.info("Other type");
    }
}
```

## 文本块 (Text Blocks)

```java
// 推荐：使用文本块表示多行字符串
String json = """
    {
        "name": "test",
        "value": 42
    }
    """;

// JSON Schema 定义
String schema = """
    {
        "type": "object",
        "properties": {
            "command": {"type": "string"}
        },
        "required": ["command"]
    }
    """;
```

## 模式匹配和类型模式

### instanceof 模式匹配

```java
// 推荐：模式匹配
if (obj instanceof String s) {
    return s.length();
}

// 避免：传统写法
if (obj instanceof String) {
    return ((String) obj).length();
}
```

### Guarded Patterns

```java
// 推荐：带 guard 的模式匹配
String describe(Number n) {
    return switch (n) {
        case Integer i when i > 0 -> "Positive integer: " + i;
        case Integer i -> "Integer: " + i;
        case Double d when d > 1.0 -> "Large double: " + d;
        default -> "Other number: " + n;
    };
}
```

## Optional 使用

```java
// 推荐：链式调用
Optional.ofNullable(value)
    .map(String::trim)
    .filter(s -> !s.isEmpty())
    .ifPresent(this::process);

// 推荐：返回默认值
String result = Optional.ofNullable(value)
    .orElse("default");
```

## 集合和流

### 工厂方法

```java
// 推荐：使用工厂方法
List<String> list = List.of("a", "b", "c");
Set<Integer> set = Set.of(1, 2, 3);
Map<String, Integer> map = Map.of("key", 1);

// 可变集合
List<String> mutableList = new ArrayList<>(List.of("a", "b"));
```

### 流式处理

```java
// 推荐：链式流操作
List<String> result = items.stream()
    .filter(Objects::nonNull)
    .map(String::toLowerCase)
    .sorted()
    .toList();

// 推荐：使用 collect 收集器
Map<String, List<Item>> grouped = items.stream()
    .collect(Collectors.groupingBy(Item::category));
```

## 异常处理

```java
// 推荐：具体的异常类型
try {
    processFile(path);
} catch (IOException e) {
    log.error("File processing failed", e);
    throw new BusinessException("Failed to process file", e);
}

// 推荐：try-with-resources
try (var reader = Files.newBufferedReader(path)) {
    return reader.lines().toList();
}
```

## 日志

```java
// 使用 SLF4J
private static final Logger log = LoggerFactory.getLogger(MyClass.class);

// 推荐：参数化日志
log.info("Processing item: {}", item.name());

// 避免：字符串拼接
log.info("Processing item: " + item.name()); // 不推荐
```

## Spring 特定规范

### 依赖注入

```java
// 推荐：构造函数注入
@Service
public class MyService {
    private final MyRepository repository;
    
    public MyService(MyRepository repository) {
        this.repository = repository;
    }
}

// 避免：字段注入
@Autowired
private MyRepository repository; // 不推荐
```

### 配置属性

```java
// 推荐：使用 record 作为内部配置类
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final String name;
    private final int timeout;
    
    // 或者使用 record
    public record ServerConfig(String host, int port) {}
}
```

## 注释规范

### Javadoc

```java
/**
 * 处理用户请求的服务类。
 * <p>
 * 提供用户认证、授权和会话管理功能。
 *
 * @author Your Name
 * @since 1.0
 */
public class UserService {

    /**
     * 根据 ID 查找用户。
     *
     * @param id 用户 ID，不能为空
     * @return 用户信息，不存在时抛出异常
     * @throws UserNotFoundException 用户不存在时
     */
    public User findById(String id) {
        // ...
    }
}
```

## 文件组织

```java
// 1. package 声明
package demo.k8s.agent.service;

// 2. import 分组（每组之间空一行）
import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import demo.k8s.agent.dto.UserDto;

// 3. 类定义
@Service
public class UserService {
    // 3.1 静态常量
    private static final int MAX_RETRY = 3;
    
    // 3.2 日志
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    
    // 3.3 实例变量（final 在前）
    private final UserRepository repository;
    private String cacheKey;
    
    // 3.4 构造函数
    public UserService(UserRepository repository) {
        this.repository = repository;
    }
    
    // 3.5 公开方法
    public User findById(String id) {
        // ...
    }
    
    // 3.6 私有方法
    private void validate(User user) {
        // ...
    }
    
    // 3.7 内部类/记录类
    public record UserResult(String id, String name) {}
}
```

## Java 21 特性使用指南

### 虚拟线程 (Virtual Threads)

```java
// 推荐：使用虚拟线程处理大量并发任务
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<Result>> futures = tasks.stream()
        .map(task -> executor.submit(() -> process(task)))
        .toList();
}
```

### Sealed Classes

```java
// 推荐：使用密封类限制继承
public sealed interface Result permits Success, Error {}

public final class Success implements Result {
    private final Object data;
}

public final class Error implements Result {
    private final String message;
}
```

### Pattern Matching for Records

```java
// 推荐：记录模式匹配
if (obj instanceof UserRecord(String name, int age) && age > 18) {
    processAdult(name);
}
```
