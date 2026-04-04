# Java 和 Maven 环境配置

## Java 21 路径

**Java 21 Home:**
```
C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\
```

**验证命令:**
```bash
"C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin\java.exe" -version
```

## 其他 Java 版本

**Java 17:**
```
C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot\
```

**Java 8 (系统默认):**
```
C:\Program Files (x86)\Common Files\Oracle\Java\java8path\java.exe
```

## Maven

**Maven Wrapper (推荐):**
```bash
# 项目目录内执行
./mvnw.cmd compile
./mvnw.cmd clean package
```

Maven Wrapper 会自动下载 Maven 3.9.11，无需单独安装。

## 快速编译命令

```bash
# 使用 Maven Wrapper 编译（推荐）
cd G:\project\claude-code\minimal-k8s-agent-demo
./mvnw.cmd clean compile

# 或使用 Java 21 环境变量
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot
./mvnw.cmd clean compile
```

## 环境变量设置（可选）

如需永久设置，添加以下到系统环境变量：

```
JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot
Path=%JAVA_HOME%\bin;%Path%
```

## 项目特定配置

**项目位置:**
```
G:\project\claude-code\minimal-k8s-agent-demo\
```

**应用配置:**
```
src/main/resources/application.yml
src/main/resources/application-local.yml
```

**依赖安装:**
```bash
# 先安装 spring-ai-agent-utils (如果还没安装)
cd G:\project\claude-code\spring-ai-agent-utils
./mvnw.cmd clean install

# 然后编译主项目
cd G:\project\claude-code\minimal-k8s-agent-demo
./mvnw.cmd clean compile
```
