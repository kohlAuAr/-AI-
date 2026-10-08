package com.campus.ai.recommendation;

import com.campus.ai.client.ModelClient;
import com.campus.ai.config.AiSettings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationService {
    private final ModelClient models;
    private final AiSettings settings;
    private final InterestVectorRepository cache;
    private final ObjectMapper mapper;
    public RecommendationService(ModelClient models, AiSettings settings, InterestVectorRepository cache, ObjectMapper mapper) {
        this.models = models; this.settings = settings; this.cache = cache; this.mapper = mapper;
    }

    public Result recommend(RecommendationController.Request body) {
        if (!settings.modelEnabled() || settings.embedding().model().isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "语义推荐尚未配置 Embedding 模型；个人资料保存和报名仍可使用");
        if (body.clubs().isEmpty()) return new Result("SEMANTIC_COSINE", List.of());
        List<String> texts = new ArrayList<>();
        texts.add(body.interest().trim());
        body.clubs().forEach(club -> texts.add(club.text().trim()));
        List<double[]> vectors = vectors(texts);
        List<Item> ranked = new ArrayList<>();
        for (int i = 0; i < body.clubs().size(); i++) {
            double score = cosine(vectors.get(0), vectors.get(i + 1));
            if (score > 0) ranked.add(new Item(body.clubs().get(i).id(), score));
        }
        return new Result("SEMANTIC_COSINE", ranked.stream().sorted(Comparator.comparingDouble(Item::score).reversed().thenComparing(Item::clubId)).toList());
    }

    // Small-campus cache writes are serialized; repository calls commit separately, never keep a DB transaction open over model HTTP.
    private synchronized List<double[]> vectors(List<String> texts) {
        Map<String, String> missing = new LinkedHashMap<>();
        Map<String, double[]> found = new HashMap<>();
        List<String> keys = texts.stream().map(this::key).toList();
        try {
            for (int i = 0; i < texts.size(); i++) {
                String key = keys.get(i);
                var stored = cache.findById(key);
                if (stored.isPresent()) found.put(key, mapper.readValue(stored.get().getVectorJson(), double[].class));
                else missing.put(key, texts.get(i));
            }
            if (!missing.isEmpty()) {
                List<double[]> generated = models.embed(new ArrayList<>(missing.values()));
                int i = 0;
                for (String key : missing.keySet()) found.put(key, generated.get(i++));
            }
            List<double[]> ordered = keys.stream().map(found::get).toList();
            int dimension = ordered.get(0).length;
            for (double[] vector : ordered) {
                if (dimension == 0 || vector.length != dimension || Arrays.stream(vector).anyMatch(v -> !Double.isFinite(v)) || Arrays.stream(vector).allMatch(v -> v == 0))
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "推荐向量维度或数值不正确，请检查 Embedding 模型版本");
            }
            for (String key : missing.keySet()) cache.save(new InterestVector(key, mapper.writeValueAsString(found.get(key))));
            return ordered;
        } catch (JsonProcessingException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "推荐向量无法读取或保存"); }
    }

    private String key(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("club-semantic-v1\n" + models.embeddingVersion() + "\n" + text).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    static double cosine(double[] a, double[] b) {
        double dot = 0, left = 0, right = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; left += a[i] * a[i]; right += b[i] * b[i]; }
        double score = dot / (Math.sqrt(left) * Math.sqrt(right));
        if (!Double.isFinite(score)) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "推荐相似度计算失败");
        return Math.max(-1, Math.min(1, score));
    }
    public record Item(Long clubId, double score) {}
    public record Result(String method, List<Item> items) {}
}
