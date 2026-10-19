package com.campus.ai.knowledge;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, unique = true, length = 64)
    private String fingerprint;
    @Lob @Column(nullable = false, columnDefinition = "longtext")
    private String content;
    @Column(nullable = false, length = 500)
    private String embeddingVersion;
    private int chunkCount;
    private Instant createdAt;
    private String visibility;
    private Long clubId;
    private Long uploadedBy;

    protected KnowledgeDocument() {}

    public KnowledgeDocument(String name, String fingerprint, String content, String embeddingVersion, int chunkCount) {
        this.name = name;
        this.fingerprint = fingerprint;
        this.content = content;
        this.embeddingVersion = embeddingVersion;
        this.chunkCount = chunkCount;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getContent() { return content; }
    public String getEmbeddingVersion() { return embeddingVersion; }
    public int getChunkCount() { return chunkCount; }
    public Instant getCreatedAt() { return createdAt; }
    public String getVisibility() { return visibility == null ? "PUBLIC" : visibility; }
    public Long getClubId() { return clubId; }
    public Long getUploadedBy() { return uploadedBy; }
    public void scope(String visibility, Long clubId, Long uploadedBy) { this.visibility = visibility; this.clubId = clubId; this.uploadedBy = uploadedBy; }
}
