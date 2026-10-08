package com.campus.ai.recommendation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/recommendations")
public class RecommendationController {
    private final RecommendationService recommendations;
    public RecommendationController(RecommendationService recommendations) { this.recommendations = recommendations; }
    @PostMapping
    public RecommendationService.Result recommend(@Valid @RequestBody Request body) { return recommendations.recommend(body); }
    public record Request(@NotBlank @Size(max = 1000) String interest,
                          @NotNull List<@Valid Candidate> clubs) {}
    public record Candidate(@NotNull @Positive Long id, @NotBlank @Size(max = 4000) String text) {}
}
