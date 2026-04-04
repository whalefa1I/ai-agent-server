# Skills 集成指南

## 概述

Skills 是预定义的工具集合，提供高级功能如计算、代码执行、文本解释等。每个 Skill 包含一个或多个相关工具。

## 架构

```
demo.k8s.agent.skills
├── Skill.java              # Skill 接口
├── SkillTool.java          # 工具定义
├── SkillResult.java        # 执行结果
├── SkillRegistry.java      # Skill 注册表
├── SkillService.java       # Spring 服务
├── SkillController.java    # HTTP API
├── SkillToolProvider.java  # 工具提供者（集成到 Agent 系统）
└── builtin/                # 内置 Skills
    ├── CalcSkill.java      # 计算技能
    ├── CodeSkill.java      # 代码执行技能
    └── ExplainSkill.java   # 解释技能
```

## 内置 Skills

### 1. CalcSkill - 计算技能

提供数学计算功能。

**工具列表：**

- `evaluate_expression` - 计算数学表达式
  ```json
  {
    "expression": "2 + 2 * 3"
  }
  ```

- `statistics` - 统计计算
  ```json
  {
    "values": [1, 2, 3, 4, 5],
    "metrics": ["mean", "median", "stddev"]
  }
  ```

### 2. CodeSkill - 代码执行技能

提供安全的代码执行功能。

**工具列表：**

- `execute_python` - 执行 Python 代码
  ```json
  {
    "code": "print('Hello, World!')",
    "timeout": 30
  }
  ```

- `execute_shell` - 执行 Shell 命令
  ```json
  {
    "command": "ls -la",
    "cwd": "/tmp",
    "timeout": 30
  }
  ```

### 3. ExplainSkill - 解释技能

提供代码和文本解释功能（需要 LLM 集成）。

**工具列表：**

- `explain_code` - 解释代码
- `explain_text` - 解释文本

## HTTP API

### 获取所有 Skills

```bash
GET /api/skills
```

响应：
```json
[
  {
    "name": "calc",
    "description": "提供数学计算功能",
    "enabled": true,
    "tools": [
      {
        "name": "evaluate_expression",
        "description": "计算数学表达式的值",
        "readOnly": false
      }
    ]
  }
]
```

### 获取 Skill 详情

```bash
GET /api/skills/{skillName}
```

### 执行 Skill 工具

```bash
POST /api/skills/{skillName}/execute/{toolName}
Content-Type: application/json

{
  "expression": "2 + 2 * 3"
}
```

响应（成功）：
```json
{
  "success": true,
  "output": "8",
  "metadata": {}
}
```

响应（失败）：
```json
{
  "success": false,
  "error": "Expression is required"
}
```

## 自定义 Skills

### 创建新的 Skill

```java
package demo.k8s.agent.skills.builtin;

import demo.k8s.agent.skills.Skill;
import demo.k8s.agent.skills.SkillResult;
import demo.k8s.agent.skills.SkillTool;

import java.util.List;
import java.util.Map;

public class MyCustomSkill implements Skill {
    @Override
    public String name() {
        return "my_custom";
    }

    @Override
    public String description() {
        return "我的自定义技能";
    }

    @Override
    public List<SkillTool> getTools() {
        return List.of(
            SkillTool.of(
                "my_tool",
                "我的工具",
                """
                {
                    "type": "object",
                    "properties": {
                        "param1": {"type": "string"}
                    },
                    "required": ["param1"]
                }
                """
            )
        );
    }

    @Override
    public SkillResult execute(String toolName, Map<String, Object> input) {
        // 实现工具逻辑
        String param1 = (String) input.get("param1");
        return SkillResult.success("Result: " + param1);
    }
}
```

### 注册自定义 Skill

```java
@Service
public class CustomSkillInitializer {
    private final SkillRegistry skillRegistry;

    public CustomSkillInitializer(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
        // 注册自定义 Skill
        this.skillRegistry.register(new MyCustomSkill());
    }
}
```

## 与 Agent 系统集成

Skills 通过 `SkillToolProvider` 自动集成到 Agent 系统中：

1. `SkillToolProvider` 将所有启用的 Skills 转换为 `ToolModule`
2. 在 `AgentConfiguration` 中，Skill 工具与 MCP 工具、内置工具一起注册到 `ChatClient`
3. Claude 可以根据工具描述自动调用 Skills

### 工具命名约定

Skill 工具的名称格式为：`{skillName}_{toolName}`

例如：
- `calc_evaluate_expression`
- `code_execute_python`
- `explain_explain_code`

## 安全性考虑

### CodeSkill 安全

- 代码执行有超时保护（默认 30 秒）
- 可以限制执行的命令类型
- 建议在生产环境中使用沙箱环境

### 建议的安全措施

1. **资源限制**：设置合理的超时和内存限制
2. **输入验证**：验证所有输入参数
3. **权限控制**：通过权限系统控制敏感技能的访问
4. **审计日志**：记录所有技能调用

## 配置

在 `application.yml` 中可以配置 Skills 的启用状态：

```yaml
demo:
  skills:
    enabled: true
    calc:
      enabled: true
    code:
      enabled: true
      timeout: 60  # 自定义超时时间
    explain:
      enabled: false  # 默认关闭，需要 LLM 集成
```

## 测试

```bash
# 测试 CalcSkill
curl -X POST http://localhost:8080/api/skills/calc/execute/evaluate_expression \
  -H "Content-Type: application/json" \
  -d '{"expression": "2 + 2 * 3"}'

# 测试 CodeSkill (Python)
curl -X POST http://localhost:8080/api/skills/code/execute/execute_python \
  -H "Content-Type: application/json" \
  -d '{"code": "print(1 + 1)"}'

# 测试 CodeSkill (Shell)
curl -X POST http://localhost:8080/api/skills/code/execute/execute_shell \
  -H "Content-Type: application/json" \
  -d '{"command": "echo Hello World"}'
```
