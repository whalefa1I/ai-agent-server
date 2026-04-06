FROM python:3.11-slim

# 安装常用工具（对齐开发环境，支持更多命令类型）
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Shell 基础
    bash \
    coreutils \
    grep \
    findutils \
    diffutils \
    # 网络工具
    curl \
    wget \
    ca-certificates \
    gnupg \
    # 版本控制
    git \
    # 文本处理
    sed \
    awk \
    jq \
    # 压缩/归档
    zip \
    unzip \
    tar \
    gzip \
    # 构建工具（可选，支持简单编译任务）
    build-essential \
    make \
    # 清理缓存
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 安装依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制应用
COPY main.py .

# 创建数据目录
RUN mkdir -p /tmp/agent-tools

# 暴露端口
EXPOSE 8000

# 启动命令
CMD ["python", "main.py"]
