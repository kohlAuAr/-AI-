package com.campus.ai.knowledge;

import com.campus.ai.client.ModelClient;
import com.campus.ai.config.AiSettings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.*;
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

    public List<DocumentView> list() {
        return documents.findAll().stream().map(this::view).toList();
    }

    public Map<String, Object> detail(Long id) {
        KnowledgeDocument doc = documents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在"));
        return Map.of("document", view(doc), "content", doc.getContent());
    }

    @Transactional
    public DocumentView ingest(String filename, byte[] bytes) {
        String name = filename == null ? "" : filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        String lower = name.toLowerCase(Locale.ROOT);
        if (name.length() > 128 || !(lower.endsWith(".txt") || lower.endsWith(".md"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "骨架当前只支持 UTF-8 编码的 .txt 或 .md 资料，文件名最多 128 字符");
        }
        if (bytes.length == 0 || bytes.length > 128 * 1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件必须非空且不能超过 128KB");
        String content;
        try { content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资料不是有效的 UTF-8 文本"); }
        if (content.indexOf('\0') >= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资料包含非文本内容");
        List<String> texts = chunker.split(content);
        if (texts.isEmpty() || texts.size() > 128) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资料必须有正文且分块数量不能超过 128");
        boolean vectorIndex = settings.modelEnabled() && settings.embeddingEnabled();
        String version = vectorIndex ? models.embeddingVersion() : "local-keyword";
        String fingerprint = fingerprint(content + "\0" + version);
        Optional<KnowledgeDocument> existing = documents.findByFingerprint(fingerprint);
        if (existing.isPresent()) return view(existing.get());
        List<double[]> vectors = vectorIndex ? models.embed(texts) : List.of();
        KnowledgeDocument doc = documents.save(new KnowledgeDocument(name, fingerprint, content, version, texts.size()));
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
        return new DocumentView(doc.getId(), doc.getName(), doc.getChunkCount(), doc.getEmbeddingVersion(), doc.getCreatedAt(), "PUBLIC_DEMO");
    }

    public record DocumentView(Long id, String name, int chunkCount, String embeddingVersion, Instant createdAt, String scope) {}
}
