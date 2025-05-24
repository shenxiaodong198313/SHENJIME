# ~/.bash_profile - Bash登录配置文件

# 如果存在.bashrc，则加载它
if [ -f ~/.bashrc ]; then
    source ~/.bashrc
fi

# 确保使用Cygwin bash
export SHELL=/usr/bin/bash

# 设置默认编辑器
export EDITOR=nano

# Java环境变量
export JAVA_HOME="/cygdrive/c/Java/Java17"
export PATH="$JAVA_HOME/bin:$PATH"

# Gradle环境变量
export GRADLE_HOME="/cygdrive/c/Gradle/gradle-8.13"
export PATH="$GRADLE_HOME/bin:$PATH"

# Android SDK
export ANDROID_HOME="/cygdrive/c/Users/15811/AppData/Local/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

echo "Bash profile 已加载 - 使用 Cygwin Bash" 