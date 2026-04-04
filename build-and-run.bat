@echo off
chcp 65001 >nul
echo Building and running minimal-k8s-agent-demo...
echo.

REM 设置 JAVA_HOME 为 JDK 21
if exist "C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot" (
    set JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot
    set PATH=%JAVA_HOME%\bin;%PATH%
    echo Using JDK 21 from %JAVA_HOME%
) else (
    echo [Warning] JDK 21 not found, using default Java
)

echo.
echo Step 1: Clean and compile...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo [ERROR] Build failed
    exit /b 1
)
echo [OK] Build successful

echo.
echo Step 2: Run application...
java -jar target/minimal-k8s-agent-demo-0.1.0-SNAPSHOT.jar
