package com.su26isc301.backend.controller;

import com.su26isc301.backend.dto.request.SendMessageRequest;
import com.su26isc301.backend.dto.request.StartBuyerConversationRequest;
import com.su26isc301.backend.dto.response.ApiResponse;
import com.su26isc301.backend.dto.response.BuyerConversationResponse;
import com.su26isc301.backend.dto.response.BuyerMessageResponse;
import com.su26isc301.backend.dto.response.BuyerVendorResponse;
import com.su26isc301.backend.service.BuyerMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/buyers/messages")
@RequiredArgsConstructor
public class BuyerMessageController {

    private final BuyerMessageService buyerMessageService;

    @GetMapping("/vendors")
    public ResponseEntity<ApiResponse<List<BuyerVendorResponse>>> getVendors() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách shop thành công",
                buyerMessageService.getVendors()
        ));
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<BuyerConversationResponse>>> getConversations(
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách hội thoại thành công",
                buyerMessageService.getConversations(authentication.getName())
        ));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<BuyerConversationResponse>> startConversation(
            Authentication authentication,
            @RequestBody StartBuyerConversationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Mở hội thoại thành công",
                buyerMessageService.startConversation(authentication.getName(), request.getVendorId())
        ));
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<List<BuyerMessageResponse>>> getMessages(
            Authentication authentication,
            @PathVariable Long conversationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy tin nhắn thành công",
                buyerMessageService.getMessages(authentication.getName(), conversationId)
        ));
    }

    @PostMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<BuyerMessageResponse>> sendMessage(
            Authentication authentication,
            @PathVariable Long conversationId,
            @RequestBody SendMessageRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Gửi tin nhắn thành công",
                buyerMessageService.sendMessage(authentication.getName(), conversationId, request.getContent())
        ));
    }
}
