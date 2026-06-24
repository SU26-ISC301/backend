package com.su26isc301.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotRequest {
    private String message;              // Tin nhắn của user
    private List<ChatMessage> history;   // Lịch sử chat (optional, giữ trong frontend)

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String role;    // "user" hoặc "model"
        private String content; // Nội dung tin nhắn
    }
}
