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
        if (!settings.embeddingEnabled())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "语义推荐尚未配置 Embedding 模型；个人资料保存和报名仍可使用");
        if (body.clubs().isEmpty()) return new Result("HYBRID_BM25_VECTOR_RRF", List.of());
        List<String> texts = new ArrayList<>();
        texts.add(body.interest().trim());
        body.clubs().forEach(club -> texts.add(club.text().trim()));
        List<double[]> vectors = vectors(texts);
        return new Result("HYBRID_BM25_VECTOR_RRF", hybridRank(body.interest(), body.clubs(), vectors));
    }

    static List<Item> hybridRank(String interest, List<RecommendationController.Candidate> clubs, List<double[]> vectors) {
        double[] lexical = Bm25.scores(interest, clubs.stream().map(RecommendationController.Candidate::text).toList());
        double[] semantic = new double[clubs.size()];
        for (int i = 0; i < clubs.size(); i++) semantic[i] = cosine(vectors.get(0), vectors.get(i + 1));
        double[] vectorRanks = reciprocalRanks(semantic);
        double[] lexicalRanks = reciprocalRanks(lexical);
        List<Item> ranked = new ArrayList<>();
        for (int i = 0; i < clubs.size(); i++) {
            double fusion = vectorRanks[i] + lexicalRanks[i];
            if (fusion > 0) ranked.add(new Item(clubs.get(i).id(), semantic[i], lexical[i], fusion));
        }
        return ranked.stream().sorted(Comparator.comparingDouble(Item::fusionScore).reversed()
                .thenComparing(Comparator.comparingDouble(Item::bm25Score).reversed())
                .thenComparing(Comparator.comparingDouble(Item::score).reversed()).thenComparing(Item::clubId)).toList();
    }

    // Zero/negative results are absent from that ranking; ties share a competition rank.
    private static double[] reciprocalRanks(double[] scores) {
        List<Integer> order = java.util.stream.IntStream.range(0, scores.length).boxed().filter(i -> scores[i] > 0)
                .sorted(Comparator.<Integer>comparingDouble(i -> scores[i]).reversed()).toList();
        double[] result = new double[scores.length];
        int rank = 0;
        for (int position = 0; position < order.size(); position++) {
            int index = order.get(position);
            if (position == 0 || Double.compare(scores[index], scores[order.get(position - 1)]) != 0) rank = position + 1;
            result[index] = 1.0 / (60 + rank);
        }
        return result;
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
    public record Item(Long clubId, double score, double bm25Score, double fusionScore) {}
    public record Result(String method, List<Item> items) {}
}
