import json
import os

# MCP 配置
config = {
    "mcpServers": {
        "review-gate-v2": {
            "command": r"C:\Users\15811\cursor-extensions\review-gate-v2\venv\Scripts\python.exe",
            "args": [r"C:\Users\15811\cursor-extensions\review-gate-v2\review_gate_v2_mcp.py"],
            "env": {
                "PYTHONPATH": r"C:\Users\15811\cursor-extensions\review-gate-v2",
                "PYTHONUNBUFFERED": "1",
                "REVIEW_GATE_MODE": "cursor_integration"
            }
        }
    }
}

# 创建配置文件
mcp_file = os.path.expanduser("~/.cursor/mcp.json")
with open(mcp_file, 'w', encoding='utf-8') as f:
    json.dump(config, f, indent=2)

print(f"✅ MCP 配置文件已创建: {mcp_file}") 