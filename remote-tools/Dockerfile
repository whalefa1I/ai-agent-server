FROM python:3.11-slim

# 安装常用工具（基础开发环境）
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    coreutils \
    grep \
    findutils \
    diffutils \
    curl \
    wget \
    ca-certificates \
    git \
    sed \
    gawk \
    jq \
    zip \
    unzip \
    tar \
    gzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

RUN mkdir -p /tmp/agent-tools

EXPOSE 8000

CMD ["python", "main.py"]
