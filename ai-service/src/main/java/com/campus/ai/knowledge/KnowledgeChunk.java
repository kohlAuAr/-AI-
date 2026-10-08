package com.campus.ai.knowledge;

import jakarta.persistence.*;

@Entity
@Table(name = "knowledge_chunk", uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "chunk_number"}))
public class KnowledgeChunk {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;
    @Column(name = "chunk_number", nullable = false)
    private int chunkNumber;
    @Lob @Column(nullable = false)
    private String content;
    @Lob
    private String vectorJson;

    protected KnowledgeChunk() {}

    public KnowledgeChunk(KnowledgeDocument document, int chunkNumber, String content, String vectorJson) {
        this.document = document;
        this.chunkNumber = chunkNumber;
        this.content = content;
        this.vectorJson = vectorJson;
    }

    public KnowledgeDocument getDocument() { return document; }
    public int getChunkNumber() { return chunkNumber; }
    public String getContent() { return content; }
    public String getVectorJson() { return vectorJson; }
}
