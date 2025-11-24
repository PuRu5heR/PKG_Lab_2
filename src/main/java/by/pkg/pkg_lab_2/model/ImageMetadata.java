package by.pkg.pkg_lab_2.model;

import lombok.Data;

import java.util.Map;

@Data
public class ImageMetadata {
    private String filename;
    private String dimensions;
    private String resolution;
    private String colorDepth;
    private String compression;
    private String fileSize;
    private String format;
    private Map<String, String> additionalInfo;

    public ImageMetadata(String filename) {
        this.filename = filename;
    }
}