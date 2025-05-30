# 神迹键盘开发环境配置脚本
# 设置Java 21和Gradle 8.13环境

Write-Host "正在配置神迹键盘开发环境..." -ForegroundColor Green

# 设置Java 21环境
$JAVA_HOME = "C:\Java\Java21\jdk-21"
$GRADLE_HOME = "C:\Gradle\gradle-8.13"

Write-Host "设置JAVA_HOME: $JAVA_HOME" -ForegroundColor Yellow
$env:JAVA_HOME = $JAVA_HOME
[Environment]::SetEnvironmentVariable("JAVA_HOME", $JAVA_HOME, "User")

Write-Host "设置GRADLE_HOME: $GRADLE_HOME" -ForegroundColor Yellow
$env:GRADLE_HOME = $GRADLE_HOME
[Environment]::SetEnvironmentVariable("GRADLE_HOME", $GRADLE_HOME, "User")

# 更新PATH环境变量
$currentPath = [Environment]::GetEnvironmentVariable("PATH", "User")

# 移除旧的Java和Gradle路径
$pathItems = $currentPath -split ";"
$filteredPath = $pathItems | Where-Object { 
    $_ -notlike "*Java17*" -and 
    $_ -notlike "*gradle-8.3*" -and 
    $_ -notlike "*gradle-8.4*" -and
    $_ -notlike "*Java\Java21*" -and
    $_ -notlike "*gradle-8.13*"
}

# 添加新的Java和Gradle路径
$newPath = ($filteredPath + "$JAVA_HOME\bin" + "$GRADLE_HOME\bin") -join ";"

Write-Host "更新PATH环境变量..." -ForegroundColor Yellow
$env:PATH = $newPath
[Environment]::SetEnvironmentVariable("PATH", $newPath, "User")

# 验证配置
Write-Host "`n验证环境配置:" -ForegroundColor Green

if (Test-Path "$JAVA_HOME\bin\java.exe") {
    Write-Host "✅ Java 21 路径正确" -ForegroundColor Green
    & "$JAVA_HOME\bin\java.exe" -version
} else {
    Write-Host "❌ Java 21 路径不存在: $JAVA_HOME" -ForegroundColor Red
}

if (Test-Path "$GRADLE_HOME\bin\gradle.bat") {
    Write-Host "✅ Gradle 8.13 路径正确" -ForegroundColor Green
} else {
    Write-Host "❌ Gradle 8.13 路径不存在: $GRADLE_HOME" -ForegroundColor Red
}

Write-Host "`n环境配置完成！" -ForegroundColor Green
Write-Host "请重新启动PowerShell或IDE以使环境变量生效。" -ForegroundColor Yellow 