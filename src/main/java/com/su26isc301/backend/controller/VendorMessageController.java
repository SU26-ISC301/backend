package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.SendMessageRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.VendorConversationResponse;
import com.su26isc301.backend.dto.response.VendorMessageResponse;
import com.su26isc301.backend.service.VendorMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vendors/messages")
@RequiredArgsConstructor
public class VendorMessageController {

    private final VendorMessageService vendorMessageService;

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<VendorConversationResponse>>> getConversations(
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách hội thoại thành công",
                vendorMessageService.getConversations(authentication.getName())
        ));
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<List<VendorMessageResponse>>> getMessages(
            Authentication authentication,
            @PathVariable Long conversationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy tin nhắn thành công",
                vendorMessageService.getMessages(authentication.getName(), conversationId)
        ));
    }

    @PostMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<VendorMessageResponse>> sendMessage(
            Authentication authentication,
            @PathVariable Long conversationId,
            @RequestBody SendMessageRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Gửi tin nhắn thành công",
                vendorMessageService.sendMessage(authentication.getName(), conversationId, request.getContent())
        ));
    }
}
