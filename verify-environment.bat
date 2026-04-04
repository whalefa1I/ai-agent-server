@echo off
REM 环境验证脚本 - minimal-k8s-agent-demo
REM 使用方法：verify-environment.bat

echo ╔════════════════════════════════════════════════╗
echo ║   minimal-k8s-agent-demo 环境验证              ║
echo ╚════════════════════════════════════════════════╝
echo.

REM 设置路径
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot
set MAVEN_HOME=C:\ProgramData\chocolatey\lib\maven\apache-maven-3.9.14
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

echo [1/6] 检查 Java 版本...
java -version
if errorlevel 1 (
    echo [错误] Java 未找到
    exit /b 1
)
echo.

echo [2/6] 检查 Maven 版本...
call mvn --version
if errorlevel 1 (
    echo [错误] Maven 未找到
    exit /b 1
)
echo.

echo [3/6] 检查 OPENAI_API_KEY...
if "%OPENAI_API_KEY%"=="" (
    echo [警告] OPENAI_API_KEY 未设置
    echo    请设置：set OPENAI_API_KEY=your-api-key
    echo    百炼平台：set OPENAI_BASE_URL=https://coding.dashscope.aliyuncs.com/v1
) else (
    echo [OK] API Key 已设置
    echo    OPENAI_BASE_URL=%OPENAI_BASE_URL%
)
echo.

echo [4/6] 检查 spring-ai-agent-utils...
if exist "..\spring-ai-agent-utils\pom.xml" (
    echo [OK] spring-ai-agent-utils 存在
) else (
    echo [错误] 未找到 spring-ai-agent-utils
    echo    请确保项目结构正确
    exit /b 1
)
echo.

echo [5/6] 检查 Maven 仓库位置...
if exist "D:\.m2\repository" (
    echo [OK] Maven 仓库位于 D:\.m2\repository
) else (
    echo [提示] D:\.m2\repository 不存在，将使用默认位置
)
echo.

echo [6/6] 检查项目依赖...
cd ..\spring-ai-agent-utils
echo 正在安装 spring-ai-agent-utils...
call mvn install -DskipTests
if errorlevel 1 (
    echo [错误] spring-ai-agent-utils 安装失败
    cd minimal-k8s-agent-demo
    exit /b 1
)
echo [OK] spring-ai-agent-utils 安装成功
cd ..\minimal-k8s-agent-demo
echo.

echo ╔════════════════════════════════════════════════╗
echo ║   环境验证完成！                                ║
echo ╚════════════════════════════════════════════════╝
echo.
echo 下一步:
echo   1. 编译项目：mvn clean compile
echo   2. 运行测试：mvn test
echo   3. 启动服务：mvn spring-boot:run
echo.
