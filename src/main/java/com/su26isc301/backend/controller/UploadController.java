package com.su26isc301.backend.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class UploadController {

    private final Path avatarDir = Path.of("uploads", "avatars").toAbsolutePath().normalize();

    @GetMapping({
            "/uploads/avatars/{fileName:.+}",
            "/api/auth/uploads/avatars/{fileName:.+}"
    })
    public ResponseEntity<Resource> getAvatar(@PathVariable String fileName) throws MalformedURLException {
        Path filePath = avatarDir.resolve(fileName).normalize();
        if (!filePath.startsWith(avatarDir) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        String contentType = "application/octet-stream";
        try {
            String detectedType = Files.probeContentType(filePath);
            if (detectedType != null) {
                contentType = detectedType;
            }
        } catch (Exception ignored) {
            // Fall back to a safe binary content type.
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .body(resource);
    }
}
