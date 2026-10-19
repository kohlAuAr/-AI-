package com.campus.ai.knowledge;

import com.campus.ai.client.ModelClient;
import com.campus.ai.config.AiSettings;
import com.campus.ai.security.AiActor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeService {
    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final TextChunker chunker;
    private final ModelClient models;
    private final AiSettings settings;
    private final ObjectMapper mapper;

    public KnowledgeService(DocumentRepository documents, ChunkRepository chunks, TextChunker chunker,
                            ModelClient models, AiSettings settings, ObjectMapper mapper) {
        this.documents = documents;
        this.chunks = chunks;
        this.chunker = chunker;
        this.models = models;
        this.settings = settings;
        this.mapper = mapper;
    }

    public List<DocumentView> list(AiActor actor) {
        return documents.findAll().stream().filter(actor::canRead).map(this::view).toList();
    }

    public Map<String, Object> detail(Long id, AiActor actor) {
        KnowledgeDocument doc = documents.findById(id).filter(actor::canRead).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在或无权访问"));
        return Map.of("document", view(doc), "content", doc.getContent());
    }
    public boolean visible(Long id, AiActor actor) { return documents.findById(id).filter(actor::canRead).isPresent(); }

    @Transactional
    public DocumentView ingest(String filename, byte[] bytes) {
        return ingestScoped(filename, bytes, "PUBLIC", null, null);
    }
    @Transactional
    public DocumentView ingest(String filename, byte[] bytes, String visibility, Long clubId, AiActor actor) {
        actor.requireUpload(visibility, clubId);
        return ingestScoped(filename, bytes, visibility, clubId, actor.userId());
    }
    private DocumentView ingestScoped(String filename, byte[] bytes, String visibility, Long clubId, Long uploader) {
        String name = filename == null ? "" : filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.length() > 128) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资料文件名最多 128 字符");
        String content = KnowledgeTextExtractor.extract(name, bytes);
        List<String> texts = chunker.split(content);
        if (texts.isEmpty() || texts.size() > 128) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资料必须有正文且分块数量不能超过 128");
        boolean vectorIndex = settings.modelEnabled() && settings.embeddingEnabled();
        String version = vectorIndex ? models.embeddingVersion() : "local-keyword";
        String fingerprint = fingerprint(content + "\0" + version + "\0" + visibility + ":" + clubId);
        Optional<KnowledgeDocument> existing = documents.findByFingerprint(fingerprint);
        if (existing.isPresent()) return view(existing.get());
        List<double[]> vectors = vectorIndex ? models.embed(texts) : List.of();
        KnowledgeDocument doc = documents.save(new KnowledgeDocument(name, fingerprint, content, version, texts.size()));
        doc.scope(visibility, clubId, uploader);
        List<KnowledgeChunk> savedChunks = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            savedChunks.add(new KnowledgeChunk(doc, i, texts.get(i), vectors.isEmpty() ? null : json(vectors.get(i))));
        }
        chunks.saveAll(savedChunks);
        return view(doc);
    }

    private String json(double[] vector) {
        try { return mapper.writeValueAsString(vector); }
        catch (JsonProcessingException e) { throw new IllegalStateException("向量序列化失败", e); }
    }

    private String fingerprint(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private DocumentView view(KnowledgeDocument doc) {
        return new DocumentView(doc.getId(), doc.getName(), doc.getChunkCount(), doc.getEmbeddingVersion(), doc.getCreatedAt(), doc.getVisibility(), doc.getClubId());
    }

    public record DocumentView(Long id, String name, int chunkCount, String embeddingVersion, Instant createdAt, String scope, Long clubId) {}
}
