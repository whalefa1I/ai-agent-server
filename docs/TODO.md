# 待办事项清单

> 更新日期：2026-04-04  
> 目标：构建生产级 Agent 框架

---

## 📋 任务概览

| 优先级 | 模块 | 任务数 | 状态 |
|--------|------|--------|------|
| P0 | clawhub 兼容 Skills | 5 | ✅ 已完成 |
| P0 | 用户和 Session | 7 | ✅ 已完成 |
| P0 | 可观测性 | 7 | ✅ 已完成 |
| P0 | 日志导出 | 7 | ✅ 已完成 |

---

## 🔧 1. clawhub 兼容的 Skills 系统

**目标**：实现与 clawhub 兼容的技能搜索、安装和管理功能

**完成状态**: ✅ 已完成 - 2026-04-04

### 已实现的功能

- [x] 动态技能加载机制 (`SkillLoader`, `FileSystemSkillLoader`, `ClasspathSkillLoader`)
- [x] clawhub API 适配器 (`ClawhubClient`, `ClawhubSkillService`)
- [x] 技能包管理 (`SkillDownloader`, `SkillInstaller`, `SkillValidator`, `DependencyResolver`)
- [x] 技能版本管理 (`SemVer`, `VersionManager`)
- [x] REST API 端点 (`SkillController`)

### 1.1 动态技能加载机制

- [x] 创建 `SkillLoader` 接口和实现
- [x] 支持从文件系统加载技能
- [x] 支持从类路径加载技能
- [ ] 支持热重载（技能更新无需重启）- 需要进一步完善

**文件结构**：
```
src/main/java/demo/k8s/agent/skills/
├── SkillLoader.java          # 技能加载器接口
├── FileSystemSkillLoader.java
├── ClasspathSkillLoader.java
└── SkillLoaderRegistry.java
```

### 1.2 clawhub API 适配器

- [x] 实现 clawhub 搜索 API 客户端
- [x] 实现技能安装 API
- [x] 实现技能卸载 API
- [x] 实现技能版本查询

**API 端点**：
```
GET  /api/skills/search?q={query}
POST /api/skills/{skillId}/install
POST /api/skills/{skillId}/uninstall
GET  /api/skills/{skillId}/versions
```

**文件结构**：
```
src/main/java/demo/k8s/agent/skills/clawhub/
├── ClawhubClient.java        # clawhub API 客户端
├── ClawhubSkill.java         # clawhub 技能模型
├── ClawhubSkillService.java  # 技能管理服务
└── ClawhubProperties.java    # 配置属性
```

### 1.3 技能包管理

- [x] 实现技能下载器 (HTTP/Git)
- [x] 实现技能解压和安装
- [x] 验证技能签名和完整性
- [x] 管理技能依赖

**文件结构**：
```
src/main/java/demo/k8s/agent/skills/packaging/
├── SkillManifest.java        # 技能包清单
├── SkillDownloader.java      # 下载器
├── SkillInstaller.java       # 安装器
├── SkillValidator.java       # 验证器
└── DependencyResolver.java   # 依赖解析
```

### 1.4 技能版本管理

- [x] 技能版本语义化 (SemVer)
- [x] 版本兼容性检查
- [x] 技能回滚功能
- [ ] 技能更新通知 - 需要进一步完善

### 1.5 技能市场 UI（可选）

- [ ] Web 界面展示可用技能
- [ ] 技能详情和评分
- [ ] 一键安装/卸载
- [ ] 已安装技能管理

---

## 👤 2. 用户和 Session 管理系统

**目标**：完善多用户支持和 Session 管理

### 2.1 用户实体和仓库

- [ ] 创建 `User` 实体类
- [ ] 创建 `UserRepository` 接口
- [ ] 实现 `InMemoryUserRepository`
- [ ] 实现 `JdbcUserRepository` (可选)

**文件结构**：
```
src/main/java/demo/k8s/agent/user/
├── User.java                 # 用户实体
├── UserRole.java             # 用户角色枚举
├── UserRepository.java       # 用户仓库接口
├── InMemoryUserRepository.java
└── JdbcUserRepository.java   # JDBC 实现
```

**User 实体设计**：
```java
public record User(
    String id,
    String username,
    String email,
    String passwordHash,
    UserRole role,
    Instant createdAt,
    Instant lastLoginAt,
    Map<String, Object> metadata
) {}
```

### 2.2 用户认证服务

- [ ] 实现 `UserService`
- [ ] 实现 `AuthenticationService`
- [ ] 添加 JWT Token 支持
- [ ] 实现密码加密 (BCrypt)
- [ ] 支持 API Key 认证

**文件结构**：
```
src/main/java/demo/k8s/agent/auth/
├── UserService.java
├── AuthenticationService.java
├── JwtTokenProvider.java
├── ApiKeyGenerator.java
└── AuthenticationFilter.java
```

### 2.3 Session 与用户关联

- [ ] 修改 `ConversationSession` 添加 `userId` 字段
- [ ] 修改 `ConversationManager` 支持用户隔离
- [ ] 实现用户级会话查询
- [ ] 添加 Session 超时和清理

**文件结构**：
```
src/main/java/demo/k8s/agent/state/
├── ConversationSession.java  # 添加 userId
├── UserSessionManager.java   # 用户 Session 管理
└── SessionCleanupService.java
```

### 2.4 用户配额管理

- [ ] 创建 `QuotaConfig` 配置类
- [ ] 实现 `QuotaService` 配额服务
- [ ] 添加请求限流 (RateLimiter)
- [ ] 实现 Token 计数和配额检查
- [ ] 添加配额警告和通知

**文件结构**：
```
src/main/java/demo/k8s/agent/quota/
├── QuotaConfig.java
├── QuotaService.java
├── RateLimiter.java
├── QuotaExceededException.java
└── QuotaNotificationService.java
```

**配额配置示例**：
```yaml
demo:
  quota:
    default:
      maxRequestsPerHour: 100
      maxTokensPerRequest: 100000
      maxConcurrentSessions: 5
    premium:
      maxRequestsPerHour: 1000
      maxTokensPerRequest: 500000
      maxConcurrentSessions: 20
```

### 2.5 用户偏好设置

- [ ] 创建 `UserPreferences` 实体
- [ ] 支持持久化偏好设置
- [ ] 实现偏好设置 API

**偏好设置项**：
- 默认模型选择
- 上下文长度限制
- 工具权限默认值
- UI 主题偏好
- 通知设置

### 2.6 多 Session 并发

- [ ] 支持同一用户多设备登录
- [ ] Session 同步机制
- [ ] Session 冲突解决
- [ ] 活动 Session 查询

### 2.7 用户活动审计

- [ ] 记录用户登录/登出
- [ ] 记录敏感操作
- [ ] 实现审计日志查询
- [ ] 支持审计日志导出

---

## 📊 3. 全链路可观测性

**目标**：实现完整的日志记录和追踪能力

**实现状态**: ✅ 已完成 - 2026-04-04

### 已实现的功能

- [x] 结构化日志记录器 (`StructuredLogger`)
- [x] 链路追踪上下文 (`TraceContext` + `TraceIdFilter`)
- [x] 事件总线 (`EventBus` + `Event` 类)
- [x] 事件日志记录器 (`EventLogger`)
- [x] 指标收集器 (`MetricsCollector` 集成 Micrometer)
- [x] 权限管理日志 (`PermissionManager` 集成)
- [x] 查询循环日志 (`EnhancedAgenticQueryLoop` 集成)
- [x] 会话管理日志 (`ConversationManager` 集成)
- [x] 用户服务日志 (`UserService` 集成)

### 3.1 统一日志格式

- [ ] 定义 JSON 日志格式
- [ ] 实现结构化日志输出
- [ ] 统一日志字段命名

**日志格式设计**：
```json
{
  "timestamp": "2026-04-04T10:00:00.000Z",
  "level": "INFO",
  "traceId": "abc123",
  "spanId": "def456",
  "userId": "user_001",
  "sessionId": "session_002",
  "event": "tool_call",
  "toolName": "bash",
  "input": {"command": "ls -la"},
  "output": "total 48...",
  "duration": 150,
  "success": true
}
```

**文件结构**：
```
src/main/java/demo/k8s/agent/observability/logging/
├── StructuredLogger.java
├── JsonLogEncoder.java
├── LogFormatter.java
└── LogConstants.java
```

### 3.2 链路追踪 (MDC/TraceID)

- [ ] 实现 `TraceContext`
- [ ] 集成 MDC (Mapped Diagnostic Context)
- [ ] 生成和传播 TraceID/SpanID
- [ ] 实现异步上下文传播

**文件结构**：
```
src/main/java/demo/k8s/agent/observability/tracing/
├── TraceContext.java
├── TraceIdGenerator.java
├── MdcContext.java
├── TraceInterceptor.java
└── AsyncTracePropagation.java
```

### 3.3 完整对话历史记录

- [ ] 记录所有用户输入
- [ ] 记录所有模型响应
- [ ] 记录工具调用详情
- [ ] 记录权限决策过程
- [ ] 记录错误和异常

**对话记录模型**：
```java
public record ConversationRecord(
    String recordId,
    String sessionId,
    String userId,
    Instant timestamp,
    RecordType type,
    String content,
    Map<String, Object> metadata,
    List<ToolCallRecord> toolCalls,
    TokenUsage tokenUsage,
    Duration latency
) {}
```

### 3.4 事件总线

- [ ] 实现 `EventBus` 事件总线
- [ ] 定义核心事件类型
- [ ] 实现事件订阅机制
- [ ] 支持事件持久化

**核心事件**：
- `UserLoginEvent` / `UserLogoutEvent`
- `SessionCreatedEvent` / `SessionEndedEvent`
- `ToolCalledEvent`
- `PermissionRequestedEvent` / `PermissionGrantedEvent`
- `ModelErrorEvent`
- `QuotaExceededEvent`

**文件结构**：
```
src/main/java/demo/k8s/agent/observability/events/
├── EventBus.java
├── Event.java
├── EventHandler.java
├── events/
│   ├── UserLoginEvent.java
│   ├── ToolCalledEvent.java
│   └── ...
└── EventStore.java
```

### 3.5 指标收集

- [ ] 定义核心指标
- [ ] 集成 Micrometer
- [ ] 实现指标导出 (Prometheus)
- [ ] 添加指标告警

**核心指标**：
- 请求延迟 (p50/p90/p99)
- Token 使用量 (输入/输出)
- 工具调用成功率
- 活跃用户数
- 活跃 Session 数
- 错误率

### 3.6 SessionStats 增强

- [ ] 扩展 `SessionStats` 指标
- [ ] 实现指标导出 API
- [ ] 添加 Grafana 仪表板配置

### 3.7 日志审计模块

- [ ] 实现审计日志分类
- [ ] 添加敏感操作标记
- [ ] 支持审计日志查询
- [ ] 实现审计报表

---

## 📤 4. 日志导出和训练数据转换

**目标**：支持将对话数据导出为模型训练格式

### 4.1 训练数据格式定义

- [ ] 定义 JSONL 格式
- [ ] 定义 Parquet 格式
- [ ] 支持元数据导出

**格式示例**：

**JSONL (Alpaca 格式)**：
```json
{"instruction": "写一个 Hello World 程序", "input": "", "output": "public class Hello..."}
```

**ShareGPT 格式**：
```json
{
  "conversations": [
    {"from": "human", "value": "写一个 Hello World 程序"},
    {"from": "gpt", "value": "public class Hello..."}
  ]
}
```

**ChatML 格式**：
```
<|im_start|>user
写一个 Hello World 程序<|im_end|>
<|im_start|>assistant
public class Hello...<|im_end|>
```

### 4.2 数据导出服务

- [ ] 实现 `ExportService`
- [ ] 支持按时间范围导出
- [ ] 支持按用户导出
- [ ] 支持按 Session 导出

**文件结构**：
```
src/main/java/demo/k8s/agent/export/
├── ExportService.java
├── ExportRequest.java
├── ExportJob.java
├── ExportStatus.java
└── ExportController.java
```

### 4.3 数据脱敏

- [ ] 识别敏感信息 (密码/Token/路径)
- [ ] 实现数据脱敏器
- [ ] 配置脱敏规则
- [ ] 支持自定义脱敏器

**文件结构**：
```
src/main/java/demo/k8s/agent/export/privacy/
├── DataAnonymizer.java
├── PrivacyRule.java
├── TokenMasker.java
├── PathMasker.java
└── PrivacyConfig.java
```

### 4.4 多格式导出器

- [ ] `AlpacaExporter`
- [ ] `ShareGPTExporter`
- [ ] `ChatMLExporter`
- [ ] `ParquetExporter`

### 4.5 批量和增量导出

- [ ] 实现批量导出 (全量)
- [ ] 实现增量导出 (基于时间戳)
- [ ] 支持导出断点续传
- [ ] 支持导出任务管理

### 4.6 数据压缩和归档

- [ ] 支持 GZIP 压缩
- [ ] 支持 ZIP 归档
- [ ] 自动清理临时文件
- [ ] 支持 S3 上传（可选）

### 4.7 导出 API

```
POST /api/v1/export/create      # 创建导出任务
GET  /api/v1/export/{id}/status # 查询导出状态
GET  /api/v1/export/{id}/download # 下载导出文件
GET  /api/v1/export/jobs        # 列出导出任务
DELETE /api/v1/export/{id}      # 取消导出任务
```

---

## 📁 推荐的文件结构

```
src/main/java/demo/k8s/agent/
├── user/                       # 用户管理
│   ├── User.java
│   ├── UserService.java
│   └── UserRepository.java
├── auth/                       # 认证
│   ├── AuthenticationService.java
│   └── JwtTokenProvider.java
├── quota/                      # 配额管理
│   ├── QuotaService.java
│   └── RateLimiter.java
├── skills/                     # Skills 系统
│   ├── SkillRegistry.java
│   ├── SkillLoader.java
│   ├── clawhub/                # clawhub 集成
│   └── packaging/              # 技能包管理
├── observability/              # 可观测性
│   ├── logging/                # 结构化日志
│   ├── tracing/                # 链路追踪
│   ├── events/                 # 事件总线
│   └── metrics/                # 指标收集
├── export/                     # 数据导出
│   ├── ExportService.java
│   ├── exporters/              # 格式导出器
│   └── privacy/                # 数据脱敏
└── state/                      # 状态管理 (已有，需增强)
    ├── ConversationSession.java
    └── ConversationManager.java
```

---

## 🎯 验收标准

### clawhub 兼容 Skills
- [ ] 可以搜索 clawhub 上的技能
- [ ] 可以安装和卸载技能
- [ ] 技能热重载生效
- [ ] 技能版本管理正常

### 用户和 Session
- [ ] 用户可以注册和登录
- [ ] Session 与用户正确关联
- [ ] 配额限制生效
- [ ] 多 Session 并发正常

### 可观测性
- [ ] 所有日志为 JSON 格式
- [ ] TraceID 贯穿完整请求链路
- [ ] 所有工具调用被记录
- [ ] 指标可以导出到 Prometheus

### 日志导出
- [ ] 可以导出 JSONL 格式
- [ ] 可以导出 ShareGPT 格式
- [ ] 敏感数据被脱敏
- [ ] 支持增量导出

---

## 📅 里程碑

| 里程碑 | 目标日期 | 状态 |
|--------|----------|------|
| Skills 系统完成 | 2026-04-10 | ⏳ |
| 用户管理完成 | 2026-04-15 | ⏳ |
| 可观测性完成 | 2026-04-20 | ⏳ |
| 数据导出完成 | 2026-04-25 | ⏳ |

---

## 📝 备注

1. **优先级说明**：所有任务标记为 P0，建议按顺序实施
2. **依赖关系**：用户管理是可观测性和数据导出的基础
3. **测试要求**：每个模块需要配套单元测试和集成测试
4. **文档要求**：每个 API 需要有 OpenAPI 文档
