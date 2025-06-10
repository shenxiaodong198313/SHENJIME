import json
import os

# MCP 配置 - 添加环境变量解决编码问题
config = {
    "mcpServers": {
        "review-gate-v2": {
            "command": r"C:\Users\15811\cursor-extensions\review-gate-v2\venv\Scripts\python.exe",
            "args": [r"C:\Users\15811\cursor-extensions\review-gate-v2\review_gate_v2_mcp.py"],
            "env": {
                "PYTHONPATH": r"C:\Users\15811\cursor-extensions\review-gate-v2",
                "PYTHONUNBUFFERED": "1",
                "PYTHONIOENCODING": "utf-8",
                "REVIEW_GATE_MODE": "cursor_integration",
                "TEMP": r"C:\tmp",
                "TMP": r"C:\tmp"
            }
        }
    }
}

# 更新配置文件
mcp_file = os.path.expanduser("~/.cursor/mcp.json")
with open(mcp_file, 'w', encoding='utf-8') as f:
    json.dump(config, f, indent=2)

print(f"✅ MCP 配置文件已更新: {mcp_file}")
print("✅ 添加了编码环境变量和临时目录配置") 