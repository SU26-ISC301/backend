package com.su26isc301.backend.dto.request;

import com.su26isc301.backend.enums.ReportActionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminReportActionRequest {
    private ReportActionType actionType;
    private String actionNote;
    private ZonedDateTime expiresAt;
}
