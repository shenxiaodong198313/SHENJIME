const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs').promises;
const IconGenerator = require('./src/utils/iconGenerator');

class AndroidIconGeneratorApp {
    constructor() {
        this.mainWindow = null;
        this.iconGenerator = new IconGenerator();
        this.setupEventHandlers();
    }

    setupEventHandlers() {
        app.whenReady().then(() => this.createWindow());
        
        app.on('window-all-closed', () => {
            if (process.platform !== 'darwin') {
                app.quit();
            }
        });

        app.on('activate', () => {
            if (BrowserWindow.getAllWindows().length === 0) {
                this.createWindow();
            }
        });

        // IPC事件处理
        ipcMain.handle('select-image', this.handleSelectImage.bind(this));
        ipcMain.handle('generate-icons', this.handleGenerateIcons.bind(this));
        ipcMain.handle('select-output-folder', this.handleSelectOutputFolder.bind(this));
    }

    createWindow() {
        this.mainWindow = new BrowserWindow({
            width: 800,
            height: 600,
            minWidth: 600,
            minHeight: 500,
            webPreferences: {
                nodeIntegration: true,
                contextIsolation: false,
                enableRemoteModule: true
            },
            icon: path.join(__dirname, 'assets', 'icon.png'),
            title: 'Android图标生成器',
            show: false,
            backgroundColor: '#f5f5f5'
        });

        this.mainWindow.loadFile(path.join(__dirname, 'src', 'renderer', 'index.html'));

        // 窗口准备好后显示
        this.mainWindow.once('ready-to-show', () => {
            this.mainWindow.show();
        });

        // 开发模式下打开开发者工具
        if (process.argv.includes('--dev')) {
            this.mainWindow.webContents.openDevTools();
        }
    }

    async handleSelectImage() {
        try {
            const result = await dialog.showOpenDialog(this.mainWindow, {
                title: '选择原始图片',
                filters: [
                    { name: '图片文件', extensions: ['png', 'jpg', 'jpeg', 'bmp', 'gif', 'webp'] },
                    { name: '所有文件', extensions: ['*'] }
                ],
                properties: ['openFile']
            });

            if (!result.canceled && result.filePaths.length > 0) {
                return { success: true, filePath: result.filePaths[0] };
            }
            return { success: false };
        } catch (error) {
            console.error('选择图片时出错:', error);
            return { success: false, error: error.message };
        }
    }

    async handleSelectOutputFolder() {
        try {
            const result = await dialog.showOpenDialog(this.mainWindow, {
                title: '选择输出目录',
                properties: ['openDirectory', 'createDirectory']
            });

            if (!result.canceled && result.filePaths.length > 0) {
                return { success: true, folderPath: result.filePaths[0] };
            }
            return { success: false };
        } catch (error) {
            console.error('选择输出目录时出错:', error);
            return { success: false, error: error.message };
        }
    }

    async handleGenerateIcons(event, { imagePath, outputPath, options = {} }) {
        try {
            const result = await this.iconGenerator.generateAndroidIcons(imagePath, outputPath, options);
            return result;
        } catch (error) {
            console.error('生成图标时出错:', error);
            return { success: false, error: error.message };
        }
    }
}

// 启动应用
new AndroidIconGeneratorApp(); 