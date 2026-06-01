package com.su26isc301.backend.dto.response;

import java.time.ZonedDateTime;
import java.util.UUID;

public record VendorConversationResponse(
        Long id,
        UUID customerId,
        String customerName,
        String customerAvatarUrl,
        String lastMessage,
        ZonedDateTime lastMessageAt,
        long unreadCount
) {
}
