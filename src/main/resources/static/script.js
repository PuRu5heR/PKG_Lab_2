class ImageAnalyzer {
    constructor() {
        this.files = [];
        this.results = [];
        this.zipThreshold = 10;
        this.initializeEventListeners();
    }

    initializeEventListeners() {
        const uploadArea = document.getElementById('uploadArea');
        const fileInput = document.getElementById('fileInput');

        uploadArea.addEventListener('click', () => fileInput.click());

        uploadArea.addEventListener('dragover', (e) => {
            e.preventDefault();
            uploadArea.classList.add('dragover');
        });

        uploadArea.addEventListener('dragleave', () => {
            uploadArea.classList.remove('dragover');
        });

        uploadArea.addEventListener('drop', (e) => {
            e.preventDefault();
            uploadArea.classList.remove('dragover');
            this.handleNewFiles(e.dataTransfer.files);
        });

        fileInput.addEventListener('change', (e) => {
            this.handleNewFiles(e.target.files);
            e.target.value = '';
        });
    }

    handleNewFiles(newFiles) {
        if (!newFiles || newFiles.length === 0) return;

        const fileArray = Array.from(newFiles);
        let addedCount = 0;

        fileArray.forEach(file => {
            const isDuplicate = this.files.some(existingFile =>
                existingFile.name === file.name &&
                existingFile.size === file.size
            );

            if (!isDuplicate) {
                this.files.push(file);
                addedCount++;
            }
        });

        if (addedCount > 0) {
            this.updateFileList();
            this.showNotification(`Добавлено ${addedCount} файлов. Всего в очереди: ${this.files.length}`);

            if (this.files.length >= this.zipThreshold) {
                this.showNotification(`Будет создан ZIP архив (${this.files.length} файлов)`, 'info');
            }
        } else {
            this.showNotification('Файлы уже добавлены');
        }
    }

    updateFileList() {
        const stats = document.getElementById('stats');
        if (this.files.length > 0) {
            let content = `
                <div class="stat-item">
                    <div class="stat-value">${this.files.length}</div>
                    <div class="stat-label">Файлов в очереди</div>
                </div>
                <div class="stat-item">
                    <div class="stat-value">${this.formatTotalSize()}</div>
                    <div class="stat-label">Общий размер</div>
                </div>
            `;

            if (this.files.length >= this.zipThreshold) {
                content += `
                    <div class="stat-item">
                        <div class="stat-value">ZIP</div>
                        <div class="stat-label">Автоматическая упаковка</div>
                    </div>
                `;
            }

            content += `
                <div class="stat-item">
                    <button class="btn-clear" onclick="clearQueue()">Очистить очередь</button>
                </div>
            `;

            stats.innerHTML = content;
        } else {
            stats.innerHTML = '<div class="no-files">Нет загруженных файлов</div>';
        }
    }

    formatTotalSize() {
        const totalBytes = this.files.reduce((sum, file) => sum + file.size, 0);
        return this.formatFileSize(totalBytes);
    }

    formatFileSize(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    async analyzeFiles() {
        if (this.files.length === 0) {
            this.showNotification('Пожалуйста, добавьте файлы для анализа', 'error');
            return;
        }

        this.showProgress(true);

        try {
            if (this.files.length >= this.zipThreshold) {
                await this.processWithZip();
            } else {
                await this.processRegularFiles();
            }

            this.files = [];
            this.updateFileList();

        } catch (error) {
            console.error('Error:', error);
            this.handleAnalysisError(error);
        } finally {
            this.showProgress(false);
        }
    }

    async processRegularFiles() {
        const formData = new FormData();

        this.files.forEach(file => {
            formData.append('files', file);
        });

        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 300000);

        try {
            const response = await fetch('/api/images/analyze', {
                method: 'POST',
                body: formData,
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Ошибка сервера: ${response.status} - ${errorText}`);
            }

            const newResults = await response.json();

            for (let i = 0; i < newResults.length; i++) {
                this.results.push(newResults[i]);
            }

            this.displayResults();
            this.showNotification(`Успешно проанализировано ${newResults.length} файлов`);

        } finally {
            clearTimeout(timeoutId);
        }
    }

    async processWithZip() {
        this.showNotification('Создание ZIP архива...', 'info');

        const zip = new JSZip();

        this.files.forEach(file => {
            zip.file(file.name, file);
        });

        const zipBlob = await zip.generateAsync({
            type: 'blob',
            compression: 'DEFLATE',
            compressionOptions: { level: 6 }
        });

        this.showNotification('ZIP архив создан, отправка на сервер...', 'info');

        const formData = new FormData();
        const zipFile = new File([zipBlob], `images_${Date.now()}.zip`, { type: 'application/zip' });
        formData.append('zipFile', zipFile);

        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 600000); // 10 минут для ZIP

        try {
            const response = await fetch('/api/images/analyze-zip', {
                method: 'POST',
                body: formData,
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Ошибка сервера: ${response.status} - ${errorText}`);
            }

            const newResults = await response.json();

            for (let i = 0; i < newResults.length; i++) {
                this.results.push(newResults[i]);
            }

            this.displayResults();
            this.showNotification(`Успешно проанализировано ${newResults.length} файлов из ZIP архива`);

        } finally {
            clearTimeout(timeoutId);
        }
    }

    handleAnalysisError(error) {
        if (error.name === 'AbortError') {
            this.showNotification('Время обработки превышено. Попробуйте меньший объем файлов.', 'error');
        } else if (error.message.includes('FileCountLimitExceededException')) {
            this.showNotification('Превышено максимальное количество файлов. Файлы автоматически упакованы в ZIP.', 'error');
        } else if (error.message.includes('413')) {
            this.showNotification('Слишком большой объем данных. Попробуйте разделить файлы на несколько партий.', 'error');
        } else {
            this.showNotification('Произошла ошибка при анализе файлов: ' + error.message, 'error');
        }
    }

    showProgress(show) {
        const progress = document.getElementById('progress');
        const progressFill = document.getElementById('progressFill');
        const progressText = document.getElementById('progressText');

        if (show) {
            progress.style.display = 'block';
            progressFill.style.width = '0%';
            progressText.textContent = 'Обработка...';

            let width = 0;
            const interval = setInterval(() => {
                if (width >= 90) {
                    clearInterval(interval);
                } else {
                    width += 2;
                    progressFill.style.width = width + '%';
                }
            }, 50);

            this.progressInterval = interval;
        } else {
            progress.style.display = 'none';
            if (this.progressInterval) {
                clearInterval(this.progressInterval);
            }
        }
    }

    displayResults() {
        const tbody = document.getElementById('resultsBody');
        tbody.innerHTML = '';

        this.results.forEach((result, index) => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${result.filename}</td>
                <td>${result.dimensions || 'N/A'}</td>
                <td>${result.resolution || 'N/A'}</td>
                <td>${result.colorDepth || 'N/A'}</td>
                <td>${result.compression || 'N/A'}</td>
                <td>${result.fileSize || 'N/A'}</td>
                <td>
                    <button class="details-btn" onclick="showDetails(${index})">
                        Подробнее
                    </button>
                </td>
                <td>
                    <button class="remove-btn" onclick="removeResult(${index})" title="Удалить из результатов">
                        ×
                    </button>
                </td>
            `;
            tbody.appendChild(row);
        });
    }

    showNotification(message, type = 'success') {
        let notificationContainer = document.getElementById('notificationContainer');
        if (!notificationContainer) {
            notificationContainer = document.createElement('div');
            notificationContainer.id = 'notificationContainer';
            notificationContainer.style.cssText = `
                position: fixed;
                top: 20px;
                right: 20px;
                z-index: 1000;
            `;
            document.body.appendChild(notificationContainer);
        }

        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        notification.textContent = message;
        notification.style.cssText = `
            background: ${this.getNotificationColor(type)};
            color: white;
            padding: 12px 20px;
            margin-bottom: 10px;
            border-radius: 5px;
            box-shadow: 0 3px 10px rgba(0,0,0,0.2);
            animation: slideIn 0.3s ease;
        `;

        notificationContainer.appendChild(notification);

        setTimeout(() => {
            notification.style.animation = 'slideOut 0.3s ease';
            setTimeout(() => {
                if (notification.parentNode) {
                    notification.parentNode.removeChild(notification);
                }
            }, 300);
        }, 5000);
    }

    getNotificationColor(type) {
        switch (type) {
            case 'error': return '#e74c3c';
            case 'warning': return '#f39c12';
            case 'info': return '#3498db';
            case 'success':
            default: return '#2ecc71';
        }
    }
}

const analyzer = new ImageAnalyzer();

function analyzeFiles() {
    analyzer.analyzeFiles();
}

function clearQueue() {
    analyzer.files = [];
    analyzer.updateFileList();
    analyzer.showNotification('Очередь очищена');
}

function clearAllResults() {
    analyzer.results = [];
    analyzer.displayResults();
    analyzer.showNotification('Все результаты очищены');
}

function removeResult(index) {
    analyzer.results.splice(index, 1);
    analyzer.displayResults();
    analyzer.showNotification('Результат удален');
}

function showDetails(index) {
    const result = analyzer.results[index];
    let detailsText = `Файл: ${result.filename}\n`;
    detailsText += `Размер: ${result.dimensions || 'N/A'}\n`;
    detailsText += `Разрешение: ${result.resolution || 'N/A'}\n`;
    detailsText += `Глубина цвета: ${result.colorDepth || 'N/A'}\n`;
    detailsText += `Сжатие: ${result.compression || 'N/A'}\n`;
    detailsText += `Размер файла: ${result.fileSize || 'N/A'}\n`;
    detailsText += `Формат: ${result.format || 'N/A'}\n`;

    if (result.additionalInfo) {
        detailsText += '\nДополнительная информация:\n';
        Object.entries(result.additionalInfo).forEach(([key, value]) => {
            detailsText += `${key}: ${value}\n`;
        });
    }

    const modal = document.createElement('div');
    modal.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0,0,0,0.5);
        display: flex;
        justify-content: center;
        align-items: center;
        z-index: 2000;
    `;

    modal.innerHTML = `
        <div style="background: white; padding: 20px; border-radius: 10px; max-width: 500px; max-height: 80vh; overflow-y: auto;">
            <h3>Детальная информация</h3>
            <pre style="white-space: pre-wrap; font-family: inherit;">${detailsText}</pre>
            <button onclick="this.parentElement.parentElement.remove()" 
                    style="margin-top: 15px; padding: 8px 16px; background: #3498db; color: white; border: none; border-radius: 5px; cursor: pointer;">
                Закрыть
            </button>
        </div>
    `;

    document.body.appendChild(modal);

    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.remove();
        }
    });
}

// Добавляем CSS анимации
const style = document.createElement('style');
style.textContent = `
    @keyframes slideIn {
        from { transform: translateX(100%); opacity: 0; }
        to { transform: translateX(0); opacity: 1; }
    }
    
    @keyframes slideOut {
        from { transform: translateX(0); opacity: 1; }
        to { transform: translateX(100%); opacity: 0; }
    }
    
    .btn-clear {
        background: #e74c3c;
        color: white;
        border: none;
        padding: 8px 16px;
        border-radius: 15px;
        cursor: pointer;
        font-size: 12px;
        transition: all 0.3s ease;
    }
    
    .btn-clear:hover {
        background: #c0392b;
        transform: translateY(-1px);
    }
    
    .remove-btn {
        background: #e74c3c;
        color: white;
        border: none;
        width: 25px;
        height: 25px;
        border-radius: 50%;
        cursor: pointer;
        font-size: 14px;
        margin-left: 5px;
        transition: all 0.3s ease;
    }
    
    .remove-btn:hover {
        background: #c0392b;
        transform: scale(1.1);
    }
    
    .no-files {
        text-align: center;
        color: #7f8c8d;
        font-style: italic;
        padding: 20px;
    }
    
    .zip-info {
        color: #3498db;
        font-weight: 500;
        margin-top: 10px;
    }
`;
document.head.appendChild(style);