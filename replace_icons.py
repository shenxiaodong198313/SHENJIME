#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
安卓应用图标替换脚本
将新生成的图标替换到安卓项目的mipmap目录中
"""

import os
import shutil

# 图标映射关系
ICON_MAPPING = {
    'drawable-mdpi_ic_launcher.png': 'app/src/main/res/mipmap-mdpi/ic_launcher.png',
    'drawable-hdpi_ic_launcher.png': 'app/src/main/res/mipmap-hdpi/ic_launcher.png', 
    'drawable-xhdpi_ic_launcher.png': 'app/src/main/res/mipmap-xhdpi/ic_launcher.png',
    'drawable-xxhdpi_ic_launcher.png': 'app/src/main/res/mipmap-xxhdpi/ic_launcher.png',
    'drawable-xxxhdpi_ic_launcher.png': 'app/src/main/res/mipmap-xxxhdpi/ic_launcher.png'
}

def replace_icons():
    """替换应用图标"""
    source_dir = r"C:\ProjectD\appicon"
    
    print("🔄 开始替换安卓应用图标...")
    print(f"源目录: {source_dir}")
    print("-" * 50)
    
    success_count = 0
    total_count = len(ICON_MAPPING)
    
    for source_file, target_file in ICON_MAPPING.items():
        source_path = os.path.join(source_dir, source_file)
        
        try:
            # 检查源文件是否存在
            if not os.path.exists(source_path):
                print(f"⚠️  源文件不存在: {source_path}")
                continue
            
            # 确保目标目录存在
            target_dir = os.path.dirname(target_file)
            os.makedirs(target_dir, exist_ok=True)
            
            # 复制文件
            shutil.copy2(source_path, target_file)
            print(f"✅ 替换成功: {source_file} -> {target_file}")
            success_count += 1
            
            # 同时复制为圆形图标（如果不存在的话）
            round_target = target_file.replace('ic_launcher.png', 'ic_launcher_round.png')
            if not os.path.exists(round_target) or os.path.getsize(round_target) < 1000:
                shutil.copy2(source_path, round_target)
                print(f"✅ 同时更新圆形图标: {round_target}")
            
        except Exception as e:
            print(f"❌ 替换失败 {source_file}: {e}")
    
    print("-" * 50)
    print(f"🎉 图标替换完成！成功: {success_count}/{total_count}")
    
    # 检查是否有ldpi图标需要处理
    ldpi_source = os.path.join(source_dir, 'drawable-ldpi_ic_launcher.png')
    if os.path.exists(ldpi_source):
        ldpi_target = 'app/src/main/res/mipmap-ldpi/ic_launcher.png'
        ldpi_dir = os.path.dirname(ldpi_target)
        os.makedirs(ldpi_dir, exist_ok=True)
        shutil.copy2(ldpi_source, ldpi_target)
        print(f"✅ 额外处理ldpi图标: {ldpi_target}")
    
    return success_count == total_count

if __name__ == "__main__":
    success = replace_icons()
    if success:
        print("\n🎊 所有图标替换成功！可以开始编译APK了。")
    else:
        print("\n⚠️  部分图标替换失败，请检查错误信息。") 