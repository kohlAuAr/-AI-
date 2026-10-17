package com.campus.ai.chat;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_conversation_owner")
public class AiConversation {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false) private Long ownerId;
    private Instant createdAt;
    protected AiConversation() {}
    public AiConversation(String id, Long ownerId) { this.id = id; this.ownerId = ownerId; this.createdAt = Instant.now(); }
    public String getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public Instant getCreatedAt() { return createdAt; }
}
