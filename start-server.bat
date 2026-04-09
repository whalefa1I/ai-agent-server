@echo off
REM 一键启动脚本 - Windows CMD
REM 使用方法：start-server.bat

cd /d %~dp0

REM 设置 Java 21 路径
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

REM 百炼平台配置
set DASHSCOPE_API_KEY=sk-sp-ab63f62c8df3494a8763982b1a741081
REM 不要带 /v1（Spring AI 会拼接 /v1/chat/completions）
set DASHSCOPE_BASE_URL=https://coding.dashscope.aliyuncs.com
set DASHSCOPE_MODEL=qwen-plus

echo ╔════════════════════════════════════════════════╗
echo ║   ai-agent-server 服务端启动 (百炼平台)          ║
echo ╚════════════════════════════════════════════════╝
echo.

REM 检查是否已编译
if not exist "target\minimal-k8s-agent-demo-0.1.0-SNAPSHOT.jar" (
    echo [1/2] 正在编译项目...
    call mvnw.cmd clean package -DskipTests
    if errorlevel 1 (
        echo [错误] 编译失败
        exit /b 1
    )
    echo [OK] 编译完成
) else (
    echo [1/2] 使用已编译的 jar 文件
)

echo.
echo [2/2] 启动服务端...
echo API Key: %DASHSCOPE_API_KEY%
echo Base URL: %DASHSCOPE_BASE_URL%
echo Model: %DASHSCOPE_MODEL%
echo.
echo 按 Ctrl+C 停止服务
echo.

"%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -Ddashscope.api.key=%DASHSCOPE_API_KEY% -Ddashscope.base.url=%DASHSCOPE_BASE_URL% -Ddashscope.model=%DASHSCOPE_MODEL% -jar target\minimal-k8s-agent-demo-0.1.0-SNAPSHOT.jar
