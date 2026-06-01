package com.su26isc301.backend.dto.response;

import java.time.ZonedDateTime;

public record BuyerConversationResponse(
        Long id,
        Long vendorId,
        String shopName,
        String shopLogoUrl,
        String lastMessage,
        ZonedDateTime lastMessageAt,
        long unreadCount
) {
}
