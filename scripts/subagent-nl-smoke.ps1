# 子 Agent 自然语言冒烟：通过 POST /api/v2/chat 触发模型选用 TaskCreate → MultiAgentFacade（无需前端）
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
        Name = "显式要求创建子任务（易触发 TaskCreate）"
        SessionId = "nl-smoke-explicit-001"
        Message = @"
请使用 TaskCreate 工具创建一个任务：
- subject: 子Agent冒烟验证
- description: 验证 MultiAgentFacade 与 SubagentRun 能走完；完成后用一两句话回复即可，无需真实改代码。
"@
    },
    @{
        Name = "多步骤拆解（符合 TaskCreate 工具说明中的「多步任务」场景）"
        SessionId = "nl-smoke-multi-step-002"
        Message = @"
我接下来要做三件事：1）梳理当前项目的子 Agent 配置项；2）列出与 spawn 相关的类名；3）总结风险点。
请先使用 TaskCreate 为「子 Agent 配置与 spawn 类梳理」建一个跟踪任务（subject 用英文短语即可），然后再开始分析。
"@
    },
    @{
        Name = "委派式自然语言（模型可能直接回答或选用 TaskCreate，视模型而定）"
        SessionId = "nl-smoke-delegate-003"
        Message = "请把「检查 README 是否提到 multi-agent」作为独立子任务执行，用任务工具跟踪进度。"
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
Write-Host "  [TOOL CALLBACK] toolName=TaskCreate"
Write-Host "  [TaskCreateRouter] 或 [Facade] Spawn success / [LocalRuntime]"
Write-Host "  SubagentRun / COMPLETED（若子任务实际执行）"
Write-Host ""
