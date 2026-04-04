@echo off
REM 一键编译脚本 - 使用 Java 21

REM 设置 Java 21 路径
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

echo ========================================
echo Java 版本:
"%JAVA_HOME%\bin\java.exe" -version
echo ========================================

REM 检查是否在项目目录
if not exist "pom.xml" (
    echo 错误：请在项目目录运行此脚本
    echo 当前目录：%CD%
    exit /b 1
)

echo 开始编译项目...
echo.

REM 使用 Maven Wrapper 编译
call mvnw.cmd clean compile

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo 编译成功!
    echo ========================================
) else (
    echo.
    echo ========================================
    echo 编译失败!
    echo ========================================
    exit /b 1
)
