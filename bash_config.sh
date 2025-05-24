#!/usr/bin/bash
# Cygwin Bash 全局默认配置脚本

echo "=== 配置 Cygwin Bash 为全局默认终端 ==="

# 1. 显示当前状态
echo "当前状态:"
echo "  当前shell: $0"
echo "  SHELL变量: $SHELL"
echo "  which bash: $(which bash)"

# 2. 设置环境变量
export SHELL=/usr/bin/bash
export PATH="/usr/bin:/usr/local/bin:$PATH"

echo ""
echo "配置后状态:"
echo "  SHELL变量: $SHELL"
echo "  which bash: $(which bash)"

# 3. 创建用户配置文件
cat > ~/.bashrc << 'EOF'
# Cygwin Bash 配置
export PATH="/usr/bin:/usr/local/bin:$PATH"
export SHELL=/usr/bin/bash

# 基本别名
alias ll='ls -la'
alias la='ls -A'
alias l='ls -CF'

# 提示符
PS1='\[\033[01;32m\]\u@\h\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '

echo "Cygwin Bash 配置已加载"
EOF

cat > ~/.bash_profile << 'EOF'
# 加载 .bashrc
if [ -f ~/.bashrc ]; then
    source ~/.bashrc
fi

# 环境变量
export JAVA_HOME="/cygdrive/c/Java/Java17"
export GRADLE_HOME="/cygdrive/c/Gradle/gradle-8.13"
export ANDROID_HOME="/cygdrive/c/Users/15811/AppData/Local/Android/Sdk"

export PATH="$JAVA_HOME/bin:$GRADLE_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF

echo ""
echo "✅ 配置文件已创建:"
echo "  ~/.bashrc"
echo "  ~/.bash_profile"

# 4. 重新加载配置
source ~/.bash_profile

echo ""
echo "✅ Cygwin Bash 已配置为默认终端！"
echo ""
echo "使用说明:"
echo "1. 重新打开终端窗口"
echo "2. 或者运行: source ~/.bash_profile"
echo "3. 验证: which bash 应该显示 /usr/bin/bash" 