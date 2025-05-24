#!/usr/bin/bash
# 设置Cygwin Bash为全局默认终端的脚本

echo "正在配置Cygwin Bash为默认终端..."

# 强制设置PATH，让/usr/bin优先
export PATH="/usr/bin:/usr/local/bin:/bin:$PATH"

# 验证bash路径
echo "当前bash路径: $(which bash)"
echo "当前shell: $SHELL"

# 设置环境变量
export SHELL=/usr/bin/bash

# 创建符号链接（如果需要）
if [ ! -f /usr/local/bin/bash ]; then
    mkdir -p /usr/local/bin
    ln -sf /usr/bin/bash /usr/local/bin/bash
    echo "已创建bash符号链接"
fi

# 验证配置
echo "验证配置:"
echo "  which bash: $(which bash)"
echo "  bash版本: $(bash --version | head -1)"
echo "  当前shell: $0"

echo "Cygwin Bash 配置完成！" 