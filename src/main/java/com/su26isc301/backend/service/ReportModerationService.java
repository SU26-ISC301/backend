package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.request.AdminAppealDecisionRequest;
import com.su26isc301.backend.dto.request.AdminReportActionRequest;
import com.su26isc301.backend.dto.request.AdminReportStatusUpdateRequest;
import com.su26isc301.backend.dto.request.SellerAppealRequest;
import com.su26isc301.backend.dto.response.ReportDetailResponse;
import com.su26isc301.backend.dto.response.ReportStatsResponse;
import com.su26isc301.backend.dto.response.ReportSummaryResponse;
import com.su26isc301.backend.dto.response.SellerViolationResponse;
import com.su26isc301.backend.entity.*;
import com.su26isc301.backend.enums.*;
import com.su26isc301.backend.exception.BadRequestException;
import com.su26isc301.backend.exception.ConflictException;
import com.su26isc301.backend.exception.ForbiddenAccessException;
import com.su26isc301.backend.exception.ResourceNotFoundException;
import com.su26isc301.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportModerationService {

    private final ReportRepository reportRepository;
    private final ReportEvidenceRepository reportEvidenceRepository;
    private final ReportActionRepository reportActionRepository;
    private final SellerViolationRepository sellerViolationRepository;
    private final ReportAppealRepository reportAppealRepository;
    private final ProfileRepository profileRepository;
    private final VendorRepository vendorRepository;
    private final ProductRepository productRepository;
    private final PostPromotionRepository postPromotionRepository;
    private final ReportService reportService;
    private final ReportRiskService riskService;
    private final AuditLogService auditLogService;

    // --- ADMIN METHODS ---

    @Transactional(readOnly = true)
    public Page<ReportSummaryResponse> getReports(
            Pageable pageable, ReportStatus status, ReportPriority priority,
            ReportReasonCode reasonCode, Long vendorId, UUID reporterUserId,
            ReportTargetType targetType, String query, ZonedDateTime fromDate, ZonedDateTime toDate
    ) {
        Specification<Report> spec = (root, q, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (priority != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("priority"), priority));
        }
        if (reasonCode != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("reasonCode"), reasonCode));
        }
        if (vendorId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("vendor").get("id"), vendorId));
        }
        if (reporterUserId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("reporterUserId"), reporterUserId));
        }
        if (targetType != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("targetType"), targetType));
        }
        if (fromDate != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
        }
        if (toDate != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
        }
        if (query != null && !query.trim().isEmpty()) {
            String likePattern = "%" + query.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("description")), likePattern),
                    cb.like(cb.lower(root.get("vendor").get("shopName")), likePattern),
                    cb.like(cb.lower(root.get("reporterEmailSnapshot")), likePattern),
                    cb.like(root.get("id").as(String.class), likePattern)
            ));
        }

        return reportRepository.findAll(spec, pageable).map(reportService::mapToSummaryResponse);
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse getReportDetailAdmin(Long id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Báo cáo không tồn tại"));
        // Admin gets unmasked details
        return reportService.mapToDetailResponse(report, null, Roles.admin);
    }

    @Transactional
    public ReportDetailResponse updateReportStatus(Long id, AdminReportStatusUpdateRequest request, String adminEmail) {
        Profile admin = profileRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản admin"));

        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Báo cáo không tồn tại"));

        ReportStatus oldStatus = report.getStatus();
        report.setStatus(request.getStatus());
        if (request.getAdminNote() != null) {
            report.setAdminNote(request.getAdminNote());
        }
        Report savedReport = reportRepository.save(report);

        // Audit Log
        try {
            auditLogService.log("REPORT_STATUS_UPDATED", String.format("Admin đổi trạng thái report #%d từ %s sang %s",
                    id, oldStatus.name(), request.getStatus().name()));
        } catch (Exception e) {
            System.err.println("Lỗi ghi audit log: " + e.getMessage());
        }

        return reportService.mapToDetailResponse(savedReport, admin, Roles.admin);
    }

    @Transactional
    public ReportDetailResponse applyAction(Long id, AdminReportActionRequest request, String adminEmail) {
        Profile admin = profileRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản admin"));

        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Báo cáo không tồn tại"));

        // Validate reason / action relationship
        if (request.getActionType() == null) {
            throw new BadRequestException("Hành động xử lý không hợp lệ");
        }

        // Bắt buộc nhập actionNote với các action nghiêm trọng
        List<ReportActionType> severeActions = List.of(
                ReportActionType.HIDE_PRODUCT, ReportActionType.DISABLE_PROMOTION,
                ReportActionType.RESTRICT_LISTING, ReportActionType.TEMPORARY_SUSPENSION,
                ReportActionType.PERMANENT_BAN
        );
        if (severeActions.contains(request.getActionType()) && (request.getActionNote() == null || request.getActionNote().trim().isEmpty())) {
            throw new BadRequestException("Ghi chú xử lý (actionNote) là bắt buộc đối với các hình phạt nghiêm trọng");
        }

        Vendor vendor = report.getVendor();
        Product product = report.getProduct();

        // Implement side effects
        switch (request.getActionType()) {
            case HIDE_PRODUCT:
                if (product == null) {
                    throw new BadRequestException("Báo cáo không liên kết với sản phẩm cụ thể");
                }
                product.setIsActive(false);
                product.setStatus("reported_hidden");
                product.setRejectReason(request.getActionNote());
                productRepository.save(product);

                // Create Violation
                createSellerViolation(vendor, report, "HIDE_PRODUCT", report.getPriority(), request.getExpiresAt());
                
                try {
                    auditLogService.log("REPORT_PRODUCT_HIDDEN", String.format("Ẩn sản phẩm #%d do vi phạm", product.getId()));
                } catch (Exception ignored) {}
                break;

            case DISABLE_PROMOTION:
                // Disable active PostPromotion for product or vendor
                List<PostPromotion> promotions = new ArrayList<>();
                if (product != null) {
                    promotions = postPromotionRepository.findByProductIdInAndStatus(List.of(product.getId()), "ACTIVE");
                } else {
                    promotions = postPromotionRepository.findByVendorId(vendor.getId()).stream()
                            .filter(pp -> "ACTIVE".equalsIgnoreCase(pp.getStatus()))
                            .toList();
                }

                if (promotions.isEmpty()) {
                    throw new BadRequestException("Không tìm thấy chiến dịch quảng cáo hoạt động nào để tắt");
                }

                for (PostPromotion promo : promotions) {
                    promo.setStatus("PAUSED");
                    promo.setStoppedAt(ZonedDateTime.now());
                    promo.setStopReason("Disabled by report action #" + id + " - Note: " + request.getActionNote());
                    postPromotionRepository.save(promo);
                }

                createSellerViolation(vendor, report, "DISABLE_PROMOTION", report.getPriority(), request.getExpiresAt());

                try {
                    auditLogService.log("REPORT_PROMOTION_DISABLED", String.format("Tắt chiến dịch quảng cáo cho vendor #%d", vendor.getId()));
                } catch (Exception ignored) {}
                break;

            case RESTRICT_LISTING:
                createSellerViolation(vendor, report, "RESTRICT_LISTING", report.getPriority(), request.getExpiresAt());
                try {
                    auditLogService.log("REPORT_VENDOR_RESTRICTED", String.format("Giới hạn đăng bán của vendor #%d", vendor.getId()));
                } catch (Exception ignored) {}
                break;

            case TEMPORARY_SUSPENSION:
                vendor.setStatus("suspended");
                vendorRepository.save(vendor);

                createSellerViolation(vendor, report, "TEMPORARY_SUSPENSION", report.getPriority(), request.getExpiresAt());
                try {
                    auditLogService.log("REPORT_VENDOR_SUSPENDED", String.format("Tạm khóa hoạt động vendor #%d", vendor.getId()));
                } catch (Exception ignored) {}
                break;

            case PERMANENT_BAN:
                vendor.setStatus("banned");
                vendorRepository.save(vendor);

                // Deactivate all product listings of this vendor
                List<Product> vendorProducts = productRepository.findByVendorId(vendor.getId());
                for (Product vp : vendorProducts) {
                    vp.setIsActive(false);
                    vp.setStatus("inactive");
                    productRepository.save(vp);
                }

                createSellerViolation(vendor, report, "PERMANENT_BAN", report.getPriority(), request.getExpiresAt());
                try {
                    auditLogService.log("REPORT_VENDOR_BANNED", String.format("Khóa vĩnh viễn vendor #%d và toàn bộ sản phẩm", vendor.getId()));
                } catch (Exception ignored) {}
                break;

            default:
                // For WARNING or REJECT_REPORT or RESTRICT_CHAT or NO_ACTION
                if (request.getActionType() == ReportActionType.REJECT_REPORT) {
                    report.setStatus(ReportStatus.REJECTED);
                } else if (request.getActionType() == ReportActionType.WARNING) {
                    createSellerViolation(vendor, report, "WARNING", report.getPriority(), request.getExpiresAt());
                }
                break;
        }

        // Save Report Action History
        ReportAction action = ReportAction.builder()
                .report(report)
                .actionType(request.getActionType())
                .actionNote(request.getActionNote())
                .actionByUserId(admin.getId())
                .actionByEmail(admin.getEmail())
                .expiresAt(request.getExpiresAt())
                .build();
        reportActionRepository.save(action);

        // Update Report Ticket Status
        if (report.getStatus() != ReportStatus.REJECTED) {
            report.setStatus(ReportStatus.ACTION_TAKEN);
        }
        if (request.getActionNote() != null) {
            report.setAdminNote(request.getActionNote());
        }
        Report savedReport = reportRepository.save(report);

        // Log Action
        try {
            auditLogService.log("REPORT_ACTION_APPLIED", String.format("Admin áp dụng hành động %s lên report #%d",
                    request.getActionType().name(), id));
        } catch (Exception ignored) {}

        return reportService.mapToDetailResponse(savedReport, admin, Roles.admin);
    }

    private void createSellerViolation(Vendor vendor, Report report, String type, ReportPriority severity, ZonedDateTime expiresAt) {
        SellerViolation violation = SellerViolation.builder()
                .vendor(vendor)
                .sellerProfileId(vendor.getProfile().getId())
                .report(report)
                .violationType(type)
                .severity(severity)
                .status(ViolationStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build();
        sellerViolationRepository.save(violation);
    }

    @Transactional(readOnly = true)
    public ReportStatsResponse getReportStats() {
        long newCount = reportRepository.countByStatus(ReportStatus.NEW);
        long reviewingCount = reportRepository.countByStatus(ReportStatus.REVIEWING);
        long highPriorityCount = reportRepository.countByPriority(ReportPriority.HIGH) + reportRepository.countByPriority(ReportPriority.CRITICAL);
        long appealedCount = reportRepository.countByStatus(ReportStatus.APPEALED);

        ZonedDateTime sevenDaysAgo = ZonedDateTime.now().minusDays(7);
        long reportsLast7Days = reportRepository.countByCreatedAtAfter(sevenDaysAgo);

        List<Object[]> groupByReason = reportRepository.countReportsGroupByReasonCode();
        List<ReportStatsResponse.ReasonCount> topReasons = groupByReason.stream()
                .map(row -> new ReportStatsResponse.ReasonCount(
                        row[0] != null ? ((ReportReasonCode) row[0]).name() : "OTHER",
                        row[1] != null ? ((Number) row[1]).longValue() : 0L
                ))
                .sorted(Comparator.comparingLong(ReportStatsResponse.ReasonCount::getCount).reversed())
                .toList();

        return ReportStatsResponse.builder()
                .newCount(newCount)
                .reviewingCount(reviewingCount)
                .highPriorityCount(highPriorityCount)
                .appealedCount(appealedCount)
                .reportsLast7Days(reportsLast7Days)
                .topReasons(topReasons)
                .build();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getVendorReportSummary(Long vendorId) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor không tồn tại"));

        long totalReports = reportRepository.countByVendorId(vendorId);
        long validReports = reportRepository.countValidReportsForVendorSince(vendorId, ReportStatus.VALID, ZonedDateTime.now().minusYears(10))
                + reportRepository.countValidReportsForVendorSince(vendorId, ReportStatus.ACTION_TAKEN, ZonedDateTime.now().minusYears(10));
        long reportsLast7Days = reportRepository.countReportsForVendorSince(vendorId, ZonedDateTime.now().minusDays(7));
        long reportsLast30Days = reportRepository.countReportsForVendorSince(vendorId, ZonedDateTime.now().minusDays(30));
        String riskLevel = riskService.calculateVendorRiskLevel(vendor);

        long activeViolations = sellerViolationRepository.findByVendorIdAndStatus(vendorId, ViolationStatus.ACTIVE).size();

        Map<String, Object> summary = new HashMap<>();
        summary.put("vendorId", vendorId);
        summary.put("totalReports", totalReports);
        summary.put("validReports", validReports);
        summary.put("reportsLast7Days", reportsLast7Days);
        summary.put("reportsLast30Days", reportsLast30Days);
        summary.put("riskLevel", riskLevel);
        summary.put("activeViolations", activeViolations);

        return summary;
    }

    @Transactional
    public ReportDetailResponse reviewAppeal(Long reportId, Long appealId, AdminAppealDecisionRequest request, String adminEmail) {
        Profile admin = profileRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản admin"));

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Báo cáo không tồn tại"));

        ReportAppeal appeal = reportAppealRepository.findById(appealId)
                .orElseThrow(() -> new ResourceNotFoundException("Khiếu nại không tồn tại"));

        if (!appeal.getReport().getId().equals(reportId)) {
            throw new BadRequestException("Khiếu nại không thuộc về báo cáo này");
        }

        appeal.setStatus(request.getDecision());
        appeal.setReviewNote(request.getReviewNote());
        appeal.setReviewedByUserId(admin.getId());
        appeal.setReviewedAt(ZonedDateTime.now());
        reportAppealRepository.save(appeal);

        if (request.getDecision() == AppealStatus.ACCEPTED) {
            report.setStatus(ReportStatus.CLOSED);

            // Deactivate violation record
            List<SellerViolation> violations = sellerViolationRepository.findByVendorId(report.getVendor().getId());
            for (SellerViolation violation : violations) {
                if (violation.getReport().getId().equals(reportId)) {
                    violation.setStatus(ViolationStatus.REMOVED);
                    sellerViolationRepository.save(violation);
                }
            }

            // Rollback side effects if requested
            if (Boolean.TRUE.equals(request.getRollbackActions())) {
                List<ReportAction> actions = reportActionRepository.findByReportIdOrderByCreatedAtDesc(reportId);
                if (!actions.isEmpty()) {
                    ReportAction lastAction = actions.get(0);
                    rollbackActionSideEffects(lastAction);
                }
            }
        } else {
            report.setStatus(ReportStatus.CLOSED);
        }

        Report savedReport = reportRepository.save(report);

        try {
            auditLogService.log("REPORT_APPEAL_REVIEWED", String.format("Admin quyết định xử lý khiếu nại #%d thành %s",
                    appealId, request.getDecision().name()));
        } catch (Exception ignored) {}

        return reportService.mapToDetailResponse(savedReport, admin, Roles.admin);
    }

    private void rollbackActionSideEffects(ReportAction action) {
        Vendor vendor = action.getReport().getVendor();
        Product product = action.getReport().getProduct();

        switch (action.getActionType()) {
            case HIDE_PRODUCT:
                if (product != null) {
                    product.setIsActive(true);
                    product.setStatus("active");
                    product.setRejectReason(null);
                    productRepository.save(product);
                }
                break;

            case TEMPORARY_SUSPENSION:
                vendor.setStatus("active");
                vendorRepository.save(vendor);
                break;

            case RESTRICT_LISTING:
                // Restrict listing is handled by deactivated violations, which we marked as REMOVED above.
                break;

            case DISABLE_PROMOTION:
                // Re-enabling promotions
                List<PostPromotion> disabledPromos = postPromotionRepository.findByVendorId(vendor.getId()).stream()
                        .filter(pp -> "PAUSED".equalsIgnoreCase(pp.getStatus()) && pp.getStopReason() != null && pp.getStopReason().contains("report action #" + action.getReport().getId()))
                        .toList();
                for (PostPromotion promo : disabledPromos) {
                    promo.setStatus("ACTIVE");
                    promo.setStoppedAt(null);
                    promo.setStopReason(null);
                    postPromotionRepository.save(promo);
                }
                break;

            default:
                break;
        }
    }

    // --- SELLER METHODS ---

    @Transactional(readOnly = true)
    public Page<ReportSummaryResponse> getSellerReports(String email, ReportStatus status, Pageable pageable) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản không liên kết với cửa hàng nào"));

        if (status != null) {
            return reportRepository.findByVendorIdAndStatus(vendor.getId(), status, pageable)
                    .map(reportService::mapToSummaryResponse);
        }
        return reportRepository.findByVendorId(vendor.getId(), pageable)
                .map(reportService::mapToSummaryResponse);
    }

    @Transactional(readOnly = true)
    public List<SellerViolationResponse> getSellerViolations(String email) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản không liên kết với cửa hàng nào"));

        return sellerViolationRepository.findByVendorId(vendor.getId()).stream()
                .map(v -> SellerViolationResponse.builder()
                        .id(v.getId())
                        .vendorId(v.getVendor().getId())
                        .violationType(v.getViolationType())
                        .severity(v.getSeverity())
                        .status(v.getStatus())
                        .createdAt(v.getCreatedAt())
                        .expiresAt(v.getExpiresAt())
                        .reportId(v.getReport().getId())
                        .build())
                .toList();
    }

    @Transactional
    public ReportDetailResponse submitAppeal(String email, Long reportId, SellerAppealRequest request) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản không liên kết với cửa hàng nào"));

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Báo cáo không tồn tại"));

        if (!report.getVendor().getId().equals(vendor.getId())) {
            throw new ForbiddenAccessException("Bạn không có quyền khiếu nại báo cáo này");
        }

        // BR-10: 7-day appeal deadline
        List<ReportAction> actions = reportActionRepository.findByReportIdOrderByCreatedAtDesc(reportId);
        if (actions.isEmpty()) {
            throw new BadRequestException("Báo cáo chưa được áp dụng hành động xử lý nào để khiếu nại");
        }

        ReportAction latestAction = actions.get(0);
        ZonedDateTime latestActionTime = latestAction.getCreatedAt();
        if (latestActionTime.plusDays(7).isBefore(ZonedDateTime.now())) {
            throw new BadRequestException("Đã quá hạn thời hạn khiếu nại (tối đa 7 ngày sau hành động gần nhất)");
        }

        // Ensure no other active appeals are open
        Optional<ReportAppeal> openAppeal = reportAppealRepository.findFirstByReportIdAndStatusIn(
                reportId, List.of(AppealStatus.SUBMITTED, AppealStatus.REVIEWING)
        );
        if (openAppeal.isPresent()) {
            throw new ConflictException("Đang có một khiếu nại đang chờ duyệt cho báo cáo này");
        }

        if (request.getAppealReason() == null || request.getAppealReason().trim().isEmpty()) {
            throw new BadRequestException("Lý do khiếu nại là bắt buộc");
        }
        if (request.getAppealReason().length() > 3000) {
            throw new BadRequestException("Lý do khiếu nại không được vượt quá 3000 ký tự");
        }

        ReportAppeal appeal = ReportAppeal.builder()
                .report(report)
                .vendor(vendor)
                .sellerProfileId(vendor.getProfile().getId())
                .appealReason(request.getAppealReason())
                .status(AppealStatus.SUBMITTED)
                .build();
        reportAppealRepository.save(appeal);

        report.setStatus(ReportStatus.APPEALED);
        Report savedReport = reportRepository.save(report);

        // Upload appeal evidences if any
        if (request.getEvidenceUrls() != null && !request.getEvidenceUrls().isEmpty()) {
            // Save them to ReportEvidence table associated with the report
            for (String url : request.getEvidenceUrls()) {
                ReportEvidence evidence = ReportEvidence.builder()
                        .report(report)
                        .fileUrl(url)
                        .fileName(url.substring(url.lastIndexOf("/") + 1))
                        .fileType(url.endsWith(".pdf") ? "PDF" : "IMAGE")
                        .mimeType(url.endsWith(".pdf") ? "application/pdf" : "image/png")
                        .fileSizeBytes(0L)
                        .build();
                reportEvidenceRepository.save(evidence);
            }
        }

        try {
            auditLogService.log("REPORT_APPEAL_SUBMITTED", String.format("Seller khiếu nại cho report #%d", reportId));
        } catch (Exception ignored) {}

        return reportService.mapToDetailResponse(savedReport, vendor.getProfile(), Roles.vendor);
    }
}
