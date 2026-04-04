@echo off
chcp 65001 >nul
echo Building and running minimal-k8s-agent-demo...
echo.

REM 设置 JAVA_HOME 为 JDK 21 (如果已安装)
if exist "D:\Program Files\Java\jdk-21.0.10" (
    set JAVA_HOME=D:\Program Files\Java\jdk-21.0.10
    set PATH=%JAVA_HOME%\bin;%PATH%
    echo Using JDK 21 from %JAVA_HOME%
) else (
    echo [Warning] JDK 21 not found, using default Java
)

echo.
echo Step 1: Clean and compile...
call mvn clean compile -DskipTests
if errorlevel 1 (
    echo [ERROR] Compilation failed
    exit /b 1
)
echo [OK] Compilation successful

echo.
echo Step 2: Run application...
call mvn spring-boot:run -Dspring-boot.run.profiles=local
