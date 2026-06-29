package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.response.FinanceReportResponse;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.entity.VendorWallet;
import com.su26isc301.backend.repository.PromotionClickRepository;
import com.su26isc301.backend.repository.PromotionImpressionRepository;
import com.su26isc301.backend.repository.VendorRepository;
import com.su26isc301.backend.repository.VendorWalletRepository;
import com.su26isc301.backend.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceReportService {

    private final VendorRepository vendorRepository;
    private final VendorWalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final PromotionClickRepository clickRepository;
    private final PromotionImpressionRepository impressionRepository;

    @Transactional(readOnly = true)
    public FinanceReportResponse getFinanceReport(String email) {
        Vendor vendor = vendorRepository.findByProfileEmail(email)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        VendorWallet wallet = walletRepository.findByVendorId(vendor.getId())
                .orElse(null);

        BigDecimal availableBalance = wallet != null ? wallet.getAvailableBalance() : BigDecimal.ZERO;
        BigDecimal lockedBalance = wallet != null ? wallet.getLockedBalance() : BigDecimal.ZERO;

        BigDecimal totalDeposited = transactionRepository.sumTotalDeposited(vendor.getId());
        if (totalDeposited == null) totalDeposited = BigDecimal.ZERO;

        BigDecimal totalSpent = transactionRepository.sumTotalSpent(vendor.getId());
        if (totalSpent == null) {
            totalSpent = BigDecimal.ZERO;
        } else {
            totalSpent = totalSpent.abs(); // Return positive representation of spent amount
        }

        BigDecimal marketingSpent = clickRepository.sumMarketingSpent(vendor.getId());
        if (marketingSpent == null) marketingSpent = BigDecimal.ZERO;

        Long marketingClicks = clickRepository.countMarketingClicks(vendor.getId());
        if (marketingClicks == null) marketingClicks = 0L;

        Long marketingImpressions = impressionRepository.countMarketingImpressions(vendor.getId());
        if (marketingImpressions == null) marketingImpressions = 0L;

        BigDecimal ctr = BigDecimal.ZERO;
        if (marketingImpressions > 0) {
            ctr = BigDecimal.valueOf(marketingClicks).divide(BigDecimal.valueOf(marketingImpressions), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }

        BigDecimal averageRoiPerClick = BigDecimal.ZERO;
        if (marketingClicks > 0) {
            averageRoiPerClick = marketingSpent.divide(BigDecimal.valueOf(marketingClicks), 2, RoundingMode.HALF_UP);
        }

        // Daily stats
        List<PromotionClickRepository.DailyClickStats> dailyClicks = clickRepository.getDailyClickStats(vendor.getId());
        List<PromotionImpressionRepository.DailyImpressionStats> dailyImpressions = impressionRepository.getDailyImpressionStats(vendor.getId());

        Map<String, PromotionImpressionRepository.DailyImpressionStats> impressionMap = dailyImpressions.stream()
                .collect(Collectors.toMap(PromotionImpressionRepository.DailyImpressionStats::getDate, i -> i));

        List<FinanceReportResponse.DailyCashFlow> cashFlowList = new ArrayList<>();

        for (PromotionClickRepository.DailyClickStats clickStat : dailyClicks) {
            String date = clickStat.getDate();
            BigDecimal spend = clickStat.getSpend() != null ? clickStat.getSpend() : BigDecimal.ZERO;
            Long clicks = clickStat.getClicks() != null ? clickStat.getClicks() : 0L;

            Long impressions = 0L;
            PromotionImpressionRepository.DailyImpressionStats impStat = impressionMap.get(date);
            if (impStat != null && impStat.getImpressions() != null) {
                impressions = impStat.getImpressions();
            }

            BigDecimal dailyCtr = BigDecimal.ZERO;
            if (impressions > 0) {
                dailyCtr = BigDecimal.valueOf(clicks).divide(BigDecimal.valueOf(impressions), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            }

            BigDecimal dailyAvgRoi = BigDecimal.ZERO;
            if (clicks > 0) {
                dailyAvgRoi = spend.divide(BigDecimal.valueOf(clicks), 2, RoundingMode.HALF_UP);
            }

            cashFlowList.add(FinanceReportResponse.DailyCashFlow.builder()
                    .date(date)
                    .spend(spend)
                    .clicks(clicks)
                    .impressions(impressions)
                    .averageRoiPerClick(dailyAvgRoi)
                    .ctr(dailyCtr)
                    .build());
            
            impressionMap.remove(date);
        }

        // Add any remaining days that have impressions but no clicks
        for (PromotionImpressionRepository.DailyImpressionStats impStat : impressionMap.values()) {
            String date = impStat.getDate();
            Long impressions = impStat.getImpressions() != null ? impStat.getImpressions() : 0L;

            cashFlowList.add(FinanceReportResponse.DailyCashFlow.builder()
                    .date(date)
                    .spend(BigDecimal.ZERO)
                    .clicks(0L)
                    .impressions(impressions)
                    .averageRoiPerClick(BigDecimal.ZERO)
                    .ctr(BigDecimal.ZERO)
                    .build());
        }

        cashFlowList.sort(Comparator.comparing(FinanceReportResponse.DailyCashFlow::getDate));

        return FinanceReportResponse.builder()
                .availableBalance(availableBalance)
                .lockedBalance(lockedBalance)
                .totalDeposited(totalDeposited)
                .totalSpent(totalSpent)
                .marketingSpent(marketingSpent)
                .marketingClicks(marketingClicks)
                .marketingImpressions(marketingImpressions)
                .averageRoiPerClick(averageRoiPerClick)
                .ctr(ctr)
                .cashFlow(cashFlowList)
                .build();
    }
}
