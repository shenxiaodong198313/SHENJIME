# ~/.bashrc - Bash配置文件
# 确保Cygwin路径优先
export PATH="/usr/bin:/usr/local/bin:$PATH"

# 设置默认shell
export SHELL=/usr/bin/bash

# 基本别名
alias ll='ls -la'
alias la='ls -A'
alias l='ls -CF'

# 颜色支持
if [ -x /usr/bin/dircolors ]; then
    test -r ~/.dircolors && eval "$(dircolors -b ~/.dircolors)" || eval "$(dircolors -b)"
    alias ls='ls --color=auto'
    alias grep='grep --color=auto'
    alias fgrep='fgrep --color=auto'
    alias egrep='egrep --color=auto'
fi

# 提示符设置
PS1='\[\033[01;32m\]\u@\h\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '

# 历史记录设置
HISTCONTROL=ignoreboth
HISTSIZE=1000
HISTFILESIZE=2000

echo "Cygwin Bash 已配置为默认终端" 