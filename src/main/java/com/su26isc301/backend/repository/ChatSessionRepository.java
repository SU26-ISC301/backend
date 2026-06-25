package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    // Lấy danh sách session của user, mới nhất lên trước
    List<ChatSession> findByProfileIdOrderByUpdatedAtDesc(UUID profileId);

    // Tìm session thuộc đúng user (đảm bảo không truy cập session người khác)
    Optional<ChatSession> findByIdAndProfileId(Long id, UUID profileId);
}
