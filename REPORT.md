# Отчет по лабораторной работе №2 "Чтение информации из графических файлов"

## Цель работы

Закрепить теоретический материал и практическое освоение основных возможностей по:
- работе с различными форматами хранения растровых изображений;
- получению информации об изображении, хранящемся в файле.

## Ход исследования

Создал веб-приложение, позволяющее пользователю анализировать следующие форматы изображений: jpg, gif, tif, bmp, png, pcx.

## Основные требования к приложению

- Отображать имя файла, размер изображения, разрешение, глубина цвета, сжатие;
- Удобная подача считываемой информации
- Поддержка большого количества данных

## Используемые технологии

[![Java](https://img.shields.io/badge/Java-17-red.svg)](https://docs.oracle.com/en/java/javase/17/)

[![Spring Boot](https://img.shields.io/badge/Spring-Boot-4.svg)](https://docs.spring.io/spring-boot/api/java/index.html)

[![HTML](https://img.shields.io/badge/HTML-5-orange.svg)](https://developer.mozilla.org/ru/docs/Web/HTML)

[![CSS](https://img.shields.io/badge/CSS-3-blue.svg)](https://developer.mozilla.org/ru/docs/Web/CSS)

[![JavaScript](https://img.shields.io/badge/JavaScript-ES6-yellow.svg)](https://developer.mozilla.org/ru/docs/Web/JavaScript)

[Metadata Extractor for Java](https://github.com/drewnoakes/metadata-extractor)

## Что получилось, что нет

### Успешно реализовано:

**Поддержка 6 форматов изображений**:
- JPEG - с анализом сегментов структуры файла
- PNG - с чтением chunk'ов и извлечением метаданных IHDR
- GIF - с анализом палитры, анимации и версий
- TIFF - с поддержкой обоих порядков байт (little/big endian)
- BMP - с определением типа сжатия
- PCX - с анализом версий и методов кодирования

**Архитектура приложения**:
- Spring Boot бэкенд с REST API
- Фронтенд на чистом JavaScript
- Специализированные сервисы анализа для каждого формата
- Поддержка пакетной обработки и ZIP-архивов

**Функциональность анализа**:
- Определение размеров изображения
- Анализ глубины цвета и цветового пространства
- Определение методов сжатия
- Извлечение разрешения (DPI)
- Анализ дополнительных метаданных (EXIF, палитра, прозрачность и т.д.)

**Пользовательский интерфейс**:
- Drag-and-drop загрузка файлов
- Интерактивная таблица результатов
- Детальные модальные окна с полной информацией
- Прогресс-бар для длительных операций
- Уведомления о статусе операций

**Оптимизация производительности**:
- Пакетная обработка файлов (batch processing)
- Автоматическая упаковка в ZIP при большом количестве файлов
- Настройки таймаутов и ограничений размера файлов

### Возникшие сложности:

**Анализ файлов**:
- Различия в реализации некоторых редких подформатов
- Нахождение разрешения изображений
- Проблемы с устаревшими версиями PCX файлов

**Производительность при больших объемах**:
- Ограничения памяти при обработке сотни файлов одновременно
- Длительное время анализа для TIFF файлов со сложной структурой

## Как тестировал

1. **Тестирование форматов**:
    - Создание тестовых файлов всех поддерживаемых форматов
    - Проверка корректности извлечения размеров и разрешения
    - Валидация определения методов сжатия

2. **Тестирование граничных случаев**:
    - Файлы с минимально допустимыми размерами
    - Изображения с нестандартными цветовыми пространствами
    - Файлы с поврежденными заголовками

3. **Производительность**:
    - Тестирование с пакетами от 1 до 200 файлов общим размером до 2 гб
    - Проверка обработки ZIP архивов
    - Мониторинг использования памяти

4. **Интерфейс**:
    - Тестирование drag-and-drop функциональности
    - Проверка отображения на различных разрешениях экрана
    - Валидация уведомлений и обработки ошибок

## Формулы и алгоритмы анализа форматов

### 1. JPEG формат

**Структура сегментов:**
```
[FF][Marker][Length_High][Length_Low][Data...]
```

**Определение длины сегмента:**
```java
private static int getSegmentLength(byte[] fileBytes, int offset) {
    return ((fileBytes[offset + 2] & 0xFF) << 8) | (fileBytes[offset + 3] & 0xFF);
}
```

**Извлечение размеров из SOF сегмента:**
```java
int height = ((fileBytes[offset + 5] & 0xFF) << 8) | (fileBytes[offset + 6] & 0xFF);
int width = ((fileBytes[offset + 7] & 0xFF) << 8) | (fileBytes[offset + 8] & 0xFF);
```

**Определение цветового пространства:**
```java
private static String getColorSpaceFromComponents(int components) {
    return switch (components) {
        case 1 -> "Grayscale";
        case 3 -> "YCbCr";
        case 4 -> "CMYK";
        default -> "Unknown";
    };
}
```

### 2. PNG формат

**Сигнатура PNG:**
```
89 50 4E 47 0D 0A 1A 0A
```

**Структура чанков:**
```
[Length:4][Type:4][Data:Length][CRC:4]
```

**Извлечение размеров из IHDR чанка:**
```java
int width = ((fileBytes[i+4] & 0xFF) << 24) |
           ((fileBytes[i+5] & 0xFF) << 16) |
           ((fileBytes[i+6] & 0xFF) << 8) |
           (fileBytes[i+7] & 0xFF);

int height = ((fileBytes[i+8] & 0xFF) << 24) |
            ((fileBytes[i+9] & 0xFF) << 16) |
            ((fileBytes[i+10] & 0xFF) << 8) |
            (fileBytes[i+11] & 0xFF);
```

**Определение типа цвета:**
```java
private static String getColorType(int colorType) {
    return switch (colorType) {
        case 0 -> "Grayscale";
        case 2 -> "Truecolor (RGB)";
        case 3 -> "Indexed";
        case 4 -> "Grayscale with alpha";
        case 6 -> "Truecolor with alpha (RGBA)";
        default -> "Неизвестно";
    };
}
```

**Проверка прозрачности:**
```java
private static boolean hasTransparency(int colorType) {
    return colorType == 4 || colorType == 6;
}
```

### 3. GIF формат

**Анализ палитры:**
```java
int packedByte = fileBytes[10] & 0xFF;
boolean hasGlobalColorTable = (packedByte & 0x80) != 0;
int colorResolution = ((packedByte & 0x70) >> 4) + 1;
boolean sortFlag = (packedByte & 0x08) != 0;
int globalColorTableSize = 2 << (packedByte & 0x07);
```

**Определение анимации:**
```java
boolean hasAnimation = false;
for (int i = 0; i < fileBytes.length - 1; i++) {
    if (fileBytes[i] == 0x21 && fileBytes[i+1] == (byte)0xF9) {
        hasAnimation = true;
        break;
    }
}
```

### 4. TIFF формат

**Определение порядка байт:**
```java
boolean isLittleEndian = (fileBytes[0] == 0x49 && fileBytes[1] == 0x49);
boolean isBigEndian = (fileBytes[0] == 0x4D && fileBytes[1] == 0x4D);
```

**Чтение значений с учетом порядка байт:**
```java
private static int readShort(byte[] data, int offset, boolean isLittleEndian) {
    if (isLittleEndian) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    } else {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }
}
```

**Анализ IFD структуры:**
```java
int entryCount = readShort(fileBytes, offset, isLittleEndian);
for (int i = 0; i < entryCount; i++) {
    int entryOffset = offset + 2 + (i * 12);
    analyzeTag(fileBytes, entryOffset, isLittleEndian, additionalInfo);
}
```

**Определение типа сжатия:**
```java
private static String getCompressionType(int value) {
    return switch (value) {
        case 1 -> "None";
        case 2 -> "CCITT RLE";
        case 3 -> "CCITT Group 3";
        case 4 -> "CCITT Group 4";
        case 5 -> "LZW";
        case 6, 7 -> "JPEG";
        case 8 -> "Deflate";
        case 9 -> "JBIG";
        case 10 -> "RLE";
        default -> "N/A";
    };
}
```

### 5. BMP формат

**Определение типа сжатия:**
```java
int compression = (fileBytes[30] & 0xFF) |
                 ((fileBytes[31] & 0xFF) << 8) |
                 ((fileBytes[32] & 0xFF) << 16) |
                 ((fileBytes[33] & 0xFF) << 24);

return switch (compression) {
    case 0 -> "без сжатия";
    case 1 -> "RLE 8 бит";
    case 2 -> "RLE 4 бита";
    case 3 -> "битовые маски";
    case 4 -> "JPEG";
    case 5 -> "PNG";
    default -> "N/A";
};
```

### 6. PCX формат

**Анализ версии и сжатия:**
```java
int version = fileBytes[2] & 0xFF;
int encoding = fileBytes[3] & 0xFF;

String versionStr = getPcxVersion(version);
String compressionStr = (encoding == 1) ? "RLE" : "без сжатия";
```

**Определение версии PCX:**
```java
private String getPcxVersion(int version) {
    return switch (version) {
        case 0 -> "2.5";
        case 2 -> "2.8 с палитрой";
        case 3 -> "2.8 без палитры";
        case 4 -> "Paintbrush для Windows";
        case 5 -> "3.0+";
        default -> "неизвестная версия: " + version;
    };
}
```

### 7. Общие алгоритмы

**Форматирование размера файла:**
```java
private String formatFileSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
    if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
    return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
}
```

**Определение глубины цвета:**
```java
private String getColorDepth(BufferedImage image) {
    int colorDepth = image.getColorModel().getPixelSize();
    String depthInfo = colorDepth + " bit";
    String colorSpace = getColorSpaceInfo(image);
    if (!colorSpace.isEmpty()) {
        depthInfo += " [" + colorSpace + "]";
    }
    return depthInfo;
}
```

**Извлечение разрешения из EXIF:**
```java
Directory exifDir = extractedMetadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
if (exifDir != null) {
    if (exifDir.containsTag(ExifIFD0Directory.TAG_X_RESOLUTION)) {
        Object xRes = exifDir.getObject(ExifIFD0Directory.TAG_X_RESOLUTION);
        Object yRes = exifDir.getObject(ExifIFD0Directory.TAG_Y_RESOLUTION);
        if (xRes != null && yRes != null) {
            imageMetadata.setResolution(xRes + "×" + yRes + " dpi");
        }
    }
}
```

## Выводы

- Закреплен теоретический материал по сжатию изображений и информации, хранящейся в файле.
- Освоена работа с различными форматами хранения растровых изображений
- Освоено получение информации об изображении, хранящейся в файле
- В ходе работы создано полноценное веб-приложение, позволяющее пользователю анализировать 