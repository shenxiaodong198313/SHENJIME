#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
安卓应用图标生成器
从原始图标生成所有安卓密度的图标尺寸
"""

from PIL import Image, ImageDraw
import os
import sys

# 安卓图标尺寸配置
ICON_SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

def create_round_icon(image, size):
    """创建圆形图标"""
    # 创建圆形遮罩
    mask = Image.new('L', (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size, size), fill=255)
    
    # 调整图像大小
    resized_image = image.resize((size, size), Image.Resampling.LANCZOS)
    
    # 创建圆形图标
    round_icon = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    round_icon.paste(resized_image, (0, 0))
    round_icon.putalpha(mask)
    
    return round_icon

def generate_icons(source_image_path, output_dir):
    """生成所有尺寸的图标"""
    try:
        # 打开原始图像
        print(f"正在加载图像: {source_image_path}")
        original_image = Image.open(source_image_path)
        
        # 转换为RGBA模式以支持透明度
        if original_image.mode != 'RGBA':
            original_image = original_image.convert('RGBA')
        
        print(f"原始图像尺寸: {original_image.size}")
        
        # 为每个密度生成图标
        for density, size in ICON_SIZES.items():
            print(f"正在生成 {density} 图标 ({size}x{size})")
            
            # 创建输出目录
            density_dir = os.path.join(output_dir, density)
            os.makedirs(density_dir, exist_ok=True)
            
            # 生成普通图标
            resized_icon = original_image.resize((size, size), Image.Resampling.LANCZOS)
            icon_path = os.path.join(density_dir, 'ic_launcher.png')
            resized_icon.save(icon_path, 'PNG', optimize=True)
            print(f"  保存: {icon_path}")
            
            # 生成圆形图标
            round_icon = create_round_icon(original_image, size)
            round_icon_path = os.path.join(density_dir, 'ic_launcher_round.png')
            round_icon.save(round_icon_path, 'PNG', optimize=True)
            print(f"  保存: {round_icon_path}")
        
        print("✅ 所有图标生成完成！")
        return True
        
    except Exception as e:
        print(f"❌ 生成图标时出错: {e}")
        return False

def main():
    source_image = "appicon.png"
    output_dir = "temp_icons"
    
    if not os.path.exists(source_image):
        print(f"❌ 源图像文件不存在: {source_image}")
        return False
    
    print("🎨 开始生成安卓应用图标...")
    print(f"源图像: {source_image}")
    print(f"输出目录: {output_dir}")
    print("-" * 50)
    
    success = generate_icons(source_image, output_dir)
    
    if success:
        print("-" * 50)
        print("🎉 图标生成成功！")
        print(f"生成的图标保存在: {output_dir}")
        print("\n生成的文件:")
        for density in ICON_SIZES.keys():
            print(f"  {density}/ic_launcher.png")
            print(f"  {density}/ic_launcher_round.png")
    
    return success

if __name__ == "__main__":
    main() 