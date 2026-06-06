package com.su26isc301.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketResearchResponse {
    private String dataMode;
    private ZonedDateTime updatedAt;
    private List<MarketCategoryOption> categories;
    private MarketCategoryInsight selectedCategory;
    private MarketOverview overview;
    private List<PriceSource> sources;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketCategoryOption {
        private String id;
        private String name;
        private Boolean selectable;
        private List<MarketCategoryOption> children;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketCategoryInsight {
        private String id;
        private String name;
        private String keyword;
        private Integer demand;
        private String trend;
        private BigDecimal recommendedPrice;
        private BigDecimal marketAverage;
        private Integer competitorCount;
        private Integer sampleCount;
        private String status;
        private String strategy;
        private List<String> categoryPath;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketOverview {
        private Integer totalSources;
        private String lowestPriceSource;
        private BigDecimal lowestPrice;
        private String highestTrustSource;
        private Integer highestTrust;
        private BigDecimal priceSpreadPercent;
        private BigDecimal averageTrust;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceSource {
        private String source;
        private BigDecimal min;
        private BigDecimal avg;
        private BigDecimal max;
        private String sales;
        private BigDecimal rating;
        private String promo;
        private Integer trust;
    }
}
