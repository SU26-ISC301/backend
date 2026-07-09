package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.request.CreateReportRequest;
import com.su26isc301.backend.dto.response.ReportDetailResponse;
import com.su26isc301.backend.dto.response.ReportSummaryResponse;
import com.su26isc301.backend.entity.*;
import com.su26isc301.backend.enums.ReportPriority;
import com.su26isc301.backend.enums.ReportStatus;
import com.su26isc301.backend.enums.ReportTargetType;
import com.su26isc301.backend.enums.Roles;
import com.su26isc301.backend.exception.BadRequestException;
import com.su26isc301.backend.exception.ConflictException;
import com.su26isc301.backend.exception.ForbiddenAccessException;
import com.su26isc301.backend.exception.ResourceNotFoundException;
import com.su26isc301.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportEvidenceRepository reportEvidenceRepository;
    private final ReportActionRepository reportActionRepository;
    private final ReportAppealRepository reportAppealRepository;
    private final ReportMessageSnapshotRepository reportMessageSnapshotRepository;
    private final ProfileRepository profileRepository;
    private final VendorRepository vendorRepository;
    private final ProductRepository productRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ReportEvidenceStorageService storageService;
    private final ReportRiskService riskService;
    private final AuditLogService auditLogService;

    @Transactional
    public ReportDetailResponse createReport(String email, CreateReportRequest request, List<MultipartFile> files) {
        Profile reporter = profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản người báo cáo"));

        // BR-01: Confirm truthful
        if (request.getConfirmTruthful() == null || !request.getConfirmTruthful()) {
            throw new BadRequestException("Bạn phải xác nhận thông tin báo cáo là đúng sự thật");
        }

        // BR-04: Anti-duplicate 24h
        ZonedDateTime twentyFourHoursAgo = ZonedDateTime.now().minusDays(1);
        Optional<Report> duplicate = reportRepository
                .findFirstByReporterUserIdAndTargetTypeAndTargetIdAndReasonCodeAndCreatedAtAfter(
                        reporter.getId(),
                        request.getTargetType(),
                        request.getTargetId(),
                        request.getReasonCode(),
                        twentyFourHoursAgo
                );
        if (duplicate.isPresent()) {
            throw new ConflictException("Bạn đã báo cáo nội dung này với cùng lý do trong 24 giờ qua");
        }

        // Target validation & resolution
        Vendor targetVendor = null;
        Product targetProduct = null;
        Conversation targetConversation = null;

        if (request.getTargetType() == ReportTargetType.VENDOR) {
            targetVendor = vendorRepository.findById(request.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cửa hàng bị báo cáo không tồn tại"));
        } else if (request.getTargetType() == ReportTargetType.PRODUCT) {
            targetProduct = productRepository.findById(request.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm bị báo cáo không tồn tại"));
            if (!Boolean.TRUE.equals(targetProduct.getIsActive())) {
                throw new BadRequestException("Sản phẩm bị báo cáo không còn hoạt động");
            }
            targetVendor = targetProduct.getVendor();
        } else if (request.getTargetType() == ReportTargetType.CONVERSATION) {
            targetConversation = conversationRepository.findById(request.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cuộc hội thoại bị báo cáo không tồn tại"));

            // Verify reporter is part of conversation
            boolean isCustomer = targetConversation.getCustomer().getId().equals(reporter.getId());
            boolean isVendorOwner = targetConversation.getVendor().getProfile().getId().equals(reporter.getId());
            if (!isCustomer && !isVendorOwner) {
                throw new ForbiddenAccessException("Bạn không phải thành viên của cuộc trò chuyện này");
            }
            targetVendor = targetConversation.getVendor();
        } else {
            throw new BadRequestException("Loại target báo cáo không hợp lệ");
        }

        if (targetVendor == null) {
            throw new ResourceNotFoundException("Không tìm thấy cửa hàng liên quan đến đối tượng báo cáo");
        }

        // BR-02: Prevent self-reporting
        UUID targetSellerProfileId = targetVendor.getProfile().getId();
        if (targetSellerProfileId.equals(reporter.getId())) {
            throw new ForbiddenAccessException("Bạn không được tự báo cáo cửa hàng hoặc sản phẩm của chính mình");
        }

        // Description validation
        if (request.getDescription() != null && request.getDescription().length() > 2000) {
            throw new BadRequestException("Mô tả báo cáo không được vượt quá 2000 ký tự");
        }

        // Calculate Priority
        ReportPriority priority = riskService.calculatePriority(targetVendor.getId(), request.getReasonCode());

        // Build Report
        Report report = Report.builder()
                .reporterUserId(reporter.getId())
                .reporterEmailSnapshot(reporter.getEmail())
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .vendor(targetVendor)
                .sellerProfileId(targetSellerProfileId)
                .product(targetProduct)
                .conversation(targetConversation)
                .reasonCode(request.getReasonCode())
                .description(request.getDescription())
                .status(ReportStatus.NEW)
                .priority(priority)
                .build();

        Report savedReport = reportRepository.save(report);

        // Store evidences
        List<ReportEvidence> evidences = new ArrayList<>();
        // 1. Handle uploaded files
        if (files != null && !files.isEmpty()) {
            List<String> fileUrls = storageService.storeFiles(files);
            for (int i = 0; i < fileUrls.size(); i++) {
                MultipartFile f = files.get(i);
                String url = fileUrls.get(i);
                ReportEvidence evidence = ReportEvidence.builder()
                        .report(savedReport)
                        .fileUrl(url)
                        .fileName(f.getOriginalFilename())
                        .fileType(f.getContentType() != null && f.getContentType().contains("pdf") ? "PDF" : "IMAGE")
                        .mimeType(f.getContentType())
                        .fileSizeBytes(f.getSize())
                        .build();
                evidences.add(reportEvidenceRepository.save(evidence));
            }
        }
        // 2. Handle JSON evidence urls if provided
        if (request.getEvidenceUrls() != null && !request.getEvidenceUrls().isEmpty()) {
            for (String url : request.getEvidenceUrls()) {
                ReportEvidence evidence = ReportEvidence.builder()
                        .report(savedReport)
                        .fileUrl(url)
                        .fileName(url.substring(url.lastIndexOf("/") + 1))
                        .fileType(url.endsWith(".pdf") ? "PDF" : "IMAGE")
                        .mimeType(url.endsWith(".pdf") ? "application/pdf" : "image/png")
                        .fileSizeBytes(0L)
                        .build();
                evidences.add(reportEvidenceRepository.save(evidence));
            }
        }

        // Store chat snapshots if CONVERSATION
        List<ReportMessageSnapshot> messageSnapshots = new ArrayList<>();
        if (request.getTargetType() == ReportTargetType.CONVERSATION && targetConversation != null) {
            // Get 20 most recent messages
            List<Message> recentMessages = messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(targetConversation.getId());
            int size = recentMessages.size();
            List<Message> snapshotTargets = size > 20 ? recentMessages.subList(size - 20, size) : recentMessages;

            for (Message msg : snapshotTargets) {
                ReportMessageSnapshot snap = ReportMessageSnapshot.builder()
                        .report(savedReport)
                        .messageId(msg.getId())
                        .senderId(msg.getSender().getId())
                        .senderRole(msg.getSender().getRole().name())
                        .contentSnapshot(msg.getContent())
                        .messageCreatedAt(msg.getCreatedAt())
                        .build();
                messageSnapshots.add(reportMessageSnapshotRepository.save(snap));
            }
        }

        // Audit Log
        try {
            auditLogService.log("REPORT_CREATED", String.format("Tạo báo cáo vi phạm #%d cho target %s #%d",
                    savedReport.getId(), savedReport.getTargetType().name(), savedReport.getTargetId()));
        } catch (Exception e) {
            System.err.println("Lỗi ghi audit log: " + e.getMessage());
        }

        return mapToDetailResponse(savedReport, reporter, Roles.customer);
    }

    @Transactional(readOnly = true)
    public Page<ReportSummaryResponse> getMyReports(String email, Pageable pageable) {
        Profile reporter = profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản người dùng"));
        return reportRepository.findByReporterUserId(reporter.getId(), pageable)
                .map(this::mapToSummaryResponse);
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse getReportDetail(String email, Long id) {
        Profile user = profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản người dùng"));

        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Báo cáo không tồn tại"));

        // Determine user access and role
        boolean isAdmin = user.getRole() == Roles.admin;
        boolean isReporter = report.getReporterUserId().equals(user.getId());
        boolean isSellerOwner = report.getSellerProfileId() != null && report.getSellerProfileId().equals(user.getId());

        if (!isAdmin && !isReporter && !isSellerOwner) {
            throw new ForbiddenAccessException("Bạn không có quyền truy cập thông tin báo cáo này");
        }

        Roles viewRole = Roles.customer;
        if (isAdmin) {
            viewRole = Roles.admin;
        } else if (isSellerOwner) {
            viewRole = Roles.vendor;
        }

        return mapToDetailResponse(report, user, viewRole);
    }

    public ReportSummaryResponse mapToSummaryResponse(Report report) {
        return ReportSummaryResponse.builder()
                .id(report.getId())
                .reporterUserId(report.getReporterUserId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .vendorId(report.getVendor().getId())
                .vendorShopName(report.getVendor().getShopName())
                .reasonCode(report.getReasonCode())
                .reasonLabel(report.getReasonCode().getLabel())
                .status(report.getStatus())
                .priority(report.getPriority())
                .createdAt(report.getCreatedAt())
                .build();
    }

    public ReportDetailResponse mapToDetailResponse(Report report, Profile currentUser, Roles viewRole) {
        List<ReportEvidence> evidenceList = reportEvidenceRepository.findByReportId(report.getId());
        List<ReportAction> actionList = reportActionRepository.findByReportIdOrderByCreatedAtDesc(report.getId());
        List<ReportAppeal> appealList = reportAppealRepository.findByReportId(report.getId());
        List<ReportMessageSnapshot> messageList = reportMessageSnapshotRepository.findByReportIdOrderByMessageCreatedAtAsc(report.getId());

        ReportDetailResponse.ReportDetailResponseBuilder builder = ReportDetailResponse.builder()
                .id(report.getId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .vendor(ReportDetailResponse.VendorSummary.builder()
                        .id(report.getVendor().getId())
                        .shopName(report.getVendor().getShopName())
                        .logoUrl(report.getVendor().getLogoUrl())
                        .status(report.getVendor().getStatus())
                        .build())
                .sellerProfileId(report.getSellerProfileId())
                .reasonCode(report.getReasonCode())
                .reasonLabel(report.getReasonCode().getLabel())
                .description(report.getDescription())
                .status(report.getStatus())
                .priority(report.getPriority())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt());

        // Target info snapshots
        if (report.getProduct() != null) {
            builder.product(ReportDetailResponse.ProductSummary.builder()
                    .id(report.getProduct().getId())
                    .name(report.getProduct().getName())
                    .slug(report.getProduct().getSlug())
                    .status(report.getProduct().getStatus())
                    .isActive(report.getProduct().getIsActive())
                    .rejectReason(report.getProduct().getRejectReason())
                    .build());
        }

        if (report.getConversation() != null) {
            builder.conversation(ReportDetailResponse.ConversationSummary.builder()
                    .id(report.getConversation().getId())
                    .vendorId(report.getConversation().getVendor().getId())
                    .customerId(report.getConversation().getCustomer().getId())
                    .build());
        }

        // Evidences
        builder.evidences(evidenceList.stream().map(e -> ReportDetailResponse.EvidenceDetail.builder()
                .id(e.getId())
                .fileUrl(e.getFileUrl())
                .fileName(e.getFileName())
                .fileType(e.getFileType())
                .mimeType(e.getMimeType())
                .fileSizeBytes(e.getFileSizeBytes())
                .createdAt(e.getCreatedAt())
                .build()).toList());

        // Actions
        builder.actions(actionList.stream().map(a -> ReportDetailResponse.ActionDetail.builder()
                .id(a.getId())
                .actionType(a.getActionType())
                .actionNote(a.getActionNote())
                .actionByUserId(a.getActionByUserId())
                .actionByEmail(viewRole == Roles.admin ? a.getActionByEmail() : null) // Mask email for others
                .expiresAt(a.getExpiresAt())
                .createdAt(a.getCreatedAt())
                .build()).toList());

        // Appeals
        builder.appeals(appealList.stream().map(ap -> ReportDetailResponse.AppealDetail.builder()
                .id(ap.getId())
                .appealReason(ap.getAppealReason())
                .status(ap.getStatus())
                .reviewNote(viewRole == Roles.admin || viewRole == Roles.vendor ? ap.getReviewNote() : null) // Mask review note for customers
                .reviewedByUserId(ap.getReviewedByUserId())
                .createdAt(ap.getCreatedAt())
                .reviewedAt(ap.getReviewedAt())
                .build()).toList());

        // Message Snapshots
        builder.messages(messageList.stream().map(m -> ReportDetailResponse.MessageSnapshotDetail.builder()
                .id(m.getId())
                .messageId(m.getMessageId())
                .senderId(m.getSenderId())
                .senderRole(m.getSenderRole())
                .contentSnapshot(m.getContentSnapshot())
                .messageCreatedAt(m.getMessageCreatedAt())
                .build()).toList());

        // BR-07: Masking reporter data for seller
        if (viewRole == Roles.admin) {
            builder.reporterUserId(report.getReporterUserId());
            builder.reporterEmailSnapshot(report.getReporterEmailSnapshot());
            builder.adminNote(report.getAdminNote());
        } else if (viewRole == Roles.customer) {
            builder.reporterUserId(report.getReporterUserId());
            builder.reporterEmailSnapshot(report.getReporterEmailSnapshot());
            // Mask admin note for customer
            builder.adminNote(null);
        } else if (viewRole == Roles.vendor) {
            // Seller role: Mask everything about reporter
            builder.reporterUserId(null);
            builder.reporterEmailSnapshot(null);
            builder.adminNote(null);
        }

        return builder.build();
    }
}
