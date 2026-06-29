package com.su26isc301.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReportResponse {
    private BigDecimal availableBalance;
    private BigDecimal lockedBalance;
    private BigDecimal totalDeposited;
    private BigDecimal totalSpent;
    private BigDecimal marketingSpent;
    private Long marketingClicks;
    private Long marketingImpressions;
    private BigDecimal averageRoiPerClick;
    private BigDecimal ctr;
    @Builder.Default
    private String currency = "VND";
    private List<DailyCashFlow> cashFlow;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCashFlow {
        private String date;
        private BigDecimal spend;
        private Long clicks;
        private Long impressions;
        private BigDecimal averageRoiPerClick;
        private BigDecimal ctr;
    }
}
