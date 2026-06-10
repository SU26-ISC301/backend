package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.entity.AuditLog;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.repository.AuditLogRepository;
import com.su26isc301.backend.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final ProfileRepository profileRepository;

    /**
     * API Admin: Lấy danh sách toàn bộ audit logs kèm tìm kiếm, lọc và phân trang
     */
    @GetMapping("/api/admin/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getAdminAuditLogs(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "action", required = false) String action
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> logs = auditLogRepository.findWithFilters(
                (query != null && !query.trim().isEmpty()) ? query.trim() : null,
                (action != null && !action.trim().isEmpty()) ? action.trim() : null,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách nhật ký hệ thống thành công", logs));
    }

    /**
     * API Admin: Lấy danh sách các loại hành động duy nhất trong log để hiển thị dropdown bộ lọc
     */
    @GetMapping("/api/admin/audit-logs/actions")
    public ResponseEntity<ApiResponse<List<String>>> getAdminDistinctActions() {
        List<String> actions = auditLogRepository.findDistinctActions();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách hành động thành công", actions));
    }

    /**
     * API Vendor: Lấy danh sách audit logs thuộc riêng Vendor đang đăng nhập
     */
    @GetMapping("/vendors/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getVendorAuditLogs(
            Authentication authentication,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "action", required = false) String action
    ) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Vui lòng đăng nhập"));
        }
        String email = String.valueOf(authentication.getPrincipal());
        Profile profile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản người dùng"));

        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> logs = auditLogRepository.findByUserIdWithFilters(
                profile.getId(),
                (query != null && !query.trim().isEmpty()) ? query.trim() : null,
                (action != null && !action.trim().isEmpty()) ? action.trim() : null,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách nhật ký hoạt động cửa hàng thành công", logs));
    }

    /**
     * API Vendor: Lấy danh sách các loại hành động duy nhất thuộc riêng Vendor để hiển thị dropdown bộ lọc
     */
    @GetMapping("/vendors/audit-logs/actions")
    public ResponseEntity<ApiResponse<List<String>>> getVendorDistinctActions(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Vui lòng đăng nhập"));
        }
        String email = String.valueOf(authentication.getPrincipal());
        Profile profile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản người dùng"));

        List<String> actions = auditLogRepository.findDistinctActionsByUserId(profile.getId());
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách hành động cửa hàng thành công", actions));
    }
}
