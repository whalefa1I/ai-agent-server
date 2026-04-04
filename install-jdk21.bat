@echo off
chcp 65001 >nul
echo ================================
echo 安装 JDK 21 (Eclipse Adoptium)
echo ================================
echo.

REM 检查是否已安装 JDK 21
where /Q javac && javac -version 2>&1 | findstr /C:"21." >nul
if %errorlevel% equ 0 (
    echo [OK] JDK 21 已经安装
    javac -version
    goto :configure
)

echo 正在下载 JDK 21...
powershell -Command "$ProgressPreference = 'SilentlyContinue'; Invoke-WebRequest -Uri 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.10%%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.10_9.msi' -OutFile '%%TEMP%%\\jdk-21.msi'"
if errorlevel 1 (
    echo [错误] 下载失败
    exit /b 1
)

echo 正在安装 JDK 21...
msiexec /i "%TEMP%\jdk-21.msi" /quiet /norestart INSTALLDIR="D:\Program Files\Java\jdk-21"
if errorlevel 1 (
    echo [错误] 安装失败
    exit /b 1
)

echo 等待安装完成...
timeout /t 30 /nobreak >nul

:configure
echo.
echo 配置环境变量...

REM 设置 JAVA_HOME (用户级别)
setx JAVA_HOME "D:\Program Files\Java\jdk-21"
if errorlevel 1 (
    echo [警告] 设置 JAVA_HOME 失败
)

echo [OK] JAVA_HOME 已设置为 D:\Program Files\Java\jdk-21

echo.
echo ================================
echo 安装完成!
echo 请重新打开终端以使用新的 Java 版本
echo ================================
echo.
echo 验证安装：java -version
