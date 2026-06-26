package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.ChatbotRequest;
import com.su26isc301.backend.dto.response.*;
import com.su26isc301.backend.service.BuyerChatbotService;
import com.su26isc301.backend.service.VendorChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final BuyerChatbotService buyerChatbotService;
    private final VendorChatbotService vendorChatbotService;

    /**
     * POST /api/chatbot/chat
     * Gửi tin nhắn đến AI chatbot. Tin nhắn tự động lưu vào lịch sử.
     * Tự động phân luồng:
     * - Vendor: Phân tích tài chính, hiệu suất quảng cáo, sản phẩm.
     * - Customer: Tìm kiếm, so sánh, gợi ý sản phẩm.
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatbotResponse>> chat(
            Authentication authentication,
            @RequestBody ChatbotRequest request
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Vui lòng đăng nhập để sử dụng chatbot"));
        }

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Tin nhắn không được để trống"));
        }

        String userEmail = authentication.getName();
        
        // Kiểm tra role của user từ token
        boolean isVendor = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("vendor") || a.getAuthority().equalsIgnoreCase("ROLE_VENDOR"));

        ChatbotResponse response;
        if (isVendor) {
            response = vendorChatbotService.chat(request, userEmail);
        } else {
            response = buyerChatbotService.chat(request, userEmail);
        }

        return ResponseEntity.ok(ApiResponse.success("OK", response));
    }

    /**
     * GET /api/chatbot/history
     * Lấy toàn bộ lịch sử chat của user. Dùng chung cho cả buyer và vendor.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getChatHistory(
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Vui lòng đăng nhập"));
        }

        String userEmail = authentication.getName();
        
        // Vì lịch sử lưu chung bảng profile_id, gọi service nào cũng giống nhau
        List<ChatMessageResponse> messages = buyerChatbotService.getChatHistory(userEmail);
        return ResponseEntity.ok(ApiResponse.success("OK", messages));
    }

    /**
     * DELETE /api/chatbot/history
     * Xóa toàn bộ lịch sử chat (bắt đầu cuộc trò chuyện mới). Dùng chung.
     */
    @DeleteMapping("/history")
    public ResponseEntity<ApiResponse<Void>> clearChatHistory(
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Vui lòng đăng nhập"));
        }

        String userEmail = authentication.getName();
        buyerChatbotService.clearChatHistory(userEmail);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa lịch sử chat", null));
    }
}
