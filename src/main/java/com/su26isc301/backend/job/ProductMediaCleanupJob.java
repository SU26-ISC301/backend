package com.su26isc301.backend.job;

import com.su26isc301.backend.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductMediaCleanupJob {

    private final SupabaseStorageService supabaseStorageService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${supabase.url}")
    private String supabaseUrl;

    // Chạy định kỳ vào lúc 2:00 AM mỗi ngày
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOrphanedProductMedia() {
        log.info("[CRON JOB] Bắt đầu dọn dẹp file hình ảnh/video sản phẩm rác trên Supabase...");
        try {
            // 1. Lấy tất cả URL đang được sử dụng trong Database (gộp từ 3 bảng: media chính, biến thể, và thuộc tính)
            String sql = "SELECT media_url as url FROM product_media " +
                         "UNION " +
                         "SELECT image_url as url FROM product_variants WHERE image_url IS NOT NULL " +
                         "UNION " +
                         "SELECT image_url as url FROM product_attribute_values WHERE image_url IS NOT NULL";
            
            List<String> usedUrls = jdbcTemplate.queryForList(sql, String.class);
            
            // Xây dựng Set chứa tên file để tra cứu O(1)
            Set<String> usedFileNames = new HashSet<>();
            for (String url : usedUrls) {
                if (url != null && url.contains("/product-media/")) {
                    String fileName = url.substring(url.lastIndexOf("/product-media/") + 15);
                    usedFileNames.add(fileName);
                }
            }

            // 2. Lấy danh sách tất cả file đang có trên bucket 'product-media'
            List<Map<String, Object>> storageFiles = supabaseStorageService.listFiles("product-media");

            int deletedCount = 0;
            // Chỉ xóa các file tải lên cách đây hơn 24 giờ (tránh xóa nhầm file người dùng đang tạo dở)
            Instant threshold = Instant.now().minus(24, ChronoUnit.HOURS);

            // 3. So sánh và xóa
            for (Map<String, Object> fileObj : storageFiles) {
                String fileName = (String) fileObj.get("name");
                String createdAtStr = (String) fileObj.get("created_at");
                
                // Bỏ qua nếu là thư mục rỗng
                if (fileName == null || fileName.isBlank() || fileName.equals(".emptyFolderPlaceholder")) {
                    continue;
                }

                boolean isUsed = usedFileNames.contains(fileName);

                if (!isUsed && createdAtStr != null) {
                    Instant createdAt = Instant.parse(createdAtStr);
                    if (createdAt.isBefore(threshold)) {
                        // Tái tạo lại Public URL để truyền vào hàm delete
                        String publicUrlToDelete = String.format("%s/storage/v1/object/public/product-media/%s", supabaseUrl, fileName);
                        
                        // Xóa file
                        supabaseStorageService.deleteFile(publicUrlToDelete, "product-media");
                        deletedCount++;
                        log.info(" Đã xóa file rác: {}", fileName);
                    }
                }
            }

            log.info("[CRON JOB] Hoàn thành dọn dẹp! Đã xóa {} file rác.", deletedCount);

        } catch (Exception e) {
            log.error("[CRON JOB] Lỗi trong quá trình dọn dẹp file: ", e);
        }
    }
}
