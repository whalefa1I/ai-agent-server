---
name: demo-test
description: 测试技能 - 用于验证热加载功能
metadata:
  emoji: 🧪
  always: true
---

# demo-test 技能

这是一个测试技能，用于验证技能热加载功能。

## 用法

当看到这个技能时，说明技能热加载系统正常工作。

## 测试方法

1. 启动服务器
2. 访问 `/api/skills` 查看技能列表
3. 修改本文件
4. 等待 250ms 防抖延迟
5. 再次访问 `/api/skills` 确认技能已重新加载
