package com.su26isc301.backend.service;

import com.su26isc301.backend.dto.response.VendorConversationResponse;
import com.su26isc301.backend.dto.response.VendorMessageResponse;
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
public class VendorMessageService {

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final ProfileRepository profileRepository;
    private final VendorRepository vendorRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public List<VendorConversationResponse> getConversations(String email) {
        Vendor vendor = getCurrentVendor(email);
        List<Conversation> conversations = conversationRepository.findByVendorIdOrderByUpdatedAtDesc(vendor.getId());
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
        List<Object[]> unreadCounts = messageRepository.countUnreadMessagesByConversationIds(conversationIds, vendor.getProfile().getId());
        for (Object[] row : unreadCounts) {
            Long convId = (Long) row[0];
            Long count = (Long) row[1];
            unreadCountMap.put(convId, count);
        }

        return conversations.stream()
                .map(conversation -> {
                    Profile customer = conversation.getCustomer();
                    Message lastMessage = lastMessageMap.get(conversation.getId());
                    long unreadCount = unreadCountMap.getOrDefault(conversation.getId(), 0L);

                    return new VendorConversationResponse(
                            conversation.getId(),
                            customer.getId(),
                            customer.getFullName(),
                            customer.getAvatarUrl(),
                            lastMessage == null ? "" : lastMessage.getContent(),
                            lastMessage == null ? conversation.getUpdatedAt() : lastMessage.getCreatedAt(),
                            unreadCount
                    );
                })
                .toList();
    }

    @Transactional
    public List<VendorMessageResponse> getMessages(String email, Long conversationId) {
        Vendor vendor = getCurrentVendor(email);
        Conversation conversation = getVendorConversation(vendor, conversationId);
        messageRepository.markCustomerMessagesAsRead(conversationId, vendor.getProfile().getId());

        return messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId)
                .stream()
                .map(message -> toMessageResponse(message, vendor.getProfile()))
                .toList();
    }

    @Transactional
    public VendorMessageResponse sendMessage(String email, Long conversationId, String content) {
        Vendor vendor = getCurrentVendor(email);
        Conversation conversation = getVendorConversation(vendor, conversationId);
        String normalizedContent = normalizeContent(content);

        Message message = Message.builder()
                .conversation(conversation)
                .sender(vendor.getProfile())
                .content(normalizedContent)
                .build();
        Message savedMessage = messageRepository.save(message);

        conversation.setUpdatedAt(ZonedDateTime.now());
        conversationRepository.save(conversation);

        return toMessageResponse(savedMessage, vendor.getProfile());
    }

    private Vendor getCurrentVendor(String email) {
        Profile profile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản Vendor"));
        return vendorRepository.findByProfile(profile)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không phải Vendor"));
    }

    private Conversation getVendorConversation(Vendor vendor, Long conversationId) {
        return conversationRepository.findByIdAndVendorId(conversationId, vendor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hội thoại"));
    }

    private VendorConversationResponse toConversationResponse(Conversation conversation, Profile vendorProfile) {
        Profile customer = conversation.getCustomer();
        Message lastMessage = messageRepository
                .findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversation.getId())
                .orElse(null);

        return new VendorConversationResponse(
                conversation.getId(),
                customer.getId(),
                customer.getFullName(),
                customer.getAvatarUrl(),
                lastMessage == null ? "" : lastMessage.getContent(),
                lastMessage == null ? conversation.getUpdatedAt() : lastMessage.getCreatedAt(),
                messageRepository.countByConversationIdAndSenderIdNotAndIsReadFalse(
                        conversation.getId(),
                        vendorProfile.getId()
                )
        );
    }

    private VendorMessageResponse toMessageResponse(Message message, Profile vendorProfile) {
        Profile sender = message.getSender();
        return new VendorMessageResponse(
                message.getId(),
                message.getConversation().getId(),
                sender.getId(),
                sender.getFullName(),
                message.getContent(),
                Boolean.TRUE.equals(message.getIsRead()),
                message.getCreatedAt(),
                sender.getId().equals(vendorProfile.getId())
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
