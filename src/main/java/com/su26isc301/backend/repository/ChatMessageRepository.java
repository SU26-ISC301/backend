package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Lấy tất cả tin nhắn của user, sắp xếp theo thời gian
    List<ChatMessage> findByProfileIdOrderByCreatedAtAsc(UUID profileId);

    // Xóa tất cả tin nhắn của user
    void deleteByProfileId(UUID profileId);
}
