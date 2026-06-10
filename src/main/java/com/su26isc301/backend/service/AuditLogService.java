package com.su26isc301.backend.service;

import com.su26isc301.backend.entity.AuditLog;
import com.su26isc301.backend.repository.AuditLogRepository;
import com.su26isc301.backend.repository.ProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ProfileRepository profileRepository;

    /**
     * Ghi log dựa trên người dùng hiện tại đang đăng nhập trong SecurityContext
     */
    public void log(String action, String payload) {
        String ip = "unknown";
        String userAgent = "unknown";

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            ip = getClientIp(request);
            userAgent = getClientUserAgent(request);
        }

        String email = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            email = String.valueOf(auth.getPrincipal());
        }

        saveLogAsync(email, null, action, ip, userAgent, payload);
    }

    /**
     * Ghi log tường minh khi biết email người dùng (ví dụ: login lỗi, login thành công trước khi lưu SecurityContext)
     */
    public void logExplicit(String email, String action, String payload) {
        String ip = "unknown";
        String userAgent = "unknown";

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            ip = getClientIp(request);
            userAgent = getClientUserAgent(request);
        }

        saveLogAsync(email, null, action, ip, userAgent, payload);
    }

    /**
     * Ghi log bất đồng bộ lưu vào cơ sở dữ liệu
     */
    @Async
    public void saveLogAsync(String email, UUID userId, String action, String ip, String userAgent, String payload) {
        try {
            AuditLog.AuditLogBuilder builder = AuditLog.builder()
                    .action(action)
                    .ipAddress(ip)
                    .userAgent(userAgent)
                    .payloadSnapshot(payload);

            if (email != null && !email.trim().isEmpty()) {
                builder.userEmail(email);
                profileRepository.findByEmail(email).ifPresent(profile -> {
                    builder.userId(profile.getId());
                    builder.userRole(profile.getRole().name());
                });
            } else if (userId != null) {
                builder.userId(userId);
                profileRepository.findById(userId).ifPresent(profile -> {
                    builder.userEmail(profile.getEmail());
                    builder.userRole(profile.getRole().name());
                });
            }

            auditLogRepository.save(builder.build());
        } catch (Exception e) {
            System.err.println("Lỗi ghi nhận audit log: " + e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String getClientUserAgent(HttpServletRequest request) {
        if (request == null) return "unknown";
        return request.getHeader("User-Agent");
    }
}
