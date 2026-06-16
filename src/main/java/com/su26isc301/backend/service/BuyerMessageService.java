package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.response.BuyerConversationResponse;
import com.su26isc301.backend.dto.response.BuyerMessageResponse;
import com.su26isc301.backend.dto.response.BuyerVendorResponse;
import com.su26isc301.backend.entity.Conversation;
import com.su26isc301.backend.entity.Message;
import com.su26isc301.backend.entity.Profile;
import com.su26isc301.backend.entity.Vendor;
import com.su26isc301.backend.repository.ConversationRepository;
import com.su26isc301.backend.repository.MessageRepository;
import com.su26isc301.backend.repository.ProfileRepository;
import com.su26isc301.backend.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BuyerMessageService {

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final ProfileRepository profileRepository;
    private final VendorRepository vendorRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public List<BuyerVendorResponse> getVendors() {
        return vendorRepository.findAll()
                .stream()
                .filter(vendor -> "active".equalsIgnoreCase(vendor.getStatus()))
                .map(vendor -> new BuyerVendorResponse(
                        vendor.getId(),
                        vendor.getShopName(),
                        vendor.getLogoUrl(),
                        vendor.getDescription()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BuyerConversationResponse> getConversations(String email) {
        Profile buyer = getCurrentBuyer(email);
        List<Conversation> conversations = conversationRepository.findByCustomerIdOrderByUpdatedAtDesc(buyer.getId());
        if (conversations.isEmpty()) {
            return List.of();
        }

        List<Long> conversationIds = conversations.stream().map(Conversation::getId).toList();

        // 1. Fetch last messages in batch
        java.util.Map<Long, Message> lastMessageMap = new java.util.HashMap<>();
        List<Message> lastMessages = messageRepository.findLastMessagesByConversationIds(conversationIds);
        for (Message msg : lastMessages) {
            lastMessageMap.put(msg.getConversation().getId(), msg);
        }

        // 2. Fetch unread counts in batch
        java.util.Map<Long, Long> unreadCountMap = new java.util.HashMap<>();
        List<Object[]> unreadCounts = messageRepository.countUnreadMessagesByConversationIds(conversationIds, buyer.getId());
        for (Object[] row : unreadCounts) {
            Long convId = (Long) row[0];
            Long count = (Long) row[1];
            unreadCountMap.put(convId, count);
        }

        return conversations.stream()
                .map(conversation -> {
                    Vendor vendor = conversation.getVendor();
                    Message lastMessage = lastMessageMap.get(conversation.getId());
                    long unreadCount = unreadCountMap.getOrDefault(conversation.getId(), 0L);

                    return new BuyerConversationResponse(
                            conversation.getId(),
                            vendor.getId(),
                            vendor.getShopName(),
                            vendor.getLogoUrl(),
                            lastMessage == null ? "" : lastMessage.getContent(),
                            lastMessage == null ? conversation.getUpdatedAt() : lastMessage.getCreatedAt(),
                            unreadCount
                    );
                })
                .toList();
    }

    @Transactional
    public BuyerConversationResponse startConversation(String email, Long vendorId) {
        Profile buyer = getCurrentBuyer(email);
        if (vendorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn shop cần liên hệ");
        }
        Vendor vendor = vendorRepository.findById(vendorId)
                .filter(item -> "active".equalsIgnoreCase(item.getStatus()) || "approved".equalsIgnoreCase(item.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy shop"));

        Conversation conversation = conversationRepository.findByVendorIdAndCustomerId(vendorId, buyer.getId())
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .vendor(vendor)
                        .customer(buyer)
                        .build()));
        return toConversationResponse(conversation, buyer);
    }

    @Transactional
    public List<BuyerMessageResponse> getMessages(String email, Long conversationId) {
        Profile buyer = getCurrentBuyer(email);
        Conversation conversation = getBuyerConversation(buyer, conversationId);
        messageRepository.markOtherMessagesAsRead(conversationId, buyer.getId());

        return messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId)
                .stream()
                .map(message -> toMessageResponse(message, buyer))
                .toList();
    }

    @Transactional
    public BuyerMessageResponse sendMessage(String email, Long conversationId, String content) {
        Profile buyer = getCurrentBuyer(email);
        Conversation conversation = getBuyerConversation(buyer, conversationId);
        Message savedMessage = messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(buyer)
                .content(normalizeContent(content))
                .build());

        conversation.setUpdatedAt(ZonedDateTime.now());
        conversationRepository.save(conversation);
        return toMessageResponse(savedMessage, buyer);
    }

    private Profile getCurrentBuyer(String email) {
        return profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản Buyer"));
    }

    private Conversation getBuyerConversation(Profile buyer, Long conversationId) {
        return conversationRepository.findByIdAndCustomerId(conversationId, buyer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hội thoại"));
    }

    private BuyerConversationResponse toConversationResponse(Conversation conversation, Profile buyer) {
        Vendor vendor = conversation.getVendor();
        Message lastMessage = messageRepository
                .findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversation.getId())
                .orElse(null);
        return new BuyerConversationResponse(
                conversation.getId(),
                vendor.getId(),
                vendor.getShopName(),
                vendor.getLogoUrl(),
                lastMessage == null ? "" : lastMessage.getContent(),
                lastMessage == null ? conversation.getUpdatedAt() : lastMessage.getCreatedAt(),
                messageRepository.countByConversationIdAndSenderIdNotAndIsReadFalse(
                        conversation.getId(),
                        buyer.getId()
                )
        );
    }

    private BuyerMessageResponse toMessageResponse(Message message, Profile buyer) {
        Profile sender = message.getSender();
        return new BuyerMessageResponse(
                message.getId(),
                message.getConversation().getId(),
                sender.getId(),
                sender.getFullName(),
                message.getContent(),
                Boolean.TRUE.equals(message.getIsRead()),
                message.getCreatedAt(),
                sender.getId().equals(buyer.getId())
        );
    }

    private String normalizeContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung tin nhắn không được để trống");
        }
        String normalizedContent = content.trim();
        if (normalizedContent.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tin nhắn không được vượt quá 2000 ký tự");
        }
        return normalizedContent;
    }
}
