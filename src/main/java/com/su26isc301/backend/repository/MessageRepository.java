package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @EntityGraph(attributePaths = {"sender"})
    List<Message> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId);

    long countByConversationIdAndSenderIdNotAndIsReadFalse(Long conversationId, UUID senderId);

    @Query("""
           SELECT m.conversation.id, COUNT(m) 
           FROM Message m 
           WHERE m.conversation.id IN :conversationIds 
             AND m.sender.id <> :senderId 
             AND m.isRead = false 
           GROUP BY m.conversation.id
           """)
    List<Object[]> countUnreadMessagesByConversationIds(
            @Param("conversationIds") List<Long> conversationIds, 
            @Param("senderId") UUID senderId
    );

    @Query("""
           SELECT m 
           FROM Message m 
           WHERE m.id IN (
               SELECT MAX(msg.id) 
               FROM Message msg 
               WHERE msg.conversation.id IN :conversationIds 
               GROUP BY msg.conversation.id
           )
           """)
    List<Message> findLastMessagesByConversationIds(@Param("conversationIds") List<Long> conversationIds);

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
