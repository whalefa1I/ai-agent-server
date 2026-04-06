"""
AI Agent Remote Tool Executor - MVP
最小远程工具执行服务
"""

from fastapi import FastAPI, HTTPException, Header, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import Optional, Dict, Any
import os
import subprocess
import shutil
from pathlib import Path
import time
import logging

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="AI Agent Remote Tool Executor", version="0.1.0")

# 配置
WORK_DIR = Path(os.getenv("WORK_DIR", "/tmp/agent-tools"))
TTL_SECONDS = int(os.getenv("TTL_SECONDS", "86400"))  # 默认 24 小时


class ToolRequest(BaseModel):
    tool_name: str
    input: Dict[str, Any] = {}


class ToolResponse(BaseModel):
    success: bool
    output: Optional[str] = None
    error: Optional[str] = None
    duration_ms: Optional[int] = None


# ============== 工具执行 ==============

@app.post("/tools/execute")
async def execute_tool(
    request: ToolRequest,
    x_user_id: Optional[str] = Header(None, alias="X-User-ID"),
    x_session_id: Optional[str] = Header(None, alias="X-Session-ID"),
) -> ToolResponse:
    """执行远程工具"""
    if not x_user_id:
        raise HTTPException(status_code=400, detail="Missing X-User-ID header")

    user_work_dir = WORK_DIR / x_user_id
    session_work_dir = user_work_dir / (x_session_id or "default")

    # 创建用户工作目录
    session_work_dir.mkdir(parents=True, exist_ok=True)

    start_time = time.time()

    try:
        # 直接使用 Spring Boot 工具命名（统一命名规范）
        tool_name = request.tool_name

        if tool_name == "bash":
            result = await execute_bash(request.input, session_work_dir)
        elif tool_name == "file_read":
            result = await execute_file_read(request.input, session_work_dir)
        elif tool_name == "file_write":
            result = await execute_file_write(request.input, session_work_dir)
        elif tool_name == "file_list":
            result = await execute_file_list(request.input, session_work_dir)
        elif tool_name == "glob" or tool_name == "grep":
            # glob/grep 不支持，返回错误
            return ToolResponse(
                success=False,
                error=f"Tool {tool_name} is not supported in remote mode. Use local execution."
            )
        else:
            return ToolResponse(
                success=False,
                error=f"Unknown tool: {tool_name}"
            )
        return result
    except Exception as e:
        logger.exception("Tool execution failed")
        return ToolResponse(
            success=False,
            error=str(e),
            duration_ms=int((time.time() - start_time) * 1000)
        )


async def execute_bash(input_data: Dict[str, Any], work_dir: Path) -> ToolResponse:
    """执行 Bash 命令"""
    command = input_data.get("command", "")

    if not command:
        return ToolResponse(success=False, error="Empty command")

    # 危险命令检测
    dangerous_patterns = ["rm -rf /", "dd if=/", "mkfs", "chmod -R 777 /"]
    for pattern in dangerous_patterns:
        if pattern in command:
            return ToolResponse(success=False, error=f"Dangerous command detected: {pattern}")

    try:
        result = subprocess.run(
            command,
            shell=True,
            capture_output=True,
            text=True,
            timeout=input_data.get("timeout", 60),
            cwd=work_dir
        )
        output = result.stdout if result.returncode == 0 else None
        error = result.stderr if result.returncode != 0 else None
        return ToolResponse(
            success=result.returncode == 0,
            output=output,
            error=error
        )
    except subprocess.TimeoutExpired:
        return ToolResponse(success=False, error="Command timeout")


async def execute_file_read(input_data: Dict[str, Any], work_dir: Path) -> ToolResponse:
    """读取文件"""
    file_path = input_data.get("path", "")

    if not file_path:
        return ToolResponse(success=False, error="Missing file path")

    try:
        full_path = Path(file_path) if file_path.startswith("/") else work_dir / file_path
        full_path = full_path.resolve()

        # 安全检查：确保文件在工作目录内
        if not str(full_path).startswith(str(work_dir.resolve())):
            return ToolResponse(success=False, error="Access denied: path outside work directory")

        if not full_path.exists():
            return ToolResponse(success=False, error=f"File not found: {file_path}")

        content = full_path.read_text()
        return ToolResponse(success=True, output=content)
    except Exception as e:
        return ToolResponse(success=False, error=str(e))


async def execute_file_write(input_data: Dict[str, Any], work_dir: Path) -> ToolResponse:
    """写入文件"""
    file_path = input_data.get("path", "")
    content = input_data.get("content", "")

    if not file_path:
        return ToolResponse(success=False, error="Missing file path")

    try:
        full_path = Path(file_path) if file_path.startswith("/") else work_dir / file_path
        full_path = full_path.resolve()

        # 安全检查
        if not str(full_path).startswith(str(work_dir.resolve())):
            return ToolResponse(success=False, error="Access denied: path outside work directory")

        full_path.parent.mkdir(parents=True, exist_ok=True)
        full_path.write_text(content)
        return ToolResponse(success=True, output=f"File written: {file_path}")
    except Exception as e:
        return ToolResponse(success=False, error=str(e))


async def execute_file_list(input_data: Dict[str, Any], work_dir: Path) -> ToolResponse:
    """列出目录内容"""
    path = input_data.get("path", ".")
    pattern = input_data.get("pattern", "*")

    try:
        search_path = work_dir / path if not path.startswith("/") else Path(path)
        files = [str(f.relative_to(work_dir)) for f in search_path.glob(pattern)]
        return ToolResponse(success=True, output="\n".join(files))
    except Exception as e:
        return ToolResponse(success=False, error=str(e))


# ============== 健康检查 ==============

@app.get("/health")
async def health_check():
    """健康检查"""
    return {"status": "UP", "version": "0.1.0"}


@app.get("/metrics")
async def metrics():
    """Prometheus 格式指标"""
    total_users = len(list(WORK_DIR.iterdir())) if WORK_DIR.exists() else 0
    return f"""# HELP active_users 活跃用户数
# TYPE active_users gauge
active_users {total_users}
"""


# ============== 生命周期管理 ==============

@app.on_event("startup")
async def startup_event():
    """启动时清理过期文件"""
    if not WORK_DIR.exists():
        WORK_DIR.mkdir(parents=True, exist_ok=True)
        return

    current_time = time.time()
    cleaned_count = 0

    for user_dir in WORK_DIR.iterdir():
        if not user_dir.is_dir():
            continue
        for session_dir in user_dir.iterdir():
            if not session_dir.is_dir():
                continue
            dir_mtime = session_dir.stat().st_mtime
            age_seconds = current_time - dir_mtime
            if age_seconds > TTL_SECONDS:
                try:
                    shutil.rmtree(session_dir)
                    cleaned_count += 1
                except Exception:
                    pass

    if cleaned_count > 0:
        logger.info(f"Cleaned up {cleaned_count} expired sessions")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8000")))
