package com.campus.ai.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<ConversationTurn, Long> {
    List<ConversationTurn> findByConversationIdOrderByIdAsc(String conversationId);
    List<ConversationTurn> findTop6ByConversationIdOrderByIdDesc(String conversationId);
}
