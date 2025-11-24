package by.pkg.pkg_lab_2.service;

import by.pkg.pkg_lab_2.model.ImageMetadata;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PngAnalyzerService {
    private static String getCompressionMethod(int method) {
        return method == 0 ? "Deflate" : "Неизвестно";
    }

    private static String getFilterMethod(int method) {
        return method == 0 ? "Adaptive" : "Неизвестно";
    }

    private static String getInterlaceMethod(int method) {
        return switch (method) {
            case 0 -> "None";
            case 1 -> "Adam7";
            default -> "Неизвестно";
        };
    }

    private static boolean hasTransparency(int colorType) {
        return colorType == 4 || colorType == 6;
    }

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

    /*
    Байты:   0   1   2   3   4   5   6   7   8   9   10  11  12  13  14  15  16
            ↓   ↓   ↓   ↓   ↓   ↓   ↓   ↓   ↓   ↓   ↓   ↓   ↓   ↓   ↓   ↓   ↓
            [LEN][LEN][LEN][LEN]['I']['H']['D']['R'][W1 ][W2 ][W3 ][W4 ][H1 ][H2 ][H3 ][H4 ][BD ][CT ][CM ][FM ][IL ]

    Где:
    LEN = Length (00 00 00 0D)  ← fileBytes[i-4] ... fileBytes[i-1]
    IHDR = Chunk type           ← fileBytes[i] ... fileBytes[i+3]
    W1-W4 = Width (4 байта)     ← fileBytes[i+4] ... fileBytes[i+7]
    H1-H4 = Height (4 байта)    ← fileBytes[i+8] ... fileBytes[i+11]
    BD = Bit Depth              ← fileBytes[i+12]
    CT = Color Type             ← fileBytes[i+13]
    CM = Compression Method     ← fileBytes[i+14]
    FM = Filter Method          ← fileBytes[i+15]
    IL = Interlace Method       ← fileBytes[i+16]
    */
    public static void analyze(byte[] fileBytes, ImageMetadata metadata) {
        Map<String, String> additionalInfo = new HashMap<>();

        try {
            if (fileBytes.length > 8) {
                byte[] pngSignature = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
                boolean isPng = true;
                for (int i = 0; i < 8; i++) {
                    if (fileBytes[i] != pngSignature[i]) {
                        isPng = false;
                        break;
                    }
                }
                additionalInfo.put("Сигнатура PNG", isPng ? "Корректная" : "Некорректная");
                if (isPng) {
                    for (int i = 8; i <= fileBytes.length - 25; i++) {
                        if (fileBytes[i] == 'I' && fileBytes[i+1] == 'H' &&
                                fileBytes[i+2] == 'D' && fileBytes[i+3] == 'R') {

                            int chunkLength = ((fileBytes[i-4] & 0xFF) << 24) |
                                    ((fileBytes[i-3] & 0xFF) << 16) |
                                    ((fileBytes[i-2] & 0xFF) << 8) |
                                    (fileBytes[i-1] & 0xFF);

                            if (chunkLength != 13) {
                                continue;
                            }

                            int width = ((fileBytes[i+4] & 0xFF) << 24) |
                                    ((fileBytes[i+5] & 0xFF) << 16) |
                                    ((fileBytes[i+6] & 0xFF) << 8) |
                                    (fileBytes[i+7] & 0xFF);

                            int height = ((fileBytes[i+8] & 0xFF) << 24) |
                                    ((fileBytes[i+9] & 0xFF) << 16) |
                                    ((fileBytes[i+10] & 0xFF) << 8) |
                                    (fileBytes[i+11] & 0xFF);

                            int bitDepth = fileBytes[i+12] & 0xFF;
                            int colorType = fileBytes[i+13] & 0xFF;
                            int compression = fileBytes[i+14] & 0xFF;
                            int filter = fileBytes[i+15] & 0xFF;
                            int interlace = fileBytes[i+16] & 0xFF;

                            additionalInfo.put("Тип цвета PNG", getColorType(colorType));
                            additionalInfo.put("Глубина битов", bitDepth + " bit");
                            additionalInfo.put("Размер", width + " × " + height + " px");
                            additionalInfo.put("Сжатие", getCompressionMethod(compression));
                            additionalInfo.put("Фильтрация", getFilterMethod(filter));
                            additionalInfo.put("Чередование", getInterlaceMethod(interlace));
                            additionalInfo.put("Поддержка прозрачности", hasTransparency(colorType) ? "Да" : "Нет");
                            break;
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error analyzing PNG specifics: " + e.getMessage());
            additionalInfo = null;
        } finally {
            metadata.setAdditionalInfo(additionalInfo);
        }
    }
}
