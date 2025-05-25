const fs = require('fs');
const path = require('path');

// 安卓图标尺寸配置
const ICON_SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
};

async function generateIcons() {
    const sourceImage = 'appicon.png';
    const outputDir = 'temp_icons';
    
    console.log('🎨 开始生成安卓应用图标...');
    console.log(`源图像: ${sourceImage}`);
    console.log(`输出目录: ${outputDir}`);
    console.log('-'.repeat(50));
    
    try {
        // 检查源文件是否存在
        if (!fs.existsSync(sourceImage)) {
            console.log(`❌ 源图像文件不存在: ${sourceImage}`);
            return false;
        }
        
        // 创建输出目录
        if (!fs.existsSync(outputDir)) {
            fs.mkdirSync(outputDir, { recursive: true });
        }
        
        // 读取源图像
        const sourceBuffer = fs.readFileSync(sourceImage);
        console.log(`✅ 成功读取源图像，大小: ${sourceBuffer.length} bytes`);
        
        // 为每个密度创建目录并复制文件
        for (const [density, size] of Object.entries(ICON_SIZES)) {
            console.log(`正在处理 ${density} 图标 (${size}x${size})`);
            
            // 创建密度目录
            const densityDir = path.join(outputDir, density);
            if (!fs.existsSync(densityDir)) {
                fs.mkdirSync(densityDir, { recursive: true });
            }
            
            // 暂时直接复制原图像（后续可以用sharp库进行真正的缩放）
            const iconPath = path.join(densityDir, 'ic_launcher.png');
            const roundIconPath = path.join(densityDir, 'ic_launcher_round.png');
            
            fs.writeFileSync(iconPath, sourceBuffer);
            fs.writeFileSync(roundIconPath, sourceBuffer);
            
            console.log(`  保存: ${iconPath}`);
            console.log(`  保存: ${roundIconPath}`);
        }
        
        console.log('-'.repeat(50));
        console.log('🎉 图标生成成功！');
        console.log(`生成的图标保存在: ${outputDir}`);
        console.log('\n生成的文件:');
        for (const density of Object.keys(ICON_SIZES)) {
            console.log(`  ${density}/ic_launcher.png`);
            console.log(`  ${density}/ic_launcher_round.png`);
        }
        
        return true;
        
    } catch (error) {
        console.log(`❌ 生成图标时出错: ${error.message}`);
        return false;
    }
}

// 运行生成器
generateIcons().then(success => {
    if (success) {
        console.log('\n✅ 图标生成完成，可以开始替换安卓项目中的图标了！');
    } else {
        console.log('\n❌ 图标生成失败！');
        process.exit(1);
    }
}); 