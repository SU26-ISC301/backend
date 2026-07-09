package com.su26isc301.backend.dto.request;

import com.su26isc301.backend.enums.ReportReasonCode;
import com.su26isc301.backend.enums.ReportTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {
    private ReportTargetType targetType;
    private Long targetId;
    private ReportReasonCode reasonCode;
    private String description;
    private List<String> evidenceUrls; // Optional pre-uploaded URLs
    private Boolean confirmTruthful;
}
