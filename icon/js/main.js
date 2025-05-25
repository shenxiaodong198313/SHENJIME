// 主控制脚本
class AppIconGenerator {
    constructor() {
        this.imageProcessor = new ImageProcessor();
        this.iconGenerator = new IconGenerator();
        this.currentStep = 1;
        
        this.initializeElements();
        this.bindEvents();
        this.updateStepIndicator();
    }

    // 初始化DOM元素
    initializeElements() {
        // 主要区域
        this.uploadSection = document.getElementById('uploadSection');
        this.editSection = document.getElementById('editSection');
        this.resultSection = document.getElementById('resultSection');
        
        // 上传相关
        this.uploadArea = document.getElementById('uploadArea');
        this.fileInput = document.getElementById('fileInput');
        this.selectFileBtn = document.getElementById('selectFileBtn');
        
        // 预览相关
        this.previewCanvas = document.getElementById('previewCanvas');
        this.originalImage = document.getElementById('originalImage');
        
        // 控制按钮
        this.removeBackgroundBtn = document.getElementById('removeBackgroundBtn');
        this.generateIconsBtn = document.getElementById('generateIconsBtn');
        this.downloadAllBtn = document.getElementById('downloadAllBtn');
        
        // 设置控件
        this.colorPresets = document.querySelectorAll('.color-preset');
        this.customColor = document.getElementById('customColor');
        this.iconPadding = document.getElementById('iconPadding');
        this.cornerRadius = document.getElementById('cornerRadius');
        this.compressionQuality = document.getElementById('compressionQuality');
        
        // 显示值的元素
        this.paddingValue = document.getElementById('paddingValue');
        this.radiusValue = document.getElementById('radiusValue');
        this.qualityValue = document.getElementById('qualityValue');
        
        // 状态显示
        this.processingStatus = document.getElementById('processingStatus');
        this.resultGrid = document.getElementById('resultGrid');
    }

    // 绑定事件
    bindEvents() {
        // 文件选择
        this.selectFileBtn.addEventListener('click', () => {
            this.fileInput.click();
        });
        
        this.fileInput.addEventListener('change', (e) => {
            if (e.target.files.length > 0) {
                this.handleFileSelect(e.target.files[0]);
            }
        });

        // 拖拽上传
        this.uploadArea.addEventListener('dragover', (e) => {
            e.preventDefault();
            this.uploadArea.classList.add('dragover');
        });

        this.uploadArea.addEventListener('dragleave', (e) => {
            e.preventDefault();
            this.uploadArea.classList.remove('dragover');
        });

        this.uploadArea.addEventListener('drop', (e) => {
            e.preventDefault();
            this.uploadArea.classList.remove('dragover');
            
            const files = e.dataTransfer.files;
            if (files.length > 0) {
                this.handleFileSelect(files[0]);
            }
        });

        // 去背景按钮
        this.removeBackgroundBtn.addEventListener('click', () => {
            this.handleRemoveBackground();
        });

        // 颜色预设
        this.colorPresets.forEach(preset => {
            preset.addEventListener('click', () => {
                this.handleColorPresetSelect(preset);
            });
        });

        // 自定义颜色
        this.customColor.addEventListener('change', (e) => {
            this.handleCustomColorChange(e.target.value);
        });

        // 滑块控件
        this.iconPadding.addEventListener('input', (e) => {
            this.paddingValue.textContent = e.target.value + '%';
            this.updateImageSettings();
        });

        this.cornerRadius.addEventListener('input', (e) => {
            this.radiusValue.textContent = e.target.value + '%';
            this.updateImageSettings();
        });

        this.compressionQuality.addEventListener('input', (e) => {
            this.qualityValue.textContent = Math.round(e.target.value * 100) + '%';
            this.updateImageSettings();
        });

        // 生成图标按钮
        this.generateIconsBtn.addEventListener('click', () => {
            this.handleGenerateIcons();
        });

        // 下载按钮
        this.downloadAllBtn.addEventListener('click', () => {
            this.handleDownloadAll();
        });
    }

    // 处理文件选择
    async handleFileSelect(file) {
        try {
            console.log('开始处理文件:', file.name, file.type, file.size);
            this.showLoading('正在加载图片...');
            
            // 验证文件类型
            if (!file.type.startsWith('image/')) {
                throw new Error('请选择有效的图片文件（PNG、JPG、JPEG）');
            }
            
            // 验证文件大小（限制为10MB）
            if (file.size > 10 * 1024 * 1024) {
                throw new Error('图片文件过大，请选择小于10MB的图片');
            }
            
            await this.imageProcessor.loadImage(file);
            
            // 显示原图预览
            const originalDataURL = this.imageProcessor.getOriginalImageDataURL();
            if (originalDataURL) {
                this.originalImage.src = originalDataURL;
            }
            
            // 切换到编辑步骤
            this.switchToStep(2);
            
            this.hideLoading();
            this.showSuccess('图片加载成功！');
            
        } catch (error) {
            console.error('文件处理错误:', error);
            this.hideLoading();
            this.showError('图片加载失败: ' + error.message);
        }
    }

    // 处理去背景
    async handleRemoveBackground() {
        if (!this.imageProcessor.hasImage()) {
            this.showError('请先选择图片');
            return;
        }

        try {
            this.showProcessing('正在去除背景...');
            
            await this.imageProcessor.removeBackground();
            
            this.hideProcessing();
            this.showSuccess('背景去除成功！');
            
        } catch (error) {
            this.hideProcessing();
            this.showError('背景去除失败: ' + error.message);
        }
    }

    // 处理颜色预设选择
    handleColorPresetSelect(preset) {
        // 移除其他预设的active类
        this.colorPresets.forEach(p => p.classList.remove('active'));
        
        // 添加当前预设的active类
        preset.classList.add('active');
        
        // 获取颜色值
        const color = preset.dataset.color;
        
        // 更新设置
        this.updateImageSettings({ backgroundColor: color });
    }

    // 处理自定义颜色变化
    handleCustomColorChange(color) {
        // 移除预设颜色的active状态
        this.colorPresets.forEach(p => p.classList.remove('active'));
        
        // 更新设置
        this.updateImageSettings({ backgroundColor: color });
    }

    // 更新图片设置
    updateImageSettings(newSettings = {}) {
        const settings = {
            backgroundColor: this.getCurrentBackgroundColor(),
            padding: parseInt(this.iconPadding.value),
            cornerRadius: parseInt(this.cornerRadius.value),
            quality: parseFloat(this.compressionQuality.value),
            ...newSettings
        };

        this.imageProcessor.updateSettings(settings);
    }

    // 获取当前背景颜色
    getCurrentBackgroundColor() {
        const activePreset = document.querySelector('.color-preset.active');
        if (activePreset) {
            return activePreset.dataset.color;
        }
        return this.customColor.value;
    }

    // 处理生成图标
    async handleGenerateIcons() {
        if (!this.imageProcessor.hasImage()) {
            this.showError('请先选择图片');
            return;
        }

        try {
            this.showLoading('正在生成图标...');
            
            // 获取处理后的canvas
            const processedCanvas = this.imageProcessor.processedCanvas;
            if (!processedCanvas) {
                throw new Error('图片处理失败');
            }

            // 获取当前设置
            const settings = {
                backgroundColor: this.getCurrentBackgroundColor(),
                padding: parseInt(this.iconPadding.value),
                cornerRadius: parseInt(this.cornerRadius.value),
                quality: parseFloat(this.compressionQuality.value)
            };

            // 生成所有尺寸的图标
            const icons = await this.iconGenerator.generateAllSizes(processedCanvas, settings);
            
            // 显示结果
            this.displayResults(icons);
            
            // 切换到结果步骤
            this.switchToStep(3);
            
            this.hideLoading();
            this.showSuccess(`成功生成 ${icons.length} 个图标！`);
            
        } catch (error) {
            this.hideLoading();
            this.showError('图标生成失败: ' + error.message);
        }
    }

    // 显示生成结果
    displayResults(icons) {
        this.resultGrid.innerHTML = '';
        
        icons.forEach(icon => {
            const resultItem = document.createElement('div');
            resultItem.className = 'result-item';
            
            resultItem.innerHTML = `
                <img src="${icon.dataURL}" alt="${icon.name}">
                <h4>${icon.name}</h4>
                <p>${icon.size}x${icon.size}px</p>
                <button class="btn btn-primary" onclick="app.downloadSingleIcon('${icon.name}')">
                    下载
                </button>
            `;
            
            this.resultGrid.appendChild(resultItem);
        });
    }

    // 下载单个图标
    downloadSingleIcon(iconName) {
        const icons = this.iconGenerator.getGeneratedIcons();
        const icon = icons.find(i => i.name === iconName);
        
        if (icon) {
            this.iconGenerator.downloadSingleIcon(icon);
        }
    }

    // 处理下载所有图标
    async handleDownloadAll() {
        try {
            this.showLoading('正在打包下载...');
            
            await this.iconGenerator.downloadZipPackage();
            
            this.hideLoading();
            this.showSuccess('下载完成！');
            
        } catch (error) {
            this.hideLoading();
            this.showError('下载失败: ' + error.message);
        }
    }

    // 切换步骤
    switchToStep(step) {
        this.currentStep = step;
        this.updateStepIndicator();
        
        // 隐藏所有区域
        this.uploadSection.style.display = 'none';
        this.editSection.style.display = 'none';
        this.resultSection.style.display = 'none';
        
        // 显示对应区域
        switch (step) {
            case 1:
                this.uploadSection.style.display = 'block';
                break;
            case 2:
                this.editSection.style.display = 'block';
                break;
            case 3:
                this.resultSection.style.display = 'block';
                break;
        }
    }

    // 更新步骤指示器
    updateStepIndicator() {
        const steps = document.querySelectorAll('.step');
        steps.forEach((step, index) => {
            if (index + 1 <= this.currentStep) {
                step.classList.add('active');
            } else {
                step.classList.remove('active');
            }
        });
    }

    // 显示加载状态
    showLoading(message) {
        this.showNotification(message, 'info');
    }

    // 显示处理状态
    showProcessing(message) {
        this.processingStatus.style.display = 'flex';
        this.processingStatus.innerHTML = `
            <span class="spinner"></span>
            ${message}
        `;
    }

    // 隐藏处理状态
    hideProcessing() {
        this.processingStatus.style.display = 'none';
    }

    // 隐藏加载状态
    hideLoading() {
        // 可以添加全局加载状态的隐藏逻辑
    }

    // 显示成功消息
    showSuccess(message) {
        this.showNotification(message, 'success');
    }

    // 显示错误消息
    showError(message) {
        this.showNotification(message, 'error');
    }

    // 显示通知
    showNotification(message, type = 'info') {
        // 创建通知元素
        const notification = document.createElement('div');
        notification.className = `notification notification-${type}`;
        notification.textContent = message;
        
        // 添加样式
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 15px 20px;
            border-radius: 8px;
            color: white;
            font-weight: 600;
            z-index: 1000;
            animation: slideIn 0.3s ease;
        `;
        
        // 设置背景颜色
        switch (type) {
            case 'success':
                notification.style.background = '#4CAF50';
                break;
            case 'error':
                notification.style.background = '#f44336';
                break;
            default:
                notification.style.background = '#2196F3';
        }
        
        // 添加到页面
        document.body.appendChild(notification);
        
        // 3秒后自动移除
        setTimeout(() => {
            notification.style.animation = 'slideOut 0.3s ease';
            setTimeout(() => {
                if (notification.parentNode) {
                    notification.parentNode.removeChild(notification);
                }
            }, 300);
        }, 3000);
    }

    // 重置应用状态
    reset() {
        this.imageProcessor.reset();
        this.iconGenerator.clearGeneratedIcons();
        this.switchToStep(1);
        
        // 重置控件
        this.fileInput.value = '';
        this.iconPadding.value = 10;
        this.cornerRadius.value = 0;
        this.compressionQuality.value = 0.9;
        
        // 重置显示值
        this.paddingValue.textContent = '10%';
        this.radiusValue.textContent = '0%';
        this.qualityValue.textContent = '90%';
        
        // 重置颜色选择
        this.colorPresets.forEach(p => p.classList.remove('active'));
        this.colorPresets[0].classList.add('active'); // 默认选择透明
        
        this.showSuccess('已重置所有设置');
    }
}

// 添加动画样式
const style = document.createElement('style');
style.textContent = `
    @keyframes slideIn {
        from {
            transform: translateX(100%);
            opacity: 0;
        }
        to {
            transform: translateX(0);
            opacity: 1;
        }
    }
    
    @keyframes slideOut {
        from {
            transform: translateX(0);
            opacity: 1;
        }
        to {
            transform: translateX(100%);
            opacity: 0;
        }
    }
`;
document.head.appendChild(style);

// 初始化应用
let app;
document.addEventListener('DOMContentLoaded', () => {
    app = new AppIconGenerator();
    
    // 添加键盘快捷键
    document.addEventListener('keydown', (e) => {
        if (e.ctrlKey && e.key === 'r') {
            e.preventDefault();
            app.reset();
        }
    });
    
    console.log('🎨 App图标生成工具已启动');
    console.log('快捷键: Ctrl+R 重置');
}); 