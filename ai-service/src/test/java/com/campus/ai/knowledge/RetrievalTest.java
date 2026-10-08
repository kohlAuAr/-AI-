package com.campus.ai.knowledge;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RetrievalTest {
    @Test
    void chunksCoverLongTextAndStayBounded() {
        String text = "摄影社欢迎新手。".repeat(200);
        var chunks = new TextChunker().split(text);
        assertThat(chunks).hasSizeGreaterThan(1).allMatch(chunk -> chunk.length() <= 682);
        assertThat(chunks.get(0)).startsWith("摄影社");
        assertThat(chunks.get(chunks.size() - 1)).endsWith("新手。");
    }

    @Test
    void cosineHandlesSimilarityZeroAndDifferentDimensions() {
        assertThat(HybridSearchService.cosine(new double[]{1, 0}, new double[]{1, 0})).isEqualTo(1);
        assertThat(HybridSearchService.cosine(new double[]{1, 0}, new double[]{0, 1})).isZero();
        assertThat(HybridSearchService.cosine(new double[]{0, 0}, new double[]{1, 0})).isZero();
        assertThat(HybridSearchService.cosine(new double[]{1}, new double[]{1, 0})).isZero();
    }
}
