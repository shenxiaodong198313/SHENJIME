const sharp = require('sharp');
const path = require('path');
const fs = require('fs').promises;

class IconGenerator {
    constructor() {
        // Android图标规范配置
        this.androidIconSizes = {
            // drawable目录 - 用于应用内图标
            'drawable-mdpi': { size: 48, folder: 'drawable-mdpi' },
            'drawable-hdpi': { size: 72, folder: 'drawable-hdpi' },
            'drawable-xhdpi': { size: 96, folder: 'drawable-xhdpi' },
            'drawable-xxhdpi': { size: 144, folder: 'drawable-xxhdpi' },
            'drawable-xxxhdpi': { size: 192, folder: 'drawable-xxxhdpi' },
            
            // mipmap目录 - 用于启动器图标
            'mipmap-mdpi': { size: 48, folder: 'mipmap-mdpi' },
            'mipmap-hdpi': { size: 72, folder: 'mipmap-hdpi' },
            'mipmap-xhdpi': { size: 96, folder: 'mipmap-xhdpi' },
            'mipmap-xxhdpi': { size: 144, folder: 'mipmap-xxhdpi' },
            'mipmap-xxxhdpi': { size: 192, folder: 'mipmap-xxxhdpi' },
            
            // 通知图标 (白色图标，透明背景)
            'drawable-mdpi-notification': { size: 24, folder: 'drawable-mdpi', suffix: '_notification' },
            'drawable-hdpi-notification': { size: 36, folder: 'drawable-hdpi', suffix: '_notification' },
            'drawable-xhdpi-notification': { size: 48, folder: 'drawable-xhdpi', suffix: '_notification' },
            'drawable-xxhdpi-notification': { size: 72, folder: 'drawable-xxhdpi', suffix: '_notification' },
            'drawable-xxxhdpi-notification': { size: 96, folder: 'drawable-xxxhdpi', suffix: '_notification' }
        };
        
        this.defaultOptions = {
            enableCompression: true,
            quality: 90,
            generateNotificationIcons: true,
            iconName: 'ic_launcher',
            outputFormat: 'png'
        };
    }

    /**
     * 生成Android图标
     * @param {string} imagePath - 原始图片路径
     * @param {string} outputPath - 输出目录路径
     * @param {object} options - 生成选项
     */
    async generateAndroidIcons(imagePath, outputPath, options = {}) {
        try {
            const config = { ...this.defaultOptions, ...options };
            const results = {
                success: true,
                generatedFiles: [],
                errors: [],
                summary: {
                    totalFiles: 0,
                    successCount: 0,
                    errorCount: 0
                }
            };

            // 验证输入图片
            await this.validateInputImage(imagePath);

            // 创建输出目录结构
            await this.createOutputDirectories(outputPath);

            // 加载原始图片
            const originalImage = sharp(imagePath);
            const metadata = await originalImage.metadata();
            
            console.log(`原始图片信息: ${metadata.width}x${metadata.height}, 格式: ${metadata.format}`);

            // 生成各种尺寸的图标
            for (const [key, iconConfig] of Object.entries(this.androidIconSizes)) {
                try {
                    // 跳过通知图标（如果未启用）
                    if (key.includes('notification') && !config.generateNotificationIcons) {
                        continue;
                    }

                    const fileName = this.generateFileName(config.iconName, iconConfig.suffix);
                    const outputDir = path.join(outputPath, iconConfig.folder);
                    const outputFile = path.join(outputDir, fileName);

                    await this.generateSingleIcon(
                        originalImage.clone(),
                        outputFile,
                        iconConfig.size,
                        config,
                        key.includes('notification')
                    );

                    results.generatedFiles.push({
                        size: `${iconConfig.size}x${iconConfig.size}`,
                        folder: iconConfig.folder,
                        fileName: fileName,
                        path: outputFile
                    });

                    results.summary.successCount++;
                    console.log(`✓ 生成成功: ${iconConfig.folder}/${fileName} (${iconConfig.size}x${iconConfig.size})`);

                } catch (error) {
                    const errorMsg = `生成 ${key} 时出错: ${error.message}`;
                    results.errors.push(errorMsg);
                    results.summary.errorCount++;
                    console.error(`✗ ${errorMsg}`);
                }
            }

            results.summary.totalFiles = results.summary.successCount + results.summary.errorCount;

            // 生成说明文件
            await this.generateReadmeFile(outputPath, results, config);

            return results;

        } catch (error) {
            console.error('生成Android图标时出错:', error);
            return {
                success: false,
                error: error.message,
                generatedFiles: [],
                errors: [error.message]
            };
        }
    }

    /**
     * 验证输入图片
     */
    async validateInputImage(imagePath) {
        try {
            await fs.access(imagePath);
            const image = sharp(imagePath);
            const metadata = await image.metadata();
            
            if (!metadata.width || !metadata.height) {
                throw new Error('无法读取图片尺寸信息');
            }

            if (metadata.width < 192 || metadata.height < 192) {
                console.warn('警告: 建议使用至少192x192像素的图片以获得最佳质量');
            }

            return true;
        } catch (error) {
            throw new Error(`图片验证失败: ${error.message}`);
        }
    }

    /**
     * 创建输出目录结构
     */
    async createOutputDirectories(outputPath) {
        const folders = new Set();
        
        Object.values(this.androidIconSizes).forEach(config => {
            folders.add(config.folder);
        });

        for (const folder of folders) {
            const dirPath = path.join(outputPath, folder);
            await fs.mkdir(dirPath, { recursive: true });
        }
    }

    /**
     * 生成单个图标
     */
    async generateSingleIcon(image, outputPath, size, config, isNotificationIcon = false) {
        let processedImage = image.resize(size, size, {
            kernel: sharp.kernel.lanczos3,
            fit: 'cover',
            position: 'center'
        });

        // 通知图标特殊处理（转为白色图标）
        if (isNotificationIcon) {
            processedImage = processedImage
                .greyscale()
                .threshold(128)
                .negate();
        }

        // 应用压缩设置
        if (config.enableCompression) {
            if (config.outputFormat === 'png') {
                processedImage = processedImage.png({
                    quality: config.quality,
                    compressionLevel: 9,
                    adaptiveFiltering: true
                });
            } else if (config.outputFormat === 'webp') {
                processedImage = processedImage.webp({
                    quality: config.quality,
                    lossless: false
                });
            }
        } else {
            processedImage = processedImage.png();
        }

        await processedImage.toFile(outputPath);
    }

    /**
     * 生成文件名
     */
    generateFileName(baseName, suffix = '') {
        return `${baseName}${suffix}.png`;
    }

    /**
     * 生成说明文件
     */
    async generateReadmeFile(outputPath, results, config) {
        const readmeContent = `# Android图标生成结果

## 生成统计
- 总文件数: ${results.summary.totalFiles}
- 成功生成: ${results.summary.successCount}
- 失败数量: ${results.summary.errorCount}

## 生成配置
- 图标名称: ${config.iconName}
- 输出格式: ${config.outputFormat}
- 启用压缩: ${config.enableCompression ? '是' : '否'}
- 压缩质量: ${config.quality}%
- 生成通知图标: ${config.generateNotificationIcons ? '是' : '否'}

## 生成的文件列表

### 启动器图标 (mipmap)
${results.generatedFiles
    .filter(file => file.folder.startsWith('mipmap'))
    .map(file => `- ${file.folder}/${file.fileName} (${file.size})`)
    .join('\n')}

### 应用内图标 (drawable)
${results.generatedFiles
    .filter(file => file.folder.startsWith('drawable') && !file.fileName.includes('notification'))
    .map(file => `- ${file.folder}/${file.fileName} (${file.size})`)
    .join('\n')}

${config.generateNotificationIcons ? `
### 通知图标 (drawable)
${results.generatedFiles
    .filter(file => file.fileName.includes('notification'))
    .map(file => `- ${file.folder}/${file.fileName} (${file.size})`)
    .join('\n')}
` : ''}

## 使用说明
1. 将生成的文件夹复制到Android项目的 \`src/main/res/\` 目录下
2. 在 \`AndroidManifest.xml\` 中设置应用图标:
   \`\`\`xml
   <application
       android:icon="@mipmap/${config.iconName}"
       android:roundIcon="@mipmap/${config.iconName}"
       ... >
   \`\`\`

## 错误信息
${results.errors.length > 0 ? results.errors.map(error => `- ${error}`).join('\n') : '无错误'}

---
生成时间: ${new Date().toLocaleString('zh-CN')}
生成工具: Android图标生成器 v1.0.0
`;

        const readmePath = path.join(outputPath, 'README.md');
        await fs.writeFile(readmePath, readmeContent, 'utf8');
    }
}

module.exports = IconGenerator; 