package com.campus.ai.recommendation;

import java.util.*;
import java.util.regex.Pattern;

/** Small-data BM25 (k1=1.2, b=0.75); Chinese bigrams and lowercase English tokens. */
final class Bm25 {
    private static final Pattern WORDS = Pattern.compile("[\\p{IsHan}]+|[a-z0-9_]+");
    private Bm25() {}

    static double[] scores(String query, List<String> texts) {
        List<Map<String, Integer>> documents = texts.stream().map(Bm25::terms).toList();
        double[] scores = new double[documents.size()];
        double averageLength = documents.stream().mapToInt(Bm25::length).average().orElse(0);
        if (averageLength == 0) return scores;
        for (String term : terms(query).keySet()) {
            long frequency = documents.stream().filter(doc -> doc.containsKey(term)).count();
            if (frequency == 0) continue;
            double idf = Math.log(1 + (documents.size() - frequency + 0.5) / (frequency + 0.5));
            for (int i = 0; i < documents.size(); i++) {
                Map<String, Integer> doc = documents.get(i);
                int tf = doc.getOrDefault(term, 0);
                scores[i] += idf * tf * 2.2 / (tf + 1.2 * (0.25 + 0.75 * length(doc) / averageLength));
            }
        }
        return scores;
    }

    private static int length(Map<String, Integer> terms) { return terms.values().stream().mapToInt(Integer::intValue).sum(); }

    private static Map<String, Integer> terms(String text) {
        Map<String, Integer> terms = new HashMap<>();
        var matcher = WORDS.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            int[] points = word.codePoints().toArray();
            if (Character.UnicodeScript.of(points[0]) != Character.UnicodeScript.HAN || points.length == 1) {
                terms.merge(word, 1, Integer::sum);
            } else {
                for (int i = 0; i < points.length - 1; i++) terms.merge(new String(points, i, 2), 1, Integer::sum);
            }
        }
        return terms;
    }
}
