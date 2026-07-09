package com.su26isc301.backend.dto.response;

import com.su26isc301.backend.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDetailResponse {
    private Long id;
    private UUID reporterUserId;
    private String reporterEmailSnapshot;
    private ReportTargetType targetType;
    private Long targetId;
    private VendorSummary vendor;
    private UUID sellerProfileId;
    private ProductSummary product;
    private ConversationSummary conversation;
    private ReportReasonCode reasonCode;
    private String reasonLabel;
    private String description;
    private ReportStatus status;
    private ReportPriority priority;
    private String adminNote;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private List<EvidenceDetail> evidences;
    private List<ActionDetail> actions;
    private List<AppealDetail> appeals;
    private List<MessageSnapshotDetail> messages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VendorSummary {
        private Long id;
        private String shopName;
        private String logoUrl;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSummary {
        private Long id;
        private String name;
        private String slug;
        private String status;
        private Boolean isActive;
        private String rejectReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationSummary {
        private Long id;
        private Long vendorId;
        private UUID customerId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceDetail {
        private Long id;
        private String fileUrl;
        private String fileName;
        private String fileType;
        private String mimeType;
        private Long fileSizeBytes;
        private ZonedDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionDetail {
        private Long id;
        private ReportActionType actionType;
        private String actionNote;
        private UUID actionByUserId;
        private String actionByEmail;
        private ZonedDateTime expiresAt;
        private ZonedDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppealDetail {
        private Long id;
        private String appealReason;
        private AppealStatus status;
        private String reviewNote;
        private UUID reviewedByUserId;
        private ZonedDateTime createdAt;
        private ZonedDateTime reviewedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageSnapshotDetail {
        private Long id;
        private Long messageId;
        private UUID senderId;
        private String senderRole;
        private String contentSnapshot;
        private ZonedDateTime messageCreatedAt;
    }
}
