package com.su26isc301.backend.service;

import com.su26isc301.backend.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportEvidenceStorageService {

    private final SupabaseStorageService supabaseStorageService;
    private final Path localUploadDir = Path.of("uploads", "report-evidences").toAbsolutePath().normalize();

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "application/pdf"
    );

    public String storeFile(MultipartFile file) {
        // 1. Validate file
        validateFile(file);

        // 2. Try Supabase Storage first
        try {
            return supabaseStorageService.uploadFile(file, "report-evidences");
        } catch (Exception e) {
            System.err.println("Không thể upload lên Supabase, chuyển hướng lưu cục bộ: " + e.getMessage());
        }

        // 3. Fallback to local storage
        try {
            if (!Files.exists(localUploadDir)) {
                Files.createDirectories(localUploadDir);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String fileName = UUID.randomUUID().toString() + extension;
            Path filePath = localUploadDir.resolve(fileName).normalize();

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Return relative URL that maps to static handler `/uploads/report-evidences/**`
            return "/uploads/report-evidences/" + fileName;
        } catch (IOException ioException) {
            throw new RuntimeException("Lưu bằng chứng cục bộ thất bại: " + ioException.getMessage(), ioException);
        }
    }

    public List<String> storeFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }
        if (files.size() > 5) {
            throw new BadRequestException("Tối đa chỉ được đính kèm 5 file bằng chứng");
        }

        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                urls.add(storeFile(file));
            }
        }
        return urls;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("File bằng chứng không được để trống");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Kích thước file vượt quá giới hạn 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Định dạng file không hỗ trợ. Chỉ chấp nhận JPG/JPEG/PNG/PDF");
        }
    }
}
