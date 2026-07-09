package com.su26isc301.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportStatsResponse {
    private long newCount;
    private long reviewingCount;
    private long highPriorityCount;
    private long appealedCount;
    private long reportsLast7Days;
    private List<ReasonCount> topReasons;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReasonCount {
        private String reasonCode;
        private long count;
    }
}
