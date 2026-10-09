package com.campus.ai.recommendation;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class Bm25Test {
    @Test void followsBm25TermFrequencyAndLengthFormula() {
        double[] scores = Bm25.scores("PYTHON", List.of("python python", "java"));
        double expected = Math.log(2) * 2 * 2.2 / (2 + 1.2 * (0.25 + 0.75 * 2 / 1.5));
        assertThat(scores[0]).isCloseTo(expected, within(1e-12));
        assertThat(scores[1]).isZero();
        assertThat(Bm25.scores("python python", List.of("python python", "java"))).containsExactly(scores);
    }
    @Test void chineseMultiInterestMatchesIntroductionsNotOnlyNames() {
        double[] scores = Bm25.scores("喜欢编程和摄影", List.of("星光社\n学习编程入门", "光影社\n交流摄影技巧", "山野社\n徒步露营"));
        assertThat(scores[0]).isPositive();
        assertThat(scores[1]).isPositive();
        assertThat(scores[2]).isZero();
    }
    @Test void noTermsNoHitsAndEmptyCorpusRemainFinite() {
        assertThat(Bm25.scores("编程", List.of())).isEmpty();
        assertThat(Bm25.scores("编程", List.of("...", "!!!"))).containsExactly(0, 0);
        assertThat(Bm25.scores("...", List.of("摄影", "编程"))).containsExactly(0, 0);
        assertThat(Bm25.scores("潜水", List.of("摄影", "编程"))).containsExactly(0, 0);
    }
}
