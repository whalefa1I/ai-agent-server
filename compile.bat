@echo off
REM 使用 Java 21 编译项目

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

echo ========================================
echo Using Java:
"%JAVA_HOME%\bin\java.exe" -version
echo ========================================

cd /d "%~dp0"

REM 检查 pom.xml 是否存在
if not exist "pom.xml" (
    echo Error: pom.xml not found in %CD%
    exit /b 1
)

REM 使用 Java 21 的 javac 直接编译
echo Compiling with javac...

"%JAVA_HOME%\bin\javac.exe" ^
    -d target/classes ^
    -sourcepath src/main/java ^
    -cp "target/classes" ^
    src\main\java\demo\k8s\agent\memory\**\*.java ^
    src\main\java\demo\k8s\agent\plugin\**\*.java ^
    src\main\java\demo\k8s\agent\channels\**\*.java

if %ERRORLEVEL% EQU 0 (
    echo Compilation successful!
) else (
    echo Compilation failed!
    exit /b 1
)
