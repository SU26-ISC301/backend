package com.su26isc301.backend.dto.request;

import com.su26isc301.backend.enums.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminReportStatusUpdateRequest {
    private ReportStatus status;
    private String adminNote;
}
