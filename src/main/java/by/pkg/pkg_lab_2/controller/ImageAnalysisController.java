package by.pkg.pkg_lab_2.controller;

import by.pkg.pkg_lab_2.model.ImageMetadata;
import by.pkg.pkg_lab_2.service.ImageAnalyzerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "*")
public class ImageAnalysisController {

    @Autowired
    private ImageAnalyzerService imageAnalyzerService;

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Image Analyzer is running");
    }

    @PostMapping("/analyze")
    public ResponseEntity<List<ImageMetadata>> analyzeImages(
            @RequestParam("files") MultipartFile[] files) {

        try {
            System.out.println("Received " + files.length + " files for analysis");

            List<ImageMetadata> allResults = new ArrayList<>();
            int batchSize = 1000;

            for (int i = 0; i < files.length; i += batchSize) {
                int end = Math.min(files.length, i + batchSize);
                MultipartFile[] batch = new MultipartFile[end - i];
                System.arraycopy(files, i, batch, 0, batch.length);

                System.out.println("Processing batch " + (i/batchSize + 1) + " with " + batch.length + " files");

                List<ImageMetadata> batchResults = imageAnalyzerService.analyzeImages(batch);
                allResults.addAll(batchResults);

                if (i + batchSize < files.length) {
                    System.gc();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            System.out.println("Successfully processed " + allResults.size() + " files");
            return ResponseEntity.ok(allResults);

        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/analyze-zip")
    public ResponseEntity<List<ImageMetadata>> analyzeZip(@RequestParam("zipFile") MultipartFile zipFile) {
        try {
            if (zipFile.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            System.out.println("Processing ZIP archive: " + zipFile.getOriginalFilename());

            List<MultipartFile> imageFiles = new ArrayList<>();

            try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
                ZipEntry entry;
                byte[] buffer = new byte[1024];

                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory() && isImageFile(entry.getName())) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            baos.write(buffer, 0, len);
                        }

                        imageFiles.add(getMultipartFile(entry, baos.toByteArray()));
                    }
                    zis.closeEntry();
                }
            }

            System.out.println("Found " + imageFiles.size() + " image files in ZIP");

            MultipartFile[] filesArray = imageFiles.toArray(new MultipartFile[0]);
            List<ImageMetadata> results = imageAnalyzerService.analyzeImages(filesArray);

            return ResponseEntity.ok(results);

        } catch (Exception e) {
            System.err.println("Error processing ZIP file: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private MultipartFile getMultipartFile(ZipEntry entry, byte[] fileData) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return entry.getName();
            }

            @Override
            public String getContentType() {
                return getContentTypeForFilename(entry.getName());
            }

            @Override
            public boolean isEmpty() {
                return fileData.length == 0;
            }

            @Override
            public long getSize() {
                return fileData.length;
            }

            @Override
            public byte[] getBytes() {
                return fileData;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(fileData);
            }

            @Override
            public void transferTo(java.io.File dest) throws IllegalStateException {
                throw new UnsupportedOperationException();
            }
        };
    }

    private boolean isImageFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".gif") ||
                lower.endsWith(".bmp") || lower.endsWith(".tif") ||
                lower.endsWith(".tiff") || lower.endsWith(".pcx");
    }

    private String getContentTypeForFilename(String filename) {
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".gif")) return "image/gif";
        if (filename.endsWith(".bmp")) return "image/bmp";
        if (filename.endsWith(".tif") || filename.endsWith(".tiff")) return "image/tiff";
        if (filename.endsWith(".pcx")) return "image/x-pcx";
        return "application/octet-stream";
    }
}