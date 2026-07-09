package com.su26isc301.backend.dto.response;

import com.su26isc301.backend.enums.ReportPriority;
import com.su26isc301.backend.enums.ViolationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerViolationResponse {
    private Long id;
    private Long vendorId;
    private String violationType;
    private ReportPriority severity;
    private ViolationStatus status;
    private ZonedDateTime createdAt;
    private ZonedDateTime expiresAt;
    private Long reportId;
}
