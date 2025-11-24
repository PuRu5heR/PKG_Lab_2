package by.pkg.pkg_lab_2.service;

import by.pkg.pkg_lab_2.model.ImageMetadata;

import java.util.HashMap;
import java.util.Map;

public class TifAnalyzerService {
    private static int readShort(byte[] data, int offset, boolean isLittleEndian) {
        if (offset + 1 >= data.length) return 0;
        if (isLittleEndian) {
            return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
        } else {
            return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        }
    }

    private static int readInt(byte[] data, int offset, boolean isLittleEndian) {
        if (offset + 3 >= data.length) return 0;
        if (isLittleEndian) {
            return (data[offset] & 0xFF) |
                    ((data[offset + 1] & 0xFF) << 8) |
                    ((data[offset + 2] & 0xFF) << 16) |
                    ((data[offset + 3] & 0xFF) << 24);
        } else {
            return ((data[offset] & 0xFF) << 24) |
                    ((data[offset + 1] & 0xFF) << 16) |
                    ((data[offset + 2] & 0xFF) << 8) |
                    (data[offset + 3] & 0xFF);
        }
    }

    private static int getDimensionValue(byte[] fileBytes, int dataType, int count, int valueOffset, boolean isLittleEndian) {
        if (dataType == 3 || dataType == 4) {
            if (count == 1) {
                if (dataType == 3) {
                    return valueOffset & 0xFFFF;
                } else {
                    return valueOffset;
                }
            } else {
                if (dataType == 3) {
                    return readShort(fileBytes, valueOffset, isLittleEndian);
                } else {
                    return readInt(fileBytes, valueOffset, isLittleEndian);
                }
            }
        }
        return -1;
    }

    private static String getBitsPerSample(byte[] fileBytes, int dataType, int count, int valueOffset, boolean isLittleEndian) {
        if (dataType == 3 && count > 0) {
            StringBuilder sb = new StringBuilder();
            if (count == 1) {
                int bits = valueOffset & 0xFFFF;
                return bits + " bit";
            } else {
                for (int i = 0; i < count; i++) {
                    if (i > 0) sb.append("+");
                    int bits = readShort(fileBytes, valueOffset + (i * 2), isLittleEndian);
                    sb.append(bits);
                }
                sb.append(" bit");
                return sb.toString();
            }
        } else if (dataType == 4 && count == 1) {
            return (valueOffset & 0xFFFF) + " bit";
        }
        return "N/A";
    }

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

    private static String getColorSpace(int value) {
        return switch (value) {
            case 0 -> "WhiteIsZero (Grayscale)";
            case 1 -> "BlackIsZero (Grayscale)";
            case 2 -> "RGB";
            case 3 -> "RGB Palette";
            case 4 -> "Transparency Mask";
            case 5 -> "CMYK";
            case 6 -> "YCbCr";
            case 8 -> "CIELab";
            default -> "N/A";
        };
    }

    private static double getResolution(byte[] fileBytes, int dataType, int count, int valueOffset, boolean isLittleEndian) {
        try {
            if (dataType == 5 && count == 1) { // RATIONAL
                int numerator = readInt(fileBytes, valueOffset, isLittleEndian);
                int denominator = readInt(fileBytes, valueOffset + 4, isLittleEndian);
                if (denominator != 0) {
                    return (double) numerator / denominator;
                }
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String getResolutionUnit(int value) {
        return switch (value) {
            case 1 -> "Нету";
            case 2 -> "Дюйм";
            case 3 -> "Сантиметр";
            default -> "Неизвестно (" + value + ")";
        };
    }

    private static void analyzeTag(byte[] fileBytes, int offset, boolean isLittleEndian, Map<String, String> additionalInfo) {
        int tagId = 0;
        try {
            tagId = readShort(fileBytes, offset, isLittleEndian);
            int dataType = readShort(fileBytes, offset + 2, isLittleEndian);
            int count = readInt(fileBytes, offset + 4, isLittleEndian);
            int valueOffset = readInt(fileBytes, offset + 8, isLittleEndian);

            switch (tagId) {
                case 256:
                    additionalInfo.put("Ширина", getDimensionValue(fileBytes, dataType, count, valueOffset, isLittleEndian) + " px");
                    break;
                case 257:
                    additionalInfo.put("Высота", getDimensionValue(fileBytes, dataType, count, valueOffset, isLittleEndian) + " px");
                    break;
                case 258:
                    String bitsInfo = getBitsPerSample(fileBytes, dataType, count, valueOffset, isLittleEndian);
                    additionalInfo.put("Глубина цвета", bitsInfo);
                    break;
                case 259:
                    additionalInfo.put("Сжатие", getCompressionType(valueOffset));
                    break;
                case 262:
                    additionalInfo.put("Цветовое пространство", getColorSpace(valueOffset));
                    break;
                case 282:
                    additionalInfo.put("Разрешение X", getResolution(fileBytes, dataType, count, valueOffset, isLittleEndian) + " dpi");
                    break;
                case 283:
                    additionalInfo.put("Разрешение Y", getResolution(fileBytes, dataType, count, valueOffset, isLittleEndian) + " dpi");
                    break;
                case 284:
                    additionalInfo.put("План конфигурации", valueOffset == 1 ? "Chunky" : "Planar");
                    break;
                case 296:
                    additionalInfo.put("Единицы разрешения", getResolutionUnit(valueOffset));
                    break;
            }

        } catch (Exception e) {
            System.err.println("Error analyzing tag " + tagId + ": " + e.getMessage());
        }
    }

    private static void analyzeIFD(byte[] fileBytes, int offset, boolean isLittleEndian, Map<String, String> additionalInfo) {
        try {
            int entryCount = readShort(fileBytes, offset, isLittleEndian);

            if (entryCount < 0 || entryCount > 1000) {
                additionalInfo.put("Ошибка IFD", "Некорректное количество записей: " + entryCount);
                return;
            }

            for (int i = 0; i < entryCount; i++) {
                int entryOffset = offset + 2 + (i * 12);
                analyzeTag(fileBytes, entryOffset, isLittleEndian, additionalInfo);
            }

        } catch (Exception e) {
            System.err.println("Error analyzing IFD: " + e.getMessage());
        }
    }

    public static String analyzeCompressionType(byte[] fileBytes) {
        try {
            if (fileBytes.length < 8) return "N/A";

            boolean isLittleEndian = (fileBytes[0] == 0x49 && fileBytes[1] == 0x49);

            int magic = ((fileBytes[2] & 0xFF) | ((fileBytes[3] & 0xFF) << 8));
            if (magic != 42) return "N/A";

            int ifdOffset = readInt(fileBytes, 4, isLittleEndian);
            if (ifdOffset < 8 || ifdOffset >= fileBytes.length - 8) {
                return "N/A";
            }

            int entryCount = readShort(fileBytes, ifdOffset, isLittleEndian);

            for (int i = 0; i < entryCount; i++) {
                int entryOffset = ifdOffset + 2 + (i * 12);
                int tagId = readShort(fileBytes, entryOffset, isLittleEndian);

                if (tagId == 259) {
                    int compressionType = readShort(fileBytes, entryOffset + 8, isLittleEndian);
                    return getCompressionType(compressionType);
                }
            }

            return "без сжатия";

        } catch (Exception e) {
            return "N/A";
        }
    }

    public static void analyze(byte[] fileBytes, ImageMetadata metadata) {
        Map<String, String> additionalInfo = new HashMap<>();

        try {
            if (fileBytes.length < 8) {
                additionalInfo.put("Ошибка", "Файл слишком мал для формата TIFF");
                metadata.setAdditionalInfo(additionalInfo);
                return;
            }

            boolean isLittleEndian = (fileBytes[0] == 0x49 && fileBytes[1] == 0x49);
            boolean isBigEndian = (fileBytes[0] == 0x4D && fileBytes[1] == 0x4D);

            if (!isLittleEndian && !isBigEndian) {
                additionalInfo.put("Ошибка", "Неверная сигнатура TIFF");
                metadata.setAdditionalInfo(additionalInfo);
                return;
            }

            additionalInfo.put("Порядок байт", isLittleEndian ? "Little endian" : "Big endian");

            int magicNumber = readShort(fileBytes, 2, isLittleEndian);
            if (magicNumber != 42) {
                additionalInfo.put("Ошибка", "Неверный magic number TIFF: " + magicNumber);
                metadata.setAdditionalInfo(additionalInfo);
                return;
            }

            int firstIFDOffset = readInt(fileBytes, 4, isLittleEndian);

            if (firstIFDOffset < 8 || firstIFDOffset >= fileBytes.length) {
                additionalInfo.put("Ошибка", "Некорректный offset первого IFD: " + firstIFDOffset);
                metadata.setAdditionalInfo(additionalInfo);
                return;
            }

            analyzeIFD(fileBytes, firstIFDOffset, isLittleEndian, additionalInfo);

        } catch (Exception e) {
            System.err.println("Error analyzing TIFF specifics: " + e.getMessage());
            additionalInfo = null;
        } finally {
            metadata.setAdditionalInfo(additionalInfo);
        }
    }
}