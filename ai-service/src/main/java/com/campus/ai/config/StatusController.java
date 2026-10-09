package com.campus.ai.config;

import com.campus.ai.knowledge.DocumentRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class StatusController {
    private final AiSettings settings;
    private final DocumentRepository documents;

    public StatusController(AiSettings settings, DocumentRepository documents) {
        this.settings = settings;
        this.documents = documents;
    }

    @GetMapping("/internal/status")
    public Map<String, Object> status() {
        return Map.of("mode", settings.mode().name(), "knowledgeDocuments", documents.count(),
                "retrieval", settings.modelEnabled() && settings.embeddingEnabled() ? "KEYWORD_VECTOR_WHEN_INDEXED" : "KEYWORD",
                "recommendation", settings.embeddingEnabled() ? "SEMANTIC_CONFIGURED_NOT_HEALTH_CHECKED" : "EMBEDDING_NOT_CONFIGURED",
                "embeddingProvider", settings.effectiveEmbeddingProvider().name(),
                "sessionStore", settings.redisEnabled() ? "DATABASE_WITH_REDIS_CACHE" : "DATABASE",
                "scope", "PUBLIC_DEMO", "agent", "PLANNED");
    }
}
