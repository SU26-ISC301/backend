package com.su26isc301.backend.dto.response;

import com.su26isc301.backend.enums.ReportPriority;
import com.su26isc301.backend.enums.ReportReasonCode;
import com.su26isc301.backend.enums.ReportStatus;
import com.su26isc301.backend.enums.ReportTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSummaryResponse {
    private Long id;
    private UUID reporterUserId;
    private ReportTargetType targetType;
    private Long targetId;
    private Long vendorId;
    private String vendorShopName;
    private ReportReasonCode reasonCode;
    private String reasonLabel;
    private ReportStatus status;
    private ReportPriority priority;
    private ZonedDateTime createdAt;
}
