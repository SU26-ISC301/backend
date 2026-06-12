package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.MarketResearchResponse;
import com.su26isc301.backend.service.MarketResearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-research")
@RequiredArgsConstructor
public class MarketResearchController {

    private final MarketResearchService marketResearchService;

    @GetMapping("/product")
    public ResponseEntity<ApiResponse<MarketResearchResponse>> getProductMarketResearch(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "categoryName", required = false) String categoryName
    ) {
        MarketResearchResponse response = marketResearchService.getPublicProductMarketResearch(query, categoryName);
        return ResponseEntity.ok(ApiResponse.success("Lấy khoảng giá thị trường thành công", response));
    }
}
