package com.campus.ai.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConversationRepository extends JpaRepository<AiConversation, String> {
    List<AiConversation> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
