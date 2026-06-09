package com.su26isc301.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Builder
public class SubscriptionStatusResponse {
    private String planType;        // 'free' | 'plus' | 'premium'
    private int totalSlots;         // -1 = unlimited
    private int usedSlots;
    private int remainingSlots;     // -1 = unlimited
    private ZonedDateTime startedAt;
    private ZonedDateTime expiresAt;
    private boolean isActive;
    private boolean canPost;        // true nếu còn quota
}
