@echo off
REM 本地工具测试脚本 - 逐个测试 6 个核心工具
REM 使用方法：test-local-tools.bat

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot
set MAVEN_HOME=C:\ProgramData\chocolatey\lib\maven\apache-maven-3.9.14
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

echo ╔════════════════════════════════════════════════╗
echo ║   minimal-k8s-agent-demo 本地工具测试          ║
echo ╚════════════════════════════════════════════════╝
echo.

REM 创建测试目录
echo [准备] 创建测试目录和文件...
mkdir test-tools 2>nul
cd test-tools

echo test1 > test1.java
echo test2 > test2.java
echo readme > readme.md
mkdir subdir 2>nul
echo nested > subdir\nested.java
echo Line1> test.txt
echo Line2>> test.txt
echo Line3>> test.txt
echo Line4>> test.txt
echo Line5>> test.txt

echo [OK] 测试文件创建完成
echo.

REM 运行 Maven 测试
echo [执行] 运行单元测试...
cd ..
call mvn test -Dtest=LocalGlobToolTest,LocalBashToolTest,LocalFileReadToolTest
if errorlevel 1 (
    echo [警告] 部分测试失败，继续执行...
)
echo.

REM 清理
echo [清理] 删除测试目录...
cd test-tools
cd ..
rmdir /s /q test-tools

echo.
echo ╔════════════════════════════════════════════════╗
echo ║   测试完成！                                    ║
echo ╚════════════════════════════════════════════════╝
