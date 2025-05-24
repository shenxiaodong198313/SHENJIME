const { ipcRenderer, shell } = require('electron');
const path = require('path');

class AndroidIconGeneratorUI {
    constructor() {
        this.selectedImagePath = null;
        this.selectedOutputPath = null;
        this.isGenerating = false;
        
        this.initializeElements();
        this.setupEventListeners();
        this.setupDragAndDrop();
        this.setDefaultOutputPath();
    }

    initializeElements() {
        // 获取DOM元素
        this.uploadArea = document.getElementById('uploadArea');
        this.selectImageBtn = document.getElementById('selectImageBtn');
        this.changeImageBtn = document.getElementById('changeImageBtn');
        this.imagePreview = document.getElementById('imagePreview');
        this.previewImage = document.getElementById('previewImage');
        this.imageInfo = document.getElementById('imageInfo');
        
        this.iconNameInput = document.getElementById('iconName');
        this.outputPathInput = document.getElementById('outputPath');
        this.selectOutputBtn = document.getElementById('selectOutputBtn');
        this.enableCompressionCheckbox = document.getElementById('enableCompression');
        this.qualitySlider = document.getElementById('quality');
        this.qualityValue = document.getElementById('qualityValue');
        this.generateNotificationIconsCheckbox = document.getElementById('generateNotificationIcons');
        
        this.generateBtn = document.getElementById('generateBtn');
        this.progressSection = document.getElementById('progressSection');
        this.progressFill = document.getElementById('progressFill');
        this.progressText = document.getElementById('progressText');
        
        this.resultSection = document.getElementById('resultSection');
        this.resultTitle = document.getElementById('resultTitle');
        this.resultContent = document.getElementById('resultContent');
        this.openOutputBtn = document.getElementById('openOutputBtn');
        this.generateAgainBtn = document.getElementById('generateAgainBtn');
    }

    setupEventListeners() {
        // 图片选择事件
        this.selectImageBtn.addEventListener('click', () => this.selectImage());
        this.changeImageBtn.addEventListener('click', () => this.selectImage());
        
        // 输出目录选择事件
        this.selectOutputBtn.addEventListener('click', () => this.selectOutputFolder());
        
        // 质量滑块事件
        this.qualitySlider.addEventListener('input', (e) => {
            this.qualityValue.textContent = `${e.target.value}%`;
        });
        
        // 生成按钮事件
        this.generateBtn.addEventListener('click', () => this.generateIcons());
        
        // 结果操作事件
        this.openOutputBtn.addEventListener('click', () => this.openOutputFolder());
        this.generateAgainBtn.addEventListener('click', () => this.resetToInitialState());
        
        // 输入验证事件
        this.iconNameInput.addEventListener('input', () => this.validateInputs());
        this.outputPathInput.addEventListener('change', () => this.validateInputs());
    }

    setupDragAndDrop() {
        // 阻止默认拖拽行为
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            this.uploadArea.addEventListener(eventName, this.preventDefaults, false);
            document.body.addEventListener(eventName, this.preventDefaults, false);
        });

        // 拖拽视觉反馈
        ['dragenter', 'dragover'].forEach(eventName => {
            this.uploadArea.addEventListener(eventName, () => {
                this.uploadArea.classList.add('dragover');
            }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            this.uploadArea.addEventListener(eventName, () => {
                this.uploadArea.classList.remove('dragover');
            }, false);
        });

        // 处理文件拖放
        this.uploadArea.addEventListener('drop', (e) => {
            const files = e.dataTransfer.files;
            if (files.length > 0) {
                this.handleImageFile(files[0]);
            }
        }, false);
    }

    preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }

    async selectImage() {
        try {
            const result = await ipcRenderer.invoke('select-image');
            if (result.success) {
                this.handleImagePath(result.filePath);
            }
        } catch (error) {
            this.showError('选择图片时出错', error.message);
        }
    }

    async selectOutputFolder() {
        try {
            const result = await ipcRenderer.invoke('select-output-folder');
            if (result.success) {
                this.selectedOutputPath = result.folderPath;
                this.outputPathInput.value = result.folderPath;
                this.validateInputs();
            }
        } catch (error) {
            this.showError('选择输出目录时出错', error.message);
        }
    }

    handleImageFile(file) {
        if (this.isValidImageFile(file)) {
            this.handleImagePath(file.path);
        } else {
            this.showError('文件格式错误', '请选择有效的图片文件 (PNG, JPG, JPEG, BMP, GIF, WebP)');
        }
    }

    isValidImageFile(file) {
        const validTypes = ['image/png', 'image/jpeg', 'image/jpg', 'image/bmp', 'image/gif', 'image/webp'];
        return validTypes.includes(file.type);
    }

    async handleImagePath(imagePath) {
        try {
            this.selectedImagePath = imagePath;
            
            // 显示图片预览
            this.previewImage.src = `file://${imagePath}`;
            this.previewImage.onload = () => {
                const img = this.previewImage;
                const fileName = path.basename(imagePath);
                const fileSize = this.formatFileSize(this.getFileSize(imagePath));
                
                this.imageInfo.innerHTML = `
                    <strong>文件名:</strong> ${fileName}<br>
                    <strong>尺寸:</strong> ${img.naturalWidth} × ${img.naturalHeight} 像素<br>
                    <strong>文件大小:</strong> ${fileSize}
                `;
                
                // 显示预览区域，隐藏上传区域
                this.uploadArea.style.display = 'none';
                this.imagePreview.style.display = 'block';
                this.imagePreview.classList.add('fade-in');
                
                this.validateInputs();
            };
        } catch (error) {
            this.showError('加载图片时出错', error.message);
        }
    }

    getFileSize(filePath) {
        try {
            const fs = require('fs');
            const stats = fs.statSync(filePath);
            return stats.size;
        } catch {
            return 0;
        }
    }

    formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    setDefaultOutputPath() {
        const defaultPath = path.join(__dirname, '../../output');
        this.selectedOutputPath = defaultPath;
        this.outputPathInput.value = defaultPath;
    }

    validateInputs() {
        const hasImage = !!this.selectedImagePath;
        const hasOutput = !!this.selectedOutputPath;
        const hasIconName = this.iconNameInput.value.trim().length > 0;
        
        this.generateBtn.disabled = !(hasImage && hasOutput && hasIconName) || this.isGenerating;
    }

    async generateIcons() {
        if (this.isGenerating) return;
        
        try {
            this.isGenerating = true;
            this.generateBtn.disabled = true;
            
            // 显示进度条
            this.showProgress();
            
            // 收集配置选项
            const options = {
                iconName: this.iconNameInput.value.trim(),
                enableCompression: this.enableCompressionCheckbox.checked,
                quality: parseInt(this.qualitySlider.value),
                generateNotificationIcons: this.generateNotificationIconsCheckbox.checked,
                outputFormat: 'png'
            };
            
            // 更新进度
            this.updateProgress(20, '准备生成图标...');
            
            // 调用主进程生成图标
            const result = await ipcRenderer.invoke('generate-icons', {
                imagePath: this.selectedImagePath,
                outputPath: this.selectedOutputPath,
                options: options
            });
            
            this.updateProgress(100, '生成完成!');
            
            // 延迟显示结果
            setTimeout(() => {
                this.hideProgress();
                this.showResult(result);
            }, 1000);
            
        } catch (error) {
            this.hideProgress();
            this.showError('生成图标时出错', error.message);
        } finally {
            this.isGenerating = false;
            this.validateInputs();
        }
    }

    showProgress() {
        this.progressSection.style.display = 'block';
        this.progressSection.classList.add('fade-in');
        this.resultSection.style.display = 'none';
    }

    updateProgress(percentage, text) {
        this.progressFill.style.width = `${percentage}%`;
        this.progressText.textContent = text;
    }

    hideProgress() {
        this.progressSection.style.display = 'none';
    }

    showResult(result) {
        if (result.success) {
            this.resultTitle.innerHTML = '<i class="fas fa-check-circle" style="color: #27ae60;"></i> 图标生成成功!';
            this.resultTitle.style.color = '#27ae60';
            
            const stats = result.summary;
            const files = result.generatedFiles;
            
            this.resultContent.innerHTML = `
                <div class="result-stats">
                    <div class="stat-item">
                        <div class="stat-number">${stats.totalFiles}</div>
                        <div class="stat-label">总文件数</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-number">${stats.successCount}</div>
                        <div class="stat-label">成功生成</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-number">${stats.errorCount}</div>
                        <div class="stat-label">失败数量</div>
                    </div>
                </div>
                
                <h5>生成的文件列表:</h5>
                <div class="file-list">
                    ${files.map(file => `
                        <div class="file-item">
                            <span class="file-name">${file.folder}/${file.fileName}</span>
                            <span class="file-size">${file.size}</span>
                        </div>
                    `).join('')}
                </div>
                
                ${result.errors.length > 0 ? `
                    <h5 style="color: #e74c3c; margin-top: 1rem;">错误信息:</h5>
                    <ul style="color: #e74c3c;">
                        ${result.errors.map(error => `<li>${error}</li>`).join('')}
                    </ul>
                ` : ''}
            `;
        } else {
            this.resultTitle.innerHTML = '<i class="fas fa-exclamation-circle" style="color: #e74c3c;"></i> 生成失败';
            this.resultTitle.style.color = '#e74c3c';
            this.resultContent.innerHTML = `
                <p style="color: #e74c3c;">错误信息: ${result.error}</p>
            `;
        }
        
        this.resultSection.style.display = 'block';
        this.resultSection.classList.add('bounce-in');
    }

    async openOutputFolder() {
        if (this.selectedOutputPath) {
            await shell.openPath(this.selectedOutputPath);
        }
    }

    resetToInitialState() {
        // 重置状态
        this.selectedImagePath = null;
        this.isGenerating = false;
        
        // 重置UI
        this.uploadArea.style.display = 'block';
        this.imagePreview.style.display = 'none';
        this.progressSection.style.display = 'none';
        this.resultSection.style.display = 'none';
        
        // 重置表单
        this.iconNameInput.value = 'ic_launcher';
        this.enableCompressionCheckbox.checked = true;
        this.qualitySlider.value = 90;
        this.qualityValue.textContent = '90%';
        this.generateNotificationIconsCheckbox.checked = true;
        
        this.validateInputs();
    }

    showError(title, message) {
        // 简单的错误提示，可以后续改为更美观的模态框
        alert(`${title}\n\n${message}`);
    }
}

// 当DOM加载完成后初始化应用
document.addEventListener('DOMContentLoaded', () => {
    new AndroidIconGeneratorUI();
}); 