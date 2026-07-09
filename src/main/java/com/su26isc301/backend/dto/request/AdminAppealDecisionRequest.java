package com.su26isc301.backend.dto.request;

import com.su26isc301.backend.enums.AppealStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminAppealDecisionRequest {
    private AppealStatus decision; // ACCEPTED or REJECTED
    private String reviewNote;
    private Boolean rollbackActions;
}
