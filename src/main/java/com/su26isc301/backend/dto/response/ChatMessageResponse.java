package com.su26isc301.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private Long id;
    private String role;        // "user" hoặc "model"
    private String content;
    private List<ProductResponse> recommendedProducts; // Sản phẩm gợi ý (chỉ cho tin nhắn của model)
    private ZonedDateTime createdAt;
}
