package com.campus.ai.chat;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "conversation_turn", indexes = @Index(columnList = "conversationId"))
public class ConversationTurn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 36)
    private String conversationId;
    @Lob @Column(nullable = false)
    private String question;
    @Lob @Column(nullable = false)
    private String answer;
    @Lob @Column(nullable = false)
    private String referencesJson;
    private String mode;
    private String retrieval;
    private Instant createdAt;

    protected ConversationTurn() {}

    public ConversationTurn(String conversationId, String question, String answer, String referencesJson, String mode, String retrieval) {
        this.conversationId = conversationId;
        this.question = question;
        this.answer = answer;
        this.referencesJson = referencesJson;
        this.mode = mode;
        this.retrieval = retrieval;
        this.createdAt = Instant.now();
    }

    public String getConversationId() { return conversationId; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public String getReferencesJson() { return referencesJson; }
    public String getMode() { return mode; }
    public String getRetrieval() { return retrieval; }
    public Instant getCreatedAt() { return createdAt; }
}
