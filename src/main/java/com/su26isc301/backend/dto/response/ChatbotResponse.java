package com.su26isc301.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotResponse {
    private String reply;                                  // Phản hồi text của AI
    private List<ProductResponse> recommendedProducts;     // Danh sách sản phẩm gợi ý
}
