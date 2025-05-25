#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修复图标资源冲突
删除PNG版本的圆形图标，保留XML版本
"""

import os
import glob

def fix_icon_conflicts():
    """修复图标资源冲突"""
    print("🔧 开始修复图标资源冲突...")
    
    # 查找所有mipmap目录下的ic_launcher_round.png文件
    pattern = "app/src/main/res/mipmap-*/ic_launcher_round.png"
    png_files = glob.glob(pattern)
    
    print(f"找到 {len(png_files)} 个PNG圆形图标文件需要删除:")
    
    for png_file in png_files:
        try:
            if os.path.exists(png_file):
                os.remove(png_file)
                print(f"✅ 删除: {png_file}")
            else:
                print(f"⚠️  文件不存在: {png_file}")
        except Exception as e:
            print(f"❌ 删除失败 {png_file}: {e}")
    
    print("-" * 50)
    print("🎉 图标冲突修复完成！")
    
    # 验证XML文件是否存在
    xml_files = glob.glob("app/src/main/res/mipmap-*/ic_launcher_round.xml")
    print(f"\n保留的XML圆形图标文件: {len(xml_files)} 个")
    for xml_file in xml_files:
        print(f"  ✅ {xml_file}")

if __name__ == "__main__":
    fix_icon_conflicts() 