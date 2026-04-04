---
name: demo-k8s
description: 妙想最小 K8s 沙盒示例。当用户需要执行 shell 或查看系统信息时，先加载本技能，再使用 k8s_sandbox_run 工具。
---

# Demo K8s 沙盒

## 行为说明

1. 向用户确认要执行的命令意图。
2. 调用工具 `k8s_sandbox_run`，参数 `command` 为**单行** shell（例如 `echo hello` 或 `uname -a`）。
3. 将工具返回的 Pod 日志作为事实依据回答用户。

## 约束

- 不要使用换行拼接多条命令。
- 若环境未启用 Kubernetes（`demo.k8s.enabled=false`），工具会返回占位说明，请如实告知用户。
