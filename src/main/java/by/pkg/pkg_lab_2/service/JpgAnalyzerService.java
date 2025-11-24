package by.pkg.pkg_lab_2.service;

import by.pkg.pkg_lab_2.model.ImageMetadata;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class JpgAnalyzerService {
    private static boolean isJpegSignature(byte[] fileBytes) {
        return fileBytes[0] == (byte)0xFF && fileBytes[1] == (byte)0xD8;
    }

    private static int getSegmentLength(byte[] fileBytes, int offset) {
        if (offset + 3 >= fileBytes.length) return 0;
        return ((fileBytes[offset + 2] & 0xFF) << 8) | (fileBytes[offset + 3] & 0xFF);
    }

    private static String getColorSpaceFromComponents(int components) {
        return switch (components) {
            case 1 -> "Grayscale";
            case 3 -> "YCbCr";
            case 4 -> "CMYK";
            default -> "Unknown";
        };
    }

    private static void analyzeAPP0(byte[] fileBytes, int offset, Map<String, String> additionalInfo) {
        try {
            int length = getSegmentLength(fileBytes, offset);
            if (length < 16) return;

            if (fileBytes[offset + 4] == 'J' && fileBytes[offset + 5] == 'F' &&
                    fileBytes[offset + 6] == 'I' && fileBytes[offset + 7] == 'F' &&
                    fileBytes[offset + 8] == 0) {

                additionalInfo.put("Формат", "JFIF");

                int majorVersion = fileBytes[offset + 9] & 0xFF;
                int minorVersion = fileBytes[offset + 10] & 0xFF;
                additionalInfo.put("Версия JFIF", majorVersion + "." + minorVersion);

                int densityUnits = fileBytes[offset + 11] & 0xFF;
                int xDensity = ((fileBytes[offset + 12] & 0xFF) << 8) | (fileBytes[offset + 13] & 0xFF);
                int yDensity = ((fileBytes[offset + 14] & 0xFF) << 8) | (fileBytes[offset + 15] & 0xFF);

                if (densityUnits == 1) {
                    additionalInfo.put("Разрешение X", xDensity + " dpi");
                    additionalInfo.put("Разрешение Y", yDensity + " dpi");
                } else if (densityUnits == 2) {
                    additionalInfo.put("Разрешение X", xDensity + " dpcm");
                    additionalInfo.put("Разрешение Y", yDensity + " dpcm");
                }
            }
        } catch (Exception e) {
            System.err.println("Error analyzing APP0: " + e.getMessage());
        }
    }

    private static void analyzeAPP1(byte[] fileBytes, int offset, Map<String, String> additionalInfo) {
        try {
            int length = getSegmentLength(fileBytes, offset);
            if (length < 8) return;

            if (fileBytes[offset + 4] == 'E' && fileBytes[offset + 5] == 'x' &&
                    fileBytes[offset + 6] == 'i' && fileBytes[offset + 7] == 'f' &&
                    fileBytes[offset + 8] == 0) {

                additionalInfo.put("Метаданные", "EXIF присутствуют");
            }
        } catch (Exception e) {
            System.err.println("Error analyzing APP1: " + e.getMessage());
        }
    }

    private static void analyzeSOF(byte[] fileBytes, int offset, Map<String, String> additionalInfo) {
        try {
            int length = getSegmentLength(fileBytes, offset);
            if (length < 8) return;

            int precision = fileBytes[offset + 4] & 0xFF;
            additionalInfo.put("Точность", precision + " бит/компонент");

            int height = ((fileBytes[offset + 5] & 0xFF) << 8) | (fileBytes[offset + 6] & 0xFF);
            int width = ((fileBytes[offset + 7] & 0xFF) << 8) | (fileBytes[offset + 8] & 0xFF);
            additionalInfo.put("Ширина", width + " px");
            additionalInfo.put("Высота", height + " px");

            int components = fileBytes[offset + 9] & 0xFF;
            additionalInfo.put("Компоненты", String.valueOf(components));

            String colorSpace = getColorSpaceFromComponents(components);
            additionalInfo.put("Цветовое пространство", colorSpace);
        } catch (Exception e) {
            System.err.println("Error analyzing SOF0: " + e.getMessage());
        }
    }

    private static void analyzeDRI(byte[] fileBytes, int offset, Map<String, String> additionalInfo) {
        try {
            int length = getSegmentLength(fileBytes, offset);
            if (length < 4) return;

            int restartInterval = ((fileBytes[offset + 4] & 0xFF) << 8) | (fileBytes[offset + 5] & 0xFF);
            additionalInfo.put("Интервал перезапуска", restartInterval + " MCU блоков");
        } catch (Exception e) {
            System.err.println("Error analyzing DRI: " + e.getMessage());
        }
    }

    private static void analyzeJpegSegments(byte[] fileBytes, Map<String, String> additionalInfo) {
        int i = 2;

        while (i < fileBytes.length - 1) {
            if (fileBytes[i] == (byte)0xFF && fileBytes[i + 1] != (byte)0x00) {
                int marker = fileBytes[i + 1] & 0xFF;

                switch (marker) {
                    case 0xE0: // APP0 (JFIF)
                        analyzeAPP0(fileBytes, i, additionalInfo);
                        break;
                    case 0xE1: // APP1 (EXIF)
                        analyzeAPP1(fileBytes, i, additionalInfo);
                        break;
                    case 0xC0: // SOF0 (Baseline DCT)
                        additionalInfo.put("Кодирование", "Baseline DCT (SOF0)");
                        analyzeSOF(fileBytes, i, additionalInfo);
                        break;
                    case 0xC2: // SOF2 (Progressive DCT)
                        additionalInfo.put("Кодирование", "Progressive DCT (SOF2)");
                        analyzeSOF(fileBytes, i, additionalInfo);
                        break;
                    case 0xDB: // DQT (Quantization Table)
                        additionalInfo.put("Таблицы квантования", "Присутствуют");
                        break;
                    case 0xC4: // DHT (Huffman Table)
                        additionalInfo.put("Таблицы Хаффмана", "Присутствуют");
                        break;
                    case 0xDD: // DRI (Restart Interval)
                        analyzeDRI(fileBytes, i, additionalInfo);
                        break;
                    case 0xDA: // SOS (Start of Scan) - начало данных изображения
                        additionalInfo.put("Сжатые данные", "Начинаются с offset " + i);
                        return;
                    case 0xD9:
                        return;
                }

                int segmentLength = getSegmentLength(fileBytes, i);
                if (segmentLength < 2) break;
                i += segmentLength;
            } else {
                i++;
            }
        }
    }

    public static void analyze(byte[] fileBytes, ImageMetadata metadata) {
        Map<String, String> additionalInfo = new HashMap<>();

        try {
            if (fileBytes.length < 4) {
                additionalInfo.put("Ошибка", "Файл слишком мал для формата JPEG");
                metadata.setAdditionalInfo(additionalInfo);
                return;
            }

            if (!isJpegSignature(fileBytes)) {
                additionalInfo.put("Ошибка", "Неверная сигнатура JPEG");
                metadata.setAdditionalInfo(additionalInfo);
                return;
            }

            analyzeJpegSegments(fileBytes, additionalInfo);

        } catch (Exception e) {
            System.err.println("Error analyzing JPEG specifics: " + e.getMessage());
            additionalInfo = null;
        } finally {
            metadata.setAdditionalInfo(additionalInfo);
        }
    }
}