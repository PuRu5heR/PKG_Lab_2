# Лабораторная работа №1 `Вариант 4`
[![Java](https://img.shields.io/badge/Java-17-red.svg)](https://docs.oracle.com/en/java/javase/17/)
[![Spring Boot](https://img.shields.io/badge/Spring-Boot-4.svg)](https://docs.spring.io/spring-boot/api/java/index.html)
[![HTML](https://img.shields.io/badge/HTML-5-orange.svg)](https://developer.mozilla.org/ru/docs/Web/HTML)
[![CSS](https://img.shields.io/badge/CSS-3-blue.svg)](https://developer.mozilla.org/ru/docs/Web/CSS)
[![JavaScript](https://img.shields.io/badge/JavaScript-ES6-yellow.svg)](https://developer.mozilla.org/ru/docs/Web/JavaScript)

Веб-приложение для анализа метаданных графических файлов. Поддерживает форматы: JPG, PNG, GIF, BMP, TIFF, PCX.

## Быстрый запуск

### Способ 1: Через Gradle (рекомендуется)
```bash
./gradlew bootRun
```

### Способ 2: Сборка JAR и запуск
```bash
./gradlew clean build
java -jar build/libs/pkg_lab_2-0.0.1-SNAPSHOT.jar
```

### Способ 3: С дополнительной памятью
```bash
java -Xmx2g -jar build/libs/pkg_lab_2-0.0.1-SNAPSHOT.jar
```

Gradle: 7.x или выше

## Зависимости

### Основные
- **Spring Boot 4.x** - веб-фреймворк
- **Java 17** - среда выполнения

### Библиотеки анализа изображений
- **metadata-extractor** - EXIF и метаданные
- **TwelveMonkeys ImageIO** - поддержка форматов:
    - `imageio-jpeg` - JPEG анализ
    - `imageio-tiff` - TIFF анализ
    - `imageio-bmp` - BMP анализ

### Вспомогательные
- **Lombok** - генерация кода
- **Commons IO** - утилиты ввода-вывода

## Использование

1. Запустите приложение
2. Откройте в браузере: `http://localhost:8080`
3. Перетащите файлы в область загрузки
4. Нажмите "Анализировать файлы"

## ⚙Конфигурация

Порт по умолчанию: `8080`  
Максимальный размер файла: `500MB`

Изменить порт:
```bash
java -jar app.jar --server.port=9090
```

## Структура проекта

```
src/
├── main/java/by/pkg/pkg_lab_2/
│   ├── controller/    # REST API
│   ├── service/       # Логика анализа
│   ├── model/         # Модели данных
│   └── PkgLab2Application.java
└── resources/static/  # Веб-интерфейс
```