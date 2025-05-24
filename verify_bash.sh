#!/usr/bin/bash
echo "=== Bash 配置验证 ==="
echo "SHELL变量: $SHELL"
echo "当前bash路径: $(which bash 2>/dev/null || echo '命令不可用')"
echo "bash版本: $(/usr/bin/bash --version | head -1)"
echo "PATH前几个目录:"
echo "$PATH" | tr ':' '\n' | head -5
echo ""
echo "✅ Cygwin Bash 配置验证完成！" 