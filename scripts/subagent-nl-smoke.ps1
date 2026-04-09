# 子 Agent 自然语言冒烟：通过 POST /api/v2/chat 触发模型选用 spawn_subagent（无需前端）
# 前置条件（在启动应用前设置环境变量或 JVM 参数）：
#   DEMO_MULTI_AGENT_ENABLED=true
#   DEMO_MULTI_AGENT_MODE=on
#   DASHSCOPE_API_KEY=<有效百炼 Key>（或已在 application.yml 中配置）
# 启动示例：
#   $env:DEMO_MULTI_AGENT_ENABLED='true'; $env:DEMO_MULTI_AGENT_MODE='on'; mvn spring-boot:run
# 用法：
#   .\scripts\subagent-nl-smoke.ps1
#   .\scripts\subagent-nl-smoke.ps1 -BaseUrl http://127.0.0.1:8080

[CmdletBinding()]
param(
    [string] $BaseUrl = "http://127.0.0.1:8080"
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\_init-console-utf8.ps1" -ErrorAction SilentlyContinue

$cases = @(
    @{
        Name = "显式要求委派子 Agent（易触发 spawn_subagent）"
        SessionId = "nl-smoke-explicit-001"
        Message = @"
请使用 spawn_subagent 工具执行一个子任务：
- goal: 子Agent冒烟验证。请在子运行中完成最小可验证动作并返回简短结论。
"@
    },
    @{
        Name = "多步骤拆解（先建 Task 跟踪，再用 spawn_subagent 执行）"
        SessionId = "nl-smoke-multi-step-002"
        Message = @"
请先用 TaskCreate 创建跟踪任务（subject 为英文短语），
然后使用 spawn_subagent 执行「梳理当前项目子Agent配置项并列出风险点」，
最后用 TaskUpdate/TaskOutput 回填任务进度与产出。
"@
    },
    @{
        Name = "委派式自然语言（模型可能直接回答或选用 spawn_subagent，视模型而定）"
        SessionId = "nl-smoke-delegate-003"
        Message = "请把「检查 README 是否提到 multi-agent」作为独立子任务委派给子Agent执行，并返回结果。"
    }
)

Write-Host "========== 子 Agent 自然语言冒烟 ==========" -ForegroundColor Cyan
Write-Host "BaseUrl: $BaseUrl" -ForegroundColor Gray
Write-Host "请确认已启用: DEMO_MULTI_AGENT_ENABLED=true , DEMO_MULTI_AGENT_MODE=on" -ForegroundColor Yellow
Write-Host ""

foreach ($c in $cases) {
    Write-Host "--- $($c.Name) ---" -ForegroundColor Green
    $body = @{
        sessionId = $c.SessionId
        message   = $c.Message
    } | ConvertTo-Json -Depth 6

    try {
        $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v2/chat" -Method Post -Body $body `
            -ContentType "application/json; charset=utf-8" -TimeoutSec 600
        $preview = $resp.content
        if ($null -ne $preview -and $preview.Length -gt 400) {
            $preview = $preview.Substring(0, 400) + "..."
        }
        Write-Host "HTTP OK. content 预览:" -ForegroundColor Gray
        Write-Host $preview
    } catch {
        Write-Host "请求失败: $($_.Exception.Message)" -ForegroundColor Red
        if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message -ForegroundColor Red }
    }
    Write-Host ""
}

Write-Host "========== 日志自检关键词（服务端日志中搜索）==========" -ForegroundColor Cyan
Write-Host "  [TOOL CALLBACK] toolName=spawn_subagent"
Write-Host "  [Facade] Spawn success / [LocalRuntime]"
Write-Host "  SubagentRun / COMPLETED（若子任务实际执行）"
Write-Host ""
