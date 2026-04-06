FROM python:3.11-slim

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
