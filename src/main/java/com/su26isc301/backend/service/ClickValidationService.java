package com.su26isc301.backend.service;

import com.su26isc301.backend.entity.PostPromotion;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.repository.PromotionClickRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class ClickValidationService {

    private final PromotionClickRepository clickRepository;

    public String validateClick(PostPromotion promotion, String sessionId, Profile viewer) {
        if (viewer == null) {
            return "NOT_LOGGED_IN";
        }

        // Rule 1: Self-click
        if (viewer != null && promotion.getVendor().getProfile().getId().equals(viewer.getId())) {
            return "SELF_CLICK";
        }
        
        // Rule 2: Duplicate click from same sessionId within 24h
        ZonedDateTime twentyFourHoursAgo = ZonedDateTime.now().minusHours(24);
        boolean duplicate = clickRepository.existsByPromotionIdAndSessionIdAndClickedAtAfter(
                promotion.getId(), sessionId, twentyFourHoursAgo);
        if (duplicate) {
            return "DUPLICATE_CLICK";
        }
        
        // Rule 3: Missing session ID (bot prevention)
        if (sessionId == null || sessionId.isBlank()) {
            return "BOT_CLICK_NO_SESSION";
        }
        
        return "VALID"; // Valid click
    }
}
