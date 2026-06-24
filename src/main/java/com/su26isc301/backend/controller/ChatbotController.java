package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.ChatbotRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.ChatbotResponse;
import com.su26isc301.backend.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    /**
     * POST /api/chatbot/chat
     * Gửi tin nhắn đến AI chatbot và nhận phản hồi kèm sản phẩm gợi ý.
     * Yêu cầu người dùng phải đăng nhập.
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatbotResponse>> chat(
            Authentication authentication,
            @RequestBody ChatbotRequest request
    ) {
        // Kiểm tra đăng nhập (endpoint yêu cầu authenticated)
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Vui lòng đăng nhập để sử dụng chatbot"));
        }

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Tin nhắn không được để trống"));
        }

        ChatbotResponse response = chatbotService.chat(request);
        return ResponseEntity.ok(ApiResponse.success("OK", response));
    }
}
