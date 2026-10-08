package com.campus.ai.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkRepository extends JpaRepository<KnowledgeChunk, Long> {}
