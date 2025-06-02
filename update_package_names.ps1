# 批量修改MNN模块包名的PowerShell脚本
# 将 com.alibaba.mnnllm.android 改为 com.shenji.aikeyboard.mnn

$sourceDir = "app\src\main\java\com\shenji\aikeyboard\mnn"
$oldPackage = "com.alibaba.mnnllm.android"
$newPackage = "com.shenji.aikeyboard.mnn"

Write-Host "开始批量修改包名..."
Write-Host "源目录: $sourceDir"
Write-Host "旧包名: $oldPackage"
Write-Host "新包名: $newPackage"

# 获取所有Kotlin和Java文件
$files = Get-ChildItem -Path $sourceDir -Recurse -Include "*.kt", "*.java"

Write-Host "找到 $($files.Count) 个文件需要处理"

foreach ($file in $files) {
    Write-Host "处理文件: $($file.FullName)"
    
    # 读取文件内容
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    
    # 替换包名声明
    $content = $content -replace "package $oldPackage", "package $newPackage"
    
    # 替换import语句
    $content = $content -replace "import $oldPackage", "import $newPackage"
    
    # 保存文件
    Set-Content -Path $file.FullName -Value $content -Encoding UTF8
}

Write-Host "包名修改完成！" 