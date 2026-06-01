package com.su26isc301.backend.dto.response;

import java.time.ZonedDateTime;
import java.util.UUID;

public record VendorMessageResponse(
        Long id,
        Long conversationId,
        UUID senderId,
        String senderName,
        String content,
        boolean read,
        ZonedDateTime createdAt,
        boolean sentByVendor
) {
}
