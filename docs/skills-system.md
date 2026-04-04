# Skills 系统文档

> 版本：1.0  
> 更新日期：2026-04-04

## 概述

Skills 系统是一个与 clawhub 兼容的技能管理平台，支持技能的搜索、安装、卸载和版本管理。

## 架构

```
┌─────────────────────────────────────────────────────────────┐
│                     SkillController                          │
│                  REST API 接口层                              │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                      SkillService                            │
│                 核心业务逻辑层                                │
│  ┌──────────────┐  ┌─────────────────┐  ┌────────────────┐ │
│  │ SkillRegistry│  │ClawhubSkillSvc  │  │LoaderRegistry  │ │
│  └──────────────┘  └─────────────────┘  └────────────────┘ │
└─────────────────────┬───────────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┬────────────────┐
        │             │             │                │
┌───────▼──────┐ ┌────▼─────┐ ┌────▼────────┐ ┌────▼────┐
│ ClawhubClient│ │Packaging │ │Version Mgr  │ │ Loaders │
│              │ │  •下载    │ │  •SemVer    │ │  •文件  │
│  •搜索       │ │  •安装    │ │  •回滚      │ │  •类路径│
│  •安装/卸载  │ │  •验证    │ │  •兼容性    │ │         │
└──────────────┘ │  •依赖    │ │             │ │         │
                 └───────────┘ └─────────────┘ └─────────┘
```

## 核心组件

### 1. SkillRegistry

技能注册表，管理所有已注册的技能的加载和调用。

### 2. ClawhubClient

clawhub API 客户端，提供与 clawhub 服务器的交互能力：
- 搜索技能
- 获取技能详情
- 安装/卸载技能

### 3. SkillService

核心业务逻辑层，整合了：
- SkillRegistry - 本地技能管理
- ClawhubSkillService - clawhub 技能管理
- SkillLoaderRegistry - 动态技能加载

### 4. Packaging 模块

技能包管理组件：
- **SkillDownloader**: HTTP 下载器
- **SkillInstaller**: ZIP 解压和安装
- **SkillValidator**: 完整性和兼容性验证
- **DependencyResolver**: 依赖解析和管理

### 5. Versioning 模块

版本管理组件：
- **SemVer**: 语义化版本解析和比较
- **VersionManager**: 多版本管理和回滚

## REST API

### 技能列表

```
GET /api/skills
```

获取所有已注册的技能列表。

### 技能详情

```
GET /api/skills/{skillName}
```

获取单个技能的详细信息。

### 搜索技能

```
GET /api/skills/search?q={query}
```

在 clawhub 上搜索技能。

### 安装技能

```
POST /api/skills/{skillId}/install?version=1.0.0
```

从 clawhub 安装技能。

### 卸载技能

```
POST /api/skills/{skillId}/uninstall
```

卸载已安装的技能。

### 执行技能工具

```
POST /api/skills/{skillName}/execute/{toolName}
Content-Type: application/json

{"param1": "value1", ...}
```

执行技能的指定工具。

## 配置

在 `application.yml` 中添加：

```yaml
demo:
  skills:
    clawhub:
      # 启用 clawhub 集成
      enabled: true
      # clawhub API 地址
      base-url: https://clawhub.example.com/api/v1
      # API 密钥（可选）
      api-key: your-api-key-here
```

## 内置技能

系统预置了以下内置技能：

| 技能名称 | 描述 | 工具 |
|---------|------|------|
| calc | 数学计算 | calculate |
| code | 代码执行 | execute |
| explain | 代码解释 | explain |

## 开发自定义技能

### 1. 实现 Skill 接口

```java
public class MyCustomSkill implements Skill {
    @Override
    public String name() {
        return "my-skill";
    }

    @Override
    public String description() {
        return "我的自定义技能";
    }

    @Override
    public List<SkillTool> getTools() {
        return List.of(
            new SkillTool("action", "执行操作", "{}", false)
        );
    }

    @Override
    public SkillResult execute(String toolName, Map<String, Object> input) {
        // 实现工具逻辑
        return SkillResult.success("操作完成");
    }
}
```

### 2. 注册技能

```java
@Service
public class SkillInitializer {
    public SkillInitializer(SkillService skillService) {
        skillService.registerSkill(new MyCustomSkill());
    }
}
```

## 技能包格式

技能包是一个 ZIP 文件，包含以下结构：

```
my-skill-1.0.0.zip
├── manifest.json       # 技能清单（必需）
├── tools/              # 工具定义目录（可选）
│   ├── action.json
│   └── query.json
└── lib/                # 依赖库（可选）
    └── my-lib.jar
```

### manifest.json 格式

```json
{
  "name": "my-skill",
  "version": "1.0.0",
  "description": "我的自定义技能",
  "author": "author@example.com",
  "dependencies": [],
  "tools": [
    {
      "name": "action",
      "description": "执行操作",
      "inputSchema": "{\"type\":\"object\"}",
      "readOnly": false
    }
  ],
  "runtime": {
    "javaVersion": "17",
    "requiredPackages": []
  }
}
```

## 版本管理

### 语义化版本

使用 SemVer 规范：`主版本号。次版本号。修订号`

- `^1.0.0`: 兼容主版本（>=1.0.0 且 <2.0.0）
- `~1.2.0`: 兼容次版本（>=1.2.0 且 <1.3.0）
- `>=1.0.0`: 大于等于 1.0.0
- `latest`: 最新版本

### 版本回滚

```java
// 回滚到上一个版本
String previousVersion = versionManager.rollback("my-skill");
```

## 依赖管理

技能可以声明对其他技能的依赖：

```json
{
  "name": "advanced-skill",
  "dependencies": ["calc", "code"]
}
```

安装时会自动检查依赖是否满足。

## 安全考虑

1. **技能验证**: 安装前验证技能包的完整性和兼容性
2. **权限控制**: 工具可以标记为只读（readOnly）或写操作
3. **沙箱执行**: 技能在隔离的环境中执行（待实现）

## 故障排除

### 技能无法加载

检查日志中的错误信息，常见问题：
- manifest.json 格式错误
- 缺少必需文件
- Java 版本不兼容

### 依赖冲突

```
缺少依赖：calc, code
```

先安装缺失的依赖技能。

### 版本不兼容

```
Java 版本不兼容：当前 17.0.1, 需要 21
```

升级 Java 运行时或选择兼容版本的技能。
