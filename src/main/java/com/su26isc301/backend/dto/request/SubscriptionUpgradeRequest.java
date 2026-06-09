package com.su26isc301.backend.dto.request;

import lombok.Data;

@Data
public class SubscriptionUpgradeRequest {
    /**
     * 'plus' hoặc 'premium'
     */
    private String planType;

    /**
     * 'payos' hoặc 'bank_transfer'
     */
    private String paymentMethod;
}
