package com.su26isc301.backend.service;

import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.enums.ReportPriority;
import com.su26isc301.backend.enums.ReportReasonCode;
import com.su26isc301.backend.enums.ReportStatus;
import com.su26isc301.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportRiskService {

    private final ReportRepository reportRepository;

    /**
     * Tính toán độ ưu tiên (priority) của report mới dựa trên lý do vi phạm
     * và tần suất vi phạm gần đây của vendor.
     */
    public ReportPriority calculatePriority(Long vendorId, ReportReasonCode reasonCode) {
        ReportPriority priority = reasonCode.getDefaultPriority();

        // Nếu lý do mặc định là HIGH, kiểm tra xem vendor đã bị bao nhiêu report HIGH trong 7 ngày qua
        if (priority == ReportPriority.HIGH) {
            ZonedDateTime sevenDaysAgo = ZonedDateTime.now().minusDays(7);
            // Có thể nâng lên CRITICAL nếu cùng vendor đã có từ 2 report HIGH trong 7 ngày
            // Để đơn giản, ta tìm các báo cáo có độ ưu tiên HIGH được tạo trong vòng 7 ngày qua
            long recentHighReports = reportRepository.findAll().stream()
                    .filter(r -> r.getVendor().getId().equals(vendorId)
                            && r.getPriority() == ReportPriority.HIGH
                            && r.getCreatedAt().isAfter(sevenDaysAgo))
                    .count();

            if (recentHighReports >= 2) {
                priority = ReportPriority.CRITICAL;
            }
        }

        return priority;
    }

    /**
     * Tính toán mức độ rủi ro (riskLevel) của cửa hàng (Vendor)
     * - LOW: có ít nhất 1 report hợp lệ (VALID hoặc ACTION_TAKEN) trong 30 ngày.
     * - MEDIUM: có ít nhất 3 report hợp lệ trong 30 ngày.
     * - HIGH: có ít nhất 5 report hợp lệ trong 30 ngày hoặc 2 report HIGH hợp lệ trong 7 ngày.
     * - CRITICAL: có ít nhất 3 report HIGH hợp lệ trong 7 ngày hoặc admin áp dụng temporary suspension (Vendor.status = "suspended").
     */
    public String calculateVendorRiskLevel(Vendor vendor) {
        if ("suspended".equalsIgnoreCase(vendor.getStatus())) {
            return "CRITICAL";
        }

        Long vendorId = vendor.getId();
        ZonedDateTime thirtyDaysAgo = ZonedDateTime.now().minusDays(30);
        ZonedDateTime sevenDaysAgo = ZonedDateTime.now().minusDays(7);

        // Báo cáo hợp lệ: status là VALID hoặc ACTION_TAKEN
        List<ReportStatus> validStatuses = List.of(ReportStatus.VALID, ReportStatus.ACTION_TAKEN);

        long validReports30Days = reportRepository.findAll().stream()
                .filter(r -> r.getVendor().getId().equals(vendorId)
                        && validStatuses.contains(r.getStatus())
                        && r.getCreatedAt().isAfter(thirtyDaysAgo))
                .count();

        long validHighReports7Days = reportRepository.findAll().stream()
                .filter(r -> r.getVendor().getId().equals(vendorId)
                        && validStatuses.contains(r.getStatus())
                        && r.getPriority() == ReportPriority.HIGH
                        && r.getCreatedAt().isAfter(sevenDaysAgo))
                .count();

        if (validHighReports7Days >= 3) {
            return "CRITICAL";
        }
        if (validReports30Days >= 5 || validHighReports7Days >= 2) {
            return "HIGH";
        }
        if (validReports30Days >= 3) {
            return "MEDIUM";
        }
        if (validReports30Days >= 1) {
            return "LOW";
        }

        return "SAFE";
    }
}
