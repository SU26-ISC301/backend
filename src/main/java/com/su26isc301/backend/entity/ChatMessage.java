package com.su26isc301.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_chat_messages_session_id", columnList = "chat_session_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id", nullable = false)
    private ChatSession chatSession;

    @Column(name = "role", nullable = false)
    private String role; // "user" hoặc "model"

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    // Lưu danh sách product IDs mà AI đã gợi ý (dạng JSON: "[1,2,3]")
    @Column(name = "recommended_product_ids")
    private String recommendedProductIds;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
