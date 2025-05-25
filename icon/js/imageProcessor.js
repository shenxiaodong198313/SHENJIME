// 图像处理模块
class ImageProcessor {
    constructor() {
        this.originalImage = null;
        this.processedCanvas = null;
        this.currentSettings = {
            backgroundColor: 'transparent',
            padding: 10,
            cornerRadius: 0,
            quality: 0.9
        };
    }

    // 加载图片文件
    async loadImage(file) {
        return new Promise((resolve, reject) => {
            if (!file || !file.type.startsWith('image/')) {
                reject(new Error('请选择有效的图片文件'));
                return;
            }

            const reader = new FileReader();
            reader.onload = (e) => {
                try {
                    const img = new Image();
                    img.onload = () => {
                        try {
                            console.log('图片加载成功:', img.width, 'x', img.height);
                            this.originalImage = img;
                            this.updatePreview();
                            resolve(img);
                        } catch (error) {
                            console.error('图片处理错误:', error);
                            reject(new Error('图片处理失败: ' + error.message));
                        }
                    };
                    img.onerror = (error) => {
                        console.error('图片加载错误:', error);
                        reject(new Error('图片格式不支持或文件损坏'));
                    };
                    img.src = e.target.result;
                } catch (error) {
                    console.error('创建图片对象失败:', error);
                    reject(new Error('图片创建失败'));
                }
            };
            reader.onerror = (error) => {
                console.error('文件读取错误:', error);
                reject(new Error('文件读取失败'));
            };
            reader.readAsDataURL(file);
        });
    }

    // 更新预览
    updatePreview() {
        try {
            if (!this.originalImage) {
                console.warn('没有原始图片，跳过预览更新');
                return;
            }

            const canvas = document.getElementById('previewCanvas');
            if (!canvas) {
                console.error('找不到预览画布元素');
                return;
            }

            const ctx = canvas.getContext('2d');
            if (!ctx) {
                console.error('无法获取画布上下文');
                return;
            }
            
            // 清空画布
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            
            // 计算缩放比例以适应画布
            const scale = Math.min(
                canvas.width / this.originalImage.width,
                canvas.height / this.originalImage.height
            );
            
            const scaledWidth = this.originalImage.width * scale;
            const scaledHeight = this.originalImage.height * scale;
            
            // 计算居中位置
            const x = (canvas.width - scaledWidth) / 2;
            const y = (canvas.height - scaledHeight) / 2;
            
            console.log('预览参数:', { scale, scaledWidth, scaledHeight, x, y });
            
            // 应用设置
            this.applySettings(ctx, canvas, x, y, scaledWidth, scaledHeight);
            
            // 保存处理后的canvas
            this.processedCanvas = this.createProcessedCanvas();
            
            console.log('预览更新完成');
        } catch (error) {
            console.error('预览更新失败:', error);
        }
    }

    // 应用设置到预览
    applySettings(ctx, canvas, x, y, width, height) {
        // 清空画布
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        
        // 设置背景
        if (this.currentSettings.backgroundColor !== 'transparent') {
            ctx.fillStyle = this.currentSettings.backgroundColor;
            ctx.fillRect(0, 0, canvas.width, canvas.height);
        }

        // 计算内边距
        const padding = (this.currentSettings.padding / 100) * Math.min(width, height);
        const paddedX = x + padding;
        const paddedY = y + padding;
        const paddedWidth = width - padding * 2;
        const paddedHeight = height - padding * 2;

        // 确保尺寸为正数
        if (paddedWidth <= 0 || paddedHeight <= 0) {
            return;
        }

        // 应用圆角
        if (this.currentSettings.cornerRadius > 0) {
            const radius = (this.currentSettings.cornerRadius / 100) * Math.min(paddedWidth, paddedHeight) / 2;
            this.drawRoundedImage(ctx, this.originalImage, paddedX, paddedY, paddedWidth, paddedHeight, radius);
        } else {
            ctx.drawImage(this.originalImage, paddedX, paddedY, paddedWidth, paddedHeight);
        }
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

    // 创建处理后的canvas
    createProcessedCanvas() {
        if (!this.originalImage) return null;

        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        
        // 设置canvas尺寸为原图尺寸
        canvas.width = this.originalImage.width;
        canvas.height = this.originalImage.height;
        
        // 应用设置
        this.applySettings(ctx, canvas, 0, 0, canvas.width, canvas.height);
        
        return canvas;
    }

    // 去除背景
    async removeBackground() {
        if (!this.originalImage) return;

        try {
            // 创建临时canvas获取图像数据
            const tempCanvas = document.createElement('canvas');
            const tempCtx = tempCanvas.getContext('2d');
            tempCanvas.width = this.originalImage.width;
            tempCanvas.height = this.originalImage.height;
            
            tempCtx.drawImage(this.originalImage, 0, 0);
            const imageData = tempCtx.getImageData(0, 0, tempCanvas.width, tempCanvas.height);
            
            // 使用背景去除算法
            const processedCanvas = await BackgroundRemover.removeBackground(imageData);
            
            // 创建新的图片对象
            const processedImg = new Image();
            processedImg.onload = () => {
                this.originalImage = processedImg;
                this.updatePreview();
            };
            processedImg.src = processedCanvas.toDataURL();
            
            return processedCanvas;
        } catch (error) {
            console.error('背景去除失败:', error);
            throw error;
        }
    }

    // 更新设置
    updateSettings(newSettings) {
        this.currentSettings = { ...this.currentSettings, ...newSettings };
        this.updatePreview();
    }

    // 获取处理后的图片数据URL
    getProcessedImageDataURL(format = 'image/png') {
        if (!this.processedCanvas) return null;
        return this.processedCanvas.toDataURL(format, this.currentSettings.quality);
    }

    // 获取原图数据URL
    getOriginalImageDataURL() {
        if (!this.originalImage) return null;
        
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        canvas.width = this.originalImage.width;
        canvas.height = this.originalImage.height;
        ctx.drawImage(this.originalImage, 0, 0);
        
        return canvas.toDataURL();
    }

    // 重置处理
    reset() {
        this.originalImage = null;
        this.processedCanvas = null;
        this.currentSettings = {
            backgroundColor: 'transparent',
            padding: 10,
            cornerRadius: 0,
            quality: 0.9
        };
        
        // 清空预览
        const canvas = document.getElementById('previewCanvas');
        if (canvas) {
            const ctx = canvas.getContext('2d');
            ctx.clearRect(0, 0, canvas.width, canvas.height);
        }
    }

    // 检查是否有图片
    hasImage() {
        return this.originalImage !== null;
    }

    // 获取图片信息
    getImageInfo() {
        if (!this.originalImage) return null;
        
        return {
            width: this.originalImage.width,
            height: this.originalImage.height,
            aspectRatio: this.originalImage.width / this.originalImage.height
        };
    }
}

// 导出模块
window.ImageProcessor = ImageProcessor; 