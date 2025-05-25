// 图标生成模块
class IconGenerator {
    constructor() {
        // 安卓图标尺寸规范
        this.androidSizes = [
            { name: 'ldpi', size: 36, folder: 'drawable-ldpi' },
            { name: 'mdpi', size: 48, folder: 'drawable-mdpi' },
            { name: 'hdpi', size: 72, folder: 'drawable-hdpi' },
            { name: 'xhdpi', size: 96, folder: 'drawable-xhdpi' },
            { name: 'xxhdpi', size: 144, folder: 'drawable-xxhdpi' },
            { name: 'xxxhdpi', size: 192, folder: 'drawable-xxxhdpi' }
        ];
        
        this.generatedIcons = [];
    }

    // 生成所有尺寸的图标
    async generateAllSizes(sourceCanvas, settings = {}) {
        if (!sourceCanvas) {
            throw new Error('源图片不能为空');
        }

        this.generatedIcons = [];
        const results = [];

        try {
            for (const sizeConfig of this.androidSizes) {
                const iconData = await this.generateSingleIcon(
                    sourceCanvas, 
                    sizeConfig.size, 
                    sizeConfig,
                    settings
                );
                
                this.generatedIcons.push(iconData);
                results.push(iconData);
            }

            return results;
        } catch (error) {
            console.error('图标生成失败:', error);
            throw error;
        }
    }

    // 生成单个尺寸的图标
    async generateSingleIcon(sourceCanvas, targetSize, sizeConfig, settings = {}) {
        return new Promise((resolve) => {
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            
            canvas.width = targetSize;
            canvas.height = targetSize;

            // 设置背景
            if (settings.backgroundColor && settings.backgroundColor !== 'transparent') {
                ctx.fillStyle = settings.backgroundColor;
                ctx.fillRect(0, 0, targetSize, targetSize);
            }

            // 计算内边距
            const padding = settings.padding ? (settings.padding / 100) * targetSize : 0;
            const contentSize = targetSize - padding * 2;
            
            // 计算源图片的缩放比例
            const sourceSize = Math.min(sourceCanvas.width, sourceCanvas.height);
            const scale = contentSize / sourceSize;
            
            // 计算绘制位置和尺寸
            const drawWidth = sourceCanvas.width * scale;
            const drawHeight = sourceCanvas.height * scale;
            const drawX = (targetSize - drawWidth) / 2;
            const drawY = (targetSize - drawHeight) / 2;

            // 应用圆角
            if (settings.cornerRadius && settings.cornerRadius > 0) {
                const radius = (settings.cornerRadius / 100) * targetSize / 2;
                this.drawRoundedImage(ctx, sourceCanvas, drawX, drawY, drawWidth, drawHeight, radius);
            } else {
                ctx.drawImage(sourceCanvas, drawX, drawY, drawWidth, drawHeight);
            }

            // 生成图标数据
            const quality = settings.quality || 0.9;
            const dataURL = canvas.toDataURL('image/png', quality);
            
            resolve({
                name: sizeConfig.name,
                size: targetSize,
                folder: sizeConfig.folder,
                canvas: canvas,
                dataURL: dataURL,
                blob: this.dataURLToBlob(dataURL),
                filename: `ic_launcher.png`
            });
        });
    }

    // 绘制圆角图片
    drawRoundedImage(ctx, img, x, y, width, height, radius) {
        ctx.save();
        ctx.beginPath();
        ctx.moveTo(x + radius, y);
        ctx.lineTo(x + width - radius, y);
        ctx.quadraticCurveTo(x + width, y, x + width, y + radius);
        ctx.lineTo(x + width, y + height - radius);
        ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
        ctx.lineTo(x + radius, y + height);
        ctx.quadraticCurveTo(x, y + height, x, y + height - radius);
        ctx.lineTo(x, y + radius);
        ctx.quadraticCurveTo(x, y, x + radius, y);
        ctx.closePath();
        ctx.clip();
        ctx.drawImage(img, x, y, width, height);
        ctx.restore();
    }

    // 将DataURL转换为Blob
    dataURLToBlob(dataURL) {
        const arr = dataURL.split(',');
        const mime = arr[0].match(/:(.*?);/)[1];
        const bstr = atob(arr[1]);
        let n = bstr.length;
        const u8arr = new Uint8Array(n);
        
        while (n--) {
            u8arr[n] = bstr.charCodeAt(n);
        }
        
        return new Blob([u8arr], { type: mime });
    }

    // 创建ZIP包
    async createZipPackage() {
        if (this.generatedIcons.length === 0) {
            throw new Error('没有生成的图标可以打包');
        }

        const zip = new JSZip();
        
        // 添加图标文件到对应文件夹
        for (const icon of this.generatedIcons) {
            const folderPath = `res/${icon.folder}`;
            
            // 将DataURL转换为base64数据
            const base64Data = icon.dataURL.split(',')[1];
            zip.file(`${folderPath}/${icon.filename}`, base64Data, { base64: true });
        }

        // 添加说明文件
        const readme = this.generateReadme();
        zip.file('README.md', readme);

        // 添加Android资源配置文件示例
        const manifestExample = this.generateManifestExample();
        zip.file('AndroidManifest_example.xml', manifestExample);

        return zip;
    }

    // 生成说明文件
    generateReadme() {
        return `# Android App图标包

## 包含的图标尺寸

${this.androidSizes.map(size => 
    `- **${size.name}** (${size.size}x${size.size}px) - ${size.folder}/ic_launcher.png`
).join('\n')}

## 使用方法

1. 将对应的文件夹复制到你的Android项目的 \`app/src/main/res/\` 目录下
2. 在 \`AndroidManifest.xml\` 中设置应用图标：
   \`\`\`xml
   <application
       android:icon="@drawable/ic_launcher"
       ... >
   \`\`\`

## 图标规范

- 所有图标都是PNG格式
- 支持透明背景
- 遵循Android官方图标设计规范
- 适配不同屏幕密度

## 生成时间

${new Date().toLocaleString('zh-CN')}

---
由App图标生成工具自动生成
`;
    }

    // 生成AndroidManifest示例
    generateManifestExample() {
        return `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.yourapp">

    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/AppTheme">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
    </application>

</manifest>`;
    }

    // 下载单个图标
    downloadSingleIcon(iconData, filename) {
        const link = document.createElement('a');
        link.href = iconData.dataURL;
        link.download = filename || `${iconData.name}_${iconData.size}x${iconData.size}.png`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    }

    // 下载所有图标（分别下载）
    downloadAllIcons() {
        this.generatedIcons.forEach(icon => {
            setTimeout(() => {
                this.downloadSingleIcon(icon, `${icon.folder}_ic_launcher.png`);
            }, 100 * this.generatedIcons.indexOf(icon));
        });
    }

    // 下载ZIP包
    async downloadZipPackage(filename = 'android_icons.zip') {
        try {
            const zip = await this.createZipPackage();
            const content = await zip.generateAsync({ type: 'blob' });
            
            const link = document.createElement('a');
            link.href = URL.createObjectURL(content);
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(link.href);
        } catch (error) {
            console.error('ZIP包下载失败:', error);
            // 如果ZIP失败，回退到分别下载
            this.downloadAllIcons();
        }
    }

    // 获取生成的图标数据
    getGeneratedIcons() {
        return this.generatedIcons;
    }

    // 清空生成的图标
    clearGeneratedIcons() {
        this.generatedIcons = [];
    }

    // 获取支持的尺寸配置
    getSupportedSizes() {
        return this.androidSizes;
    }

    // 预览图标
    previewIcon(iconData) {
        const img = new Image();
        img.src = iconData.dataURL;
        img.style.maxWidth = '100%';
        img.style.maxHeight = '100%';
        return img;
    }

    // 验证图标质量
    validateIconQuality(iconData) {
        const issues = [];
        
        // 检查尺寸
        if (iconData.size < 36) {
            issues.push('图标尺寸过小，可能影响显示效果');
        }
        
        // 检查文件大小（估算）
        const estimatedSize = iconData.dataURL.length * 0.75; // base64编码大约增加33%
        if (estimatedSize > 50000) { // 50KB
            issues.push('图标文件可能过大，建议优化');
        }
        
        return {
            isValid: issues.length === 0,
            issues: issues
        };
    }
}

// 导出模块
window.IconGenerator = IconGenerator; 