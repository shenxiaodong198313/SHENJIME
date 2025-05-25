// 简化的去背景功能实现
class BackgroundRemover {
    static async removeBackground(imageData) {
        return new Promise((resolve) => {
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            
            canvas.width = imageData.width;
            canvas.height = imageData.height;
            
            ctx.putImageData(imageData, 0, 0);
            
            // 获取图像数据
            const data = ctx.getImageData(0, 0, canvas.width, canvas.height);
            const pixels = data.data;
            
            // 简单的背景去除算法：基于边缘检测和颜色相似度
            const threshold = 30; // 颜色相似度阈值
            
            // 获取四个角落的颜色作为背景色参考
            const corners = [
                [0, 0], // 左上
                [canvas.width - 1, 0], // 右上
                [0, canvas.height - 1], // 左下
                [canvas.width - 1, canvas.height - 1] // 右下
            ];
            
            const backgroundColors = corners.map(([x, y]) => {
                const index = (y * canvas.width + x) * 4;
                return [pixels[index], pixels[index + 1], pixels[index + 2]];
            });
            
            // 处理每个像素
            for (let i = 0; i < pixels.length; i += 4) {
                const r = pixels[i];
                const g = pixels[i + 1];
                const b = pixels[i + 2];
                
                // 检查是否与背景色相似
                let isBackground = false;
                for (const bgColor of backgroundColors) {
                    const colorDiff = Math.sqrt(
                        Math.pow(r - bgColor[0], 2) +
                        Math.pow(g - bgColor[1], 2) +
                        Math.pow(b - bgColor[2], 2)
                    );
                    
                    if (colorDiff < threshold) {
                        isBackground = true;
                        break;
                    }
                }
                
                // 如果是背景色，设置为透明
                if (isBackground) {
                    pixels[i + 3] = 0; // 设置alpha为0（透明）
                }
            }
            
            ctx.putImageData(data, 0, 0);
            
            // 返回处理后的canvas
            resolve(canvas);
        });
    }
    
    // 更高级的去背景算法（基于边缘检测）
    static async removeBackgroundAdvanced(imageData) {
        return new Promise((resolve) => {
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            
            canvas.width = imageData.width;
            canvas.height = imageData.height;
            
            ctx.putImageData(imageData, 0, 0);
            
            // 应用高斯模糊减少噪声
            ctx.filter = 'blur(1px)';
            ctx.drawImage(canvas, 0, 0);
            ctx.filter = 'none';
            
            const data = ctx.getImageData(0, 0, canvas.width, canvas.height);
            const pixels = data.data;
            
            // 边缘检测算法
            const sobelX = [
                [-1, 0, 1],
                [-2, 0, 2],
                [-1, 0, 1]
            ];
            
            const sobelY = [
                [-1, -2, -1],
                [0, 0, 0],
                [1, 2, 1]
            ];
            
            const edges = new Uint8Array(canvas.width * canvas.height);
            
            // 计算边缘
            for (let y = 1; y < canvas.height - 1; y++) {
                for (let x = 1; x < canvas.width - 1; x++) {
                    let gx = 0, gy = 0;
                    
                    for (let ky = -1; ky <= 1; ky++) {
                        for (let kx = -1; kx <= 1; kx++) {
                            const idx = ((y + ky) * canvas.width + (x + kx)) * 4;
                            const gray = (pixels[idx] + pixels[idx + 1] + pixels[idx + 2]) / 3;
                            
                            gx += gray * sobelX[ky + 1][kx + 1];
                            gy += gray * sobelY[ky + 1][kx + 1];
                        }
                    }
                    
                    const magnitude = Math.sqrt(gx * gx + gy * gy);
                    edges[y * canvas.width + x] = magnitude > 50 ? 255 : 0;
                }
            }
            
            // 基于边缘信息调整透明度
            for (let i = 0; i < pixels.length; i += 4) {
                const pixelIndex = Math.floor(i / 4);
                const x = pixelIndex % canvas.width;
                const y = Math.floor(pixelIndex / canvas.width);
                
                // 如果在边缘附近，保持不透明
                if (edges[pixelIndex] > 0) {
                    continue;
                }
                
                // 检查周围是否有边缘
                let hasNearbyEdge = false;
                const radius = 3;
                for (let dy = -radius; dy <= radius; dy++) {
                    for (let dx = -radius; dx <= radius; dx++) {
                        const nx = x + dx;
                        const ny = y + dy;
                        if (nx >= 0 && nx < canvas.width && ny >= 0 && ny < canvas.height) {
                            if (edges[ny * canvas.width + nx] > 0) {
                                hasNearbyEdge = true;
                                break;
                            }
                        }
                    }
                    if (hasNearbyEdge) break;
                }
                
                // 如果没有附近的边缘，可能是背景
                if (!hasNearbyEdge) {
                    const r = pixels[i];
                    const g = pixels[i + 1];
                    const b = pixels[i + 2];
                    
                    // 检查是否为单调颜色（可能是背景）
                    const variance = Math.abs(r - g) + Math.abs(g - b) + Math.abs(b - r);
                    if (variance < 30) {
                        pixels[i + 3] = Math.max(0, pixels[i + 3] - 100);
                    }
                }
            }
            
            ctx.putImageData(data, 0, 0);
            resolve(canvas);
        });
    }
}

// 全局导出
window.BackgroundRemover = BackgroundRemover; 