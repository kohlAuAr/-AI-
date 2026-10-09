package com.campus.ai.recommendation;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HybridRankingTest {
    RecommendationController.Candidate club(long id, String text) { return new RecommendationController.Candidate(id, text); }
    @Test void bothInterestsCanOutrankSemanticOnlyCandidates() {
        var clubs = List.of(club(1, "公益志愿服务"), club(2, "草坪音乐交流"), club(3, "光影社\n摄影构图"), club(4, "创作社\n编程入门"));
        var result = RecommendationService.hybridRank("喜欢编程和摄影", clubs,
                List.of(new double[]{1, 0}, new double[]{1, 0}, new double[]{0.9, 0.1}, new double[]{0.5, 0.5}, new double[]{0.4, 0.6}));
        assertThat(result.subList(0, 2)).extracting(RecommendationService.Item::clubId).containsExactlyInAnyOrder(3L, 4L);
        assertThat(result.get(0).bm25Score()).isPositive();
        assertThat(result.get(0).fusionScore()).isLessThanOrEqualTo(2.0 / 61);
    }
    @Test void noLexicalHitsPreserveSemanticRankingWithoutFakeBm25Contribution() {
        var result = RecommendationService.hybridRank("镜头构图", List.of(club(2, "艺术交流"), club(1, "软件实践")),
                List.of(new double[]{1, 0}, new double[]{1, 0}, new double[]{0.5, 0.5}));
        assertThat(result).extracting(RecommendationService.Item::clubId).containsExactly(2L, 1L);
        assertThat(result).allSatisfy(item -> assertThat(item.bm25Score()).isZero());
        assertThat(result.get(0).fusionScore()).isEqualTo(1.0 / 61);
    }
    @Test void lexicalHitCanRecoverZeroSemanticResultButNotModelFailure() {
        var result = RecommendationService.hybridRank("编程", List.of(club(1, "一起学习编程"), club(2, "校园公益")),
                List.of(new double[]{1, 0}, new double[]{0, 1}, new double[]{-1, 0}));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isZero();
        assertThat(result.get(0).bm25Score()).isPositive();
    }
    @Test void tiesShareRanksAndCandidateOrderDoesNotAffectResults() {
        var vectors = List.of(new double[]{1, 0}, new double[]{1, 0}, new double[]{1, 0});
        var result = RecommendationService.hybridRank("编程", List.of(club(2, "编程学习"), club(1, "编程学习")), vectors);
        assertThat(result).extracting(RecommendationService.Item::clubId).containsExactly(1L, 2L);
        assertThat(result.get(0).fusionScore()).isEqualTo(2.0 / 61);
        assertThat(RecommendationService.hybridRank("编程", List.of(club(1, "编程学习"), club(2, "编程学习")), vectors)).isEqualTo(result);
    }
}
