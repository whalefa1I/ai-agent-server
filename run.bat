@echo off
REM 快速启动脚本 - 同时启动服务端和 TUI 客户端 (Windows 版本)
REM 使用方法：run.bat

echo ╔════════════════════════════════════════════════╗
echo ║   minimal-k8s-agent-demo 快速启动               ║
echo ╚════════════════════════════════════════════════╝
echo.

REM 检查环境变量
if "%OPENAI_API_KEY%"=="" (
    echo [警告] OPENAI_API_KEY 未设置
    echo    请设置环境变量：set OPENAI_API_KEY=your-api-key
    echo.
)

REM 检查 spring-ai-agent-utils 是否已安装
echo 检查依赖...
if not exist "..\spring-ai-agent-utils" (
    echo [警告] 未找到 spring-ai-agent-utils 项目
    echo    请先运行：cd ..\spring-ai-agent-utils ^&^& mvn install -DskipTests
    echo.
)

echo.
echo 步骤 1/3: 构建 TUI 客户端...
cd tui-client
call mvn package -DskipTests
if errorlevel 1 (
    echo [错误] TUI 客户端构建失败
    cd ..
    exit /b 1
)
echo [OK] TUI 客户端构建完成

echo.
echo 步骤 2/3: 启动服务端...
cd ..

REM 后台启动服务端
start /min cmd /c "mvn spring-boot:run > server.log 2>&1"
echo [OK] 服务端已启动

REM 等待服务端启动（最多 30 秒）
echo 等待服务端启动...
for /l %%i in (1,1,30) do (
    curl -s http://localhost:8080/api/health >nul 2>&1 && (
        echo [OK] 服务端已就绪
        goto :server_ready
    )
    timeout /t 1 /nobreak >nul
)
echo [错误] 服务端启动超时
echo 查看日志：type server.log
exit /b 1

:server_ready
echo.
echo 步骤 3/3: 启动 TUI 客户端...
echo.
echo 正在连接服务器...

REM 运行 TUI 客户端
java -jar tui-client\target\minimal-k8s-agent-tui-jar-with-dependencies.jar --server ws://localhost:8080/ws/agent

echo.
echo TUI 已关闭，服务端仍在运行
echo 手动关闭服务端：按 Ctrl+C 或关闭窗口
