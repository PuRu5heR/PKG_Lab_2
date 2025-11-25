package by.pkg.pkg_lab_2.service;

import by.pkg.pkg_lab_2.model.ImageMetadata;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

@Service
public class ImageAnalyzerService {
    public List<ImageMetadata> analyzeImages(MultipartFile[] files) {
        List<ImageMetadata> results = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                ImageMetadata metadata = analyzeSingleFile(file);
                results.add(metadata);
            } catch (Exception e) {
                ImageMetadata errorMeta = new ImageMetadata(file.getOriginalFilename());
                errorMeta.setFileSize(formatFileSize(file.getSize()));
                errorMeta.setAdditionalInfo(Map.of(
                        "Ошибка", "Неверный формат файла",
                        "MIME Type", Objects.requireNonNull(file.getContentType())
                ));
                results.add(errorMeta);
            }
        }

        return results;
    }

    private ImageMetadata analyzeSingleFile(MultipartFile file) throws Exception {
        ImageMetadata metadata = new ImageMetadata(file.getOriginalFilename());
        metadata.setFileSize(formatFileSize(file.getSize()));

        String format = determineFormat(file.getOriginalFilename());
        metadata.setFormat(format);

        byte[] fileBytes = file.getBytes();

        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            BufferedImage image = ImageIO.read(is);
            if (image != null) {
                metadata.setDimensions(image.getWidth() + "×" + image.getHeight());
                metadata.setColorDepth(getColorDepth(image));

                String compression = determineCompression(format, fileBytes);
                metadata.setCompression(compression);

                if ("GIF".equalsIgnoreCase(format)) {
                    analyzeGifPalette(fileBytes, metadata);
                } else if ("JPEG".equalsIgnoreCase(format)) {
                    JpgAnalyzerService.analyze(fileBytes, metadata);
                } else if ("TIFF".equalsIgnoreCase(format)) {
                    TifAnalyzerService.analyze(fileBytes, metadata);
                } else if ("PNG".equalsIgnoreCase(format)) {
                    PngAnalyzerService.analyze(fileBytes, metadata);
                }
            } else {
                throw new Exception("Не удалось прочитать изображение");
            }
        }

        extractResolution(fileBytes, metadata);

        return metadata;
    }

    private String determineCompression(String format, byte[] fileBytes) {
        return switch (format.toUpperCase()) {
            case "JPEG" -> "JPEG";
            case "PNG" -> "Deflate";
            case "GIF" -> "LZW";
            case "BMP" -> analyzeBmpCompressionType(fileBytes);
            case "TIFF" -> TifAnalyzerService.analyzeCompressionType(fileBytes);
            case "PCX" -> analyzePcxCompressionType(fileBytes);
            default -> "Unknown - " + format.toUpperCase();
        };
    }

    private String analyzeBmpCompressionType(byte[] fileBytes) {
        try {
            if (fileBytes.length < 30) return "N/A";

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
        } catch (Exception e) {
            return "N/A";
        }
    }

    private String analyzePcxCompressionType(byte[] fileBytes) {
        try {
            if (fileBytes.length < 4) return "N/A";

            int version = fileBytes[2] & 0xFF;
            int encoding = fileBytes[3] & 0xFF;

            String versionStr = getPcxVersion(version);
            String compressionStr = (encoding == 1) ? "RLE" : "без сжатия";

            return "PCX " + versionStr + " (" + compressionStr + ")";

        } catch (Exception e) {
            return "N/A";
        }
    }

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

    private void extractResolution(byte[] fileBytes, ImageMetadata imageMetadata) {
        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            Metadata extractedMetadata = ImageMetadataReader.readMetadata(is);

            Directory exifDir = extractedMetadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exifDir != null) {
                if (exifDir.containsTag(ExifIFD0Directory.TAG_X_RESOLUTION)) {
                    Object xRes = exifDir.getObject(ExifIFD0Directory.TAG_X_RESOLUTION);
                    Object yRes = exifDir.getObject(ExifIFD0Directory.TAG_Y_RESOLUTION);

                    if (xRes != null && yRes != null) {
                        imageMetadata.setResolution(xRes + "×" + yRes + " dpi");
                        return;
                    } else if (xRes != null) {
                        imageMetadata.setResolution(xRes + " dpi");
                        return;
                    }
                }
            }

            for (Directory directory : extractedMetadata.getDirectories()) {
                if (directory.containsTag(ExifIFD0Directory.TAG_X_RESOLUTION)) {
                    Object res = directory.getObject(ExifIFD0Directory.TAG_X_RESOLUTION);
                    if (res != null) {
                        imageMetadata.setResolution(res + " dpi");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting resolution: " + e.getMessage());
        }

        imageMetadata.setResolution("-");
    }

    private void analyzeGifPalette(byte[] fileBytes, ImageMetadata metadata) {
        Map<String, String> additionalInfo = new HashMap<>();

        try {
            if (fileBytes.length > 13) {
                String signature = new String(fileBytes, 0, 6);
                if ("GIF89a".equals(signature) || "GIF87a".equals(signature)) {
                    additionalInfo.put("Версия GIF", signature);
                }

                int packedByte = fileBytes[10] & 0xFF;
                boolean hasGlobalColorTable = (packedByte & 0x80) != 0;
                int colorResolution = ((packedByte & 0x70) >> 4) + 1;
                boolean sortFlag = (packedByte & 0x08) != 0;
                int globalColorTableSize = 2 << (packedByte & 0x07);

                additionalInfo.put("Глобальная палитра", hasGlobalColorTable ? "Да" : "Нет");

                if (hasGlobalColorTable) {
                    additionalInfo.put("Количество цветов в палитре", String.valueOf(globalColorTableSize));
                    additionalInfo.put("Разрешение цвета", colorResolution + " бит/канал");
                    additionalInfo.put("Сортировка палитры", sortFlag ? "Да" : "Нет");
                }

                boolean hasAnimation = false;
                for (int i = 0; i < fileBytes.length - 1; i++) {
                    if (fileBytes[i] == 0x21 && fileBytes[i+1] == (byte)0xF9) {
                        hasAnimation = true;
                        break;
                    }
                }
                additionalInfo.put("Анимация", hasAnimation ? "Да" : "Нет");
            }
        } catch (Exception e) {
            System.err.println("Error analyzing GIF palette: " + e.getMessage());
        } finally {
            metadata.setAdditionalInfo(additionalInfo);
        }
    }

    private String getColorDepth(BufferedImage image) {
        try {
            int colorDepth = image.getColorModel().getPixelSize();
            String depthInfo = colorDepth + " bit";

            String colorSpace = getColorSpaceInfo(image);
            if (!colorSpace.isEmpty()) {
                depthInfo += " [" + colorSpace + "]";
            }

            return depthInfo;

        } catch (Exception e) {
            return "N/A";
        }
    }

    private String getColorSpaceInfo(BufferedImage image) {
        try {
            return switch (image.getType()) {
                case BufferedImage.TYPE_BYTE_BINARY -> "Binary";
                case BufferedImage.TYPE_BYTE_INDEXED -> "Indexed";
                case BufferedImage.TYPE_BYTE_GRAY -> "Grayscale";
                case BufferedImage.TYPE_3BYTE_BGR, BufferedImage.TYPE_INT_BGR -> "BGR";
                case BufferedImage.TYPE_4BYTE_ABGR -> "ABGR";
                case BufferedImage.TYPE_INT_RGB -> "RGB";
                case BufferedImage.TYPE_INT_ARGB -> "ARGB";
                case BufferedImage.TYPE_USHORT_565_RGB -> "RGB 5-6-5";
                case BufferedImage.TYPE_USHORT_555_RGB -> "RGB 5-5-5";
                default -> "Type " + image.getType();
            };
        } catch (Exception e) {
            return "N/A";
        }
    }

    private String determineFormat(String filename) {
        if (filename == null) return "N/A";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "JPEG";
        if (lower.endsWith(".png")) return "PNG";
        if (lower.endsWith(".gif")) return "GIF";
        if (lower.endsWith(".bmp")) return "BMP";
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) return "TIFF";
        if (lower.endsWith(".pcx")) return "PCX";
        return "N/A";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}