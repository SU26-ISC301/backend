package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId);

    long countByConversationIdAndSenderIdNotAndIsReadFalse(Long conversationId, UUID senderId);

    @Modifying
    @Query("""
            UPDATE Message message
            SET message.isRead = true
            WHERE message.conversation.id = :conversationId
              AND message.sender.id <> :vendorProfileId
              AND message.isRead = false
            """)
    int markCustomerMessagesAsRead(
            @Param("conversationId") Long conversationId,
            @Param("vendorProfileId") UUID vendorProfileId
    );

    @Modifying
    @Query("""
            UPDATE Message message
            SET message.isRead = true
            WHERE message.conversation.id = :conversationId
              AND message.sender.id <> :currentProfileId
              AND message.isRead = false
            """)
    int markOtherMessagesAsRead(
            @Param("conversationId") Long conversationId,
            @Param("currentProfileId") UUID currentProfileId
    );
}
