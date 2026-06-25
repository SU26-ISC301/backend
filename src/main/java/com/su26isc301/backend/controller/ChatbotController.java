package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.ChatbotRequest;
import com.su26isc301.backend.dto.response.*;
import com.su26isc301.backend.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    /**
     * POST /api/chatbot/chat
     * Gửi tin nhắn đến AI chatbot. Tin nhắn tự động lưu vào lịch sử.
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
        ChatbotResponse response = chatbotService.chat(request, userEmail);
        return ResponseEntity.ok(ApiResponse.success("OK", response));
    }

    /**
     * GET /api/chatbot/history
     * Lấy toàn bộ lịch sử chat của user.
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
        List<ChatMessageResponse> messages = chatbotService.getChatHistory(userEmail);
        return ResponseEntity.ok(ApiResponse.success("OK", messages));
    }

    /**
     * DELETE /api/chatbot/history
     * Xóa toàn bộ lịch sử chat (bắt đầu cuộc trò chuyện mới).
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
        chatbotService.clearChatHistory(userEmail);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa lịch sử chat", null));
    }
}
