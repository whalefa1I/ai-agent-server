# 环境安装指南

## 问题诊断

### 当前环境状态

| 组件 | 当前版本 | 需要版本 | 状态 |
|------|----------|----------|------|
| Java | 1.8.0_451 | JDK 17+ | ❌ 需要升级 |
| Maven | 3.9.14 (choco) | 3.6+ | ✅ 已安装 |
| OPENAI_API_KEY | 未设置 | 需要 | ❌ 需要配置 |

## 方案一：手动下载 JDK 17（推荐）

### 下载链接

从以下任一来源下载 JDK 17：

1. **Eclipse Temurin** (推荐)
   - https://github.com/adoptium/temurin17-binaries/releases
   - 下载：`OpenJDK17U-jdk_x64_windows_hotspot_17.x.x.msi`

2. **Oracle JDK**
   - https://www.oracle.com/java/technologies/downloads/#jdk17-windows

3. **Microsoft Build of OpenJDK**
   - https://learn.microsoft.com/java/openjdk/download

### 安装步骤

1. 下载 MSI 安装包
2. 双击运行安装
3. 记住安装路径（通常 `C:\Program Files\Eclipse Temurin\jdk-17.x`）
4. 更新环境变量：

```batch
:: 系统环境变量（需要管理员权限）
setx JAVA_HOME "C:\Program Files\Eclipse Temurin\jdk-17"
setx PATH "%JAVA_HOME%\bin;%PATH%"
```

### 验证安装

```bash
java -version
# 应该显示类似：
# openjdk version "17.0.x" ...
```

## 方案二：使用 SDKMAN (如果安装了 Git Bash)

```bash
# 安装 SDKMAN
curl -s "https://get.sdkman.io" | bash

#  source SDKMAN
source "$HOME/.sdkman/bin/sdkman-init.sh"

# 安装 JDK 17
sdk install java 17.0.9-tem

# 验证
java -version
```

## 配置 API Key

### 方式一：环境变量（推荐）

```batch
:: 用户环境变量（不需要管理员）
setx OPENAI_API_KEY "sk-your-api-key-here"
setx OPENAI_BASE_URL "https://api.openai.com/v1"  :: 如果使用自定义端点
```

### 方式二：运行时指定

```bash
mvn spring-boot:run -Dspring-boot.run.environmentVariables="OPENAI_API_KEY=sk-xxx"
```

### 方式三：application.yml

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  ai:
    openai:
      api-key: sk-your-api-key-here
```

## Maven 配置

Maven 已通过 Chocolatey 安装，路径：
```
C:/ProgramData/chocolatey/lib/maven/apache-maven-3.9.14/
```

添加到 PATH（如果尚未添加）：
```batch
setx PATH "C:/ProgramData/chocolatey/lib/maven/apache-maven-3.9.14/bin;%PATH%"
```

## 快速检查脚本

运行以下命令验证环境：

```bash
# 检查 Java
"C:\Program Files\Eclipse Temurin\jdk-17\bin\java.exe" -version

# 检查 Maven
"C:/ProgramData/chocolatey/lib/maven/apache-maven-3.9.14/bin/mvn.cmd" --version

# 检查 API Key
echo %OPENAI_API_KEY%
```

## 安装依赖

安装 spring-ai-agent-utils（必须先执行）：

```bash
cd ../spring-ai-agent-utils
"C:/ProgramData/chocolatey/lib/maven/apache-maven-3.9.14/bin/mvn.cmd" install -DskipTests
```

## 运行测试

```bash
cd minimal-k8s-agent-demo

# 编译项目
"C:/ProgramData/chocolatey/lib/maven/apache-maven-3.9.14/bin/mvn.cmd" clean compile

# 运行测试
"C:/ProgramData/chocolatey/lib/maven/apache-maven-3.9.14/bin/mvn.cmd" test

# 启动应用
"C:/ProgramData/chocolatey/lib/maven/apache-maven-3.9.14/bin/mvn.cmd" spring-boot:run
```

## 常见问题

### 1. "Unsupported class file major version"

**原因**: JDK 版本过低，无法编译/运行 Java 17 代码

**解决**: 确保使用 JDK 17+：
```bash
java -version  # 应该显示 17.x.x
```

### 2. "Could not find artifact spring-ai-agent-utils"

**原因**: 未先安装依赖

**解决**: 
```bash
cd ../spring-ai-agent-utils
mvn install -DskipTests
```

### 3. API Key 无效

**原因**: 环境变量未设置或 key 过期

**解决**:
```bash
set OPENAI_API_KEY=sk-xxx
echo %OPENAI_API_KEY%  # 确认已设置
```

## 下一步

环境准备完成后，运行：
```bash
./run-test-all.sh  # Linux/macOS
# 或
run-test-all.bat   # Windows
```
