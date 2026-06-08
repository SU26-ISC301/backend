package com.su26isc301.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.anon.key}")
    private String supabaseAnonKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Hàm dùng chung để đẩy bất kỳ file nào lên Supabase Storage dựa theo Bucket chỉ định
     * @param file Đối tượng file nhận từ client
     * @param bucketName Tên bucket trên Supabase (ví dụ: "avatars" hoặc "products")
     * @return Chuỗi đường dẫn Public URL của file sau khi upload thành công
     */
    public String uploadFile(MultipartFile file, String bucketName) throws IOException {
        // Tạo tên file ngẫu nhiên bằng UUID để tránh trùng lặp trùng tên
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
        String fileName = UUID.randomUUID().toString() + extension;

        // Xây dựng Endpoint REST API của Supabase Storage
        String uploadUrl = String.format("%s/storage/v1/object/%s/%s", supabaseUrl, bucketName, fileName);

        // Thiết lập Headers cấu hình quyền truy cập
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + supabaseAnonKey);
        headers.set("apiKey", supabaseAnonKey);
        headers.setContentType(MediaType.parseMediaType(
                file.getContentType() != null ? file.getContentType() : "application/octet-stream"
        ));

        // Gửi mảng byte nhị phân của file qua HTTP POST
        HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);
        ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            // Trả về Public URL trực tiếp của file để lưu vào Database
            return String.format("%s/storage/v1/object/public/%s/%s", supabaseUrl, bucketName, fileName);
        } else {
            throw new RuntimeException("Tải file lên Supabase Storage thất bại cho bucket: " + bucketName);
        }
    }

    /**
     * Hàm dùng chung để xóa file cũ trên Supabase Storage
     * @param publicUrl Đường dẫn Public URL của file cần xóa (lấy từ Database)
     * @param bucketName Tên bucket chứa file (ví dụ: "avatars" hoặc "products")
     */
    public void deleteFile(String publicUrl, String bucketName) {
        // Nếu user chưa có ảnh cũ (publicUrl là null hoặc rỗng), thì không cần xóa
        if (publicUrl == null || publicUrl.trim().isEmpty()) {
            return;
        }

        try {
            // 1. Cắt chuỗi để lấy tên file gốc từ Public URL
            String baseUrl = String.format("%s/storage/v1/object/public/%s/", supabaseUrl, bucketName);

            // Nếu link ảnh cũ không phải của Supabase (vd link Facebook, Google), thì không xóa
            if (!publicUrl.startsWith(baseUrl)) {
                return;
            }

            String fileName = publicUrl.replace(baseUrl, "");

            // 2. Xây dựng đường dẫn API để XÓA (Lưu ý đường dẫn xóa KHÔNG có chữ 'public/')
            String deleteUrl = String.format("%s/storage/v1/object/%s/%s", supabaseUrl, bucketName, fileName);

            // 3. Thiết lập quyền (Header)
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseAnonKey);
            headers.set("apiKey", supabaseAnonKey);

            // 4. Gửi lệnh DELETE lên Supabase
            HttpEntity<String> requestEntity = new HttpEntity<>(headers);
            restTemplate.exchange(deleteUrl, HttpMethod.DELETE, requestEntity, String.class);

            System.out.println("Đã dọn dẹp thành công file rác: " + fileName);

        } catch (Exception e) {
            System.err.println("Không thể xóa file cũ trên Supabase: " + e.getMessage());
        }
    }

    /**
     * Lấy danh sách thông tin các file trong bucket (dùng để so sánh dọn rác)
     * @param bucketName Tên bucket
     * @param prefix Tiền tố thư mục (VD: "images" hoặc "videos")
     * @return Danh sách maps chứa thông tin file (name, created_at, v.v.)
     */
    public List<Map<String, Object>> listFiles(String bucketName, String prefix) {
        String url = String.format("%s/storage/v1/object/list/%s", supabaseUrl, bucketName);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + supabaseAnonKey);
        headers.set("apiKey", supabaseAnonKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("limit", 1000);
        body.put("offset", 0);
        body.put("prefix", prefix != null ? prefix : "");
        body.put("sortBy", Map.of("column", "name", "order", "asc"));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, List.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (List<Map<String, Object>>) response.getBody();
            }
        } catch (Exception e) {
            System.err.println("Không thể lấy danh sách file từ Supabase: " + e.getMessage());
        }
        return List.of();
    }
}