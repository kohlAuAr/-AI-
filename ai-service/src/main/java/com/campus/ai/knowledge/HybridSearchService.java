package com.campus.ai.knowledge;

import com.campus.ai.client.ModelClient;
import com.campus.ai.config.AiSettings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Small-data adaptation of PaiSmart's keyword/vector retrieval and reference mapping. */
@Service
public class HybridSearchService {
    private final ChunkRepository chunks;
    private final ModelClient models;
    private final AiSettings settings;
    private final ObjectMapper mapper;

    public HybridSearchService(ChunkRepository chunks, ModelClient models, AiSettings settings, ObjectMapper mapper) {
        this.chunks = chunks;
        this.models = models;
        this.settings = settings;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String query) {
        List<KnowledgeChunk> candidates = chunks.findAll();
        boolean vectorSearch = settings.modelEnabled() && candidates.stream().anyMatch(this::compatible);
        double[] queryVector = vectorSearch ? models.embed(List.of(query)).get(0) : null;
        Set<String> terms = queryTerms(query);
        List<Citation> results = new ArrayList<>();
        for (KnowledgeChunk chunk : candidates) {
            double keywordScore = keywordScore(terms, chunk.getContent());
            double score = keywordScore;
            if (queryVector != null && compatible(chunk)) {
                double similarity = cosine(queryVector, vector(chunk));
                score = 0.75 * Math.max(0, similarity) + 0.25 * keywordScore;
                if (similarity < 0.35 && keywordScore < 0.2) continue;
            } else if (keywordScore < 0.2) continue;
            KnowledgeDocument doc = chunk.getDocument();
            results.add(new Citation(doc.getId(), doc.getName(), chunk.getChunkNumber(), chunk.getContent(), score));
        }
        List<Citation> ranked = results.stream().sorted(Comparator.comparingDouble(Citation::score).reversed()
                        .thenComparing(Citation::documentId).thenComparing(Citation::chunkNumber)).limit(4).toList();
        return new SearchResponse(ranked, vectorSearch ? "KEYWORD_VECTOR" : "KEYWORD");
    }

    private boolean compatible(KnowledgeChunk chunk) {
        return chunk.getVectorJson() != null && chunk.getDocument().getEmbeddingVersion().equals(models.embeddingVersion());
    }

    private double[] vector(KnowledgeChunk chunk) {
        try { return mapper.readValue(chunk.getVectorJson(), double[].class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("资料向量无法读取", e); }
    }

    static Set<String> queryTerms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        var english = Pattern.compile("[a-z0-9_]+").matcher(query.toLowerCase(Locale.ROOT));
        while (english.find()) terms.add(english.group());
        var chinese = Pattern.compile("[\\p{IsHan}]+").matcher(query);
        while (chinese.find()) {
            String word = chinese.group();
            if (word.length() == 1) terms.add(word);
            else for (int i = 0; i < word.length() - 1; i++) terms.add(word.substring(i, i + 2));
        }
        return terms;
    }

    private double keywordScore(Set<String> terms, String content) {
        if (terms.isEmpty()) return 0;
        String lower = content.toLowerCase(Locale.ROOT);
        return (double) terms.stream().filter(lower::contains).count() / terms.size();
    }

    static double cosine(double[] left, double[] right) {
        if (left.length != right.length || left.length == 0) return 0;
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        return leftNorm == 0 || rightNorm == 0 ? 0 : dot / Math.sqrt(leftNorm * rightNorm);
    }

    public record Citation(Long documentId, String documentName, int chunkNumber, String excerpt, double score) {}
    public record SearchResponse(List<Citation> references, String retrieval) {}
}
