package com.campus.business.ai;

import com.campus.business.club.*;
import com.campus.business.identity.ProfileService;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai/recommendations")
public class RecommendationController {
    private final ProfileService profiles;
    private final ClubRepository clubs;
    private final AiServiceClient ai;
    public RecommendationController(ProfileService profiles, ClubRepository clubs, AiServiceClient ai) {
        this.profiles = profiles; this.clubs = clubs; this.ai = ai;
    }

    @PostMapping
    public Result recommend(Principal principal) {
        String interest = profiles.mine(principal).interestDescription();
        if (interest.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先在个人资料中填写兴趣描述，再获取推荐");
        List<Club> candidates = clubs.findAllByOrderByIdAsc().stream().filter(Club::isRecruiting).toList();
        if (candidates.isEmpty()) return new Result("SEMANTIC_COSINE", List.of());
        // The browser cannot supply another account's interest or invent candidate clubs.
        var response = ai.recommend(Map.of("interest", interest, "clubs", candidates.stream()
                .map(club -> Map.of("id", club.getId(), "text", clubText(club))).toList()));
        if (response == null || !response.path("items").isArray())
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "推荐服务返回格式不正确");
        List<Item> items = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (var row : response.path("items")) {
            long id = row.path("clubId").asLong(-1);
            double score = row.path("score").asDouble(Double.NaN);
            if (!Double.isFinite(score) || score < -1 || score > 1 || !seen.add(id))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "推荐服务返回无效分数或重复社团");
            // Re-read current recruitment state after the model call, rather than trusting cached flags.
            Club source = candidates.stream().filter(c -> c.getId() == id).findFirst().orElse(null);
            Club club = source == null ? null : clubs.findById(id).orElse(null);
            if (club == null || !club.isRecruiting() || !clubText(source).equals(clubText(club))) continue;
            items.add(new Item(club.getId(), club.getSlug(), club.getName(), club.getDescription(), club.getRequirements(), club.getSchedule(), club.getPlace(), score));
            if (items.size() == 3) break;
        }
        return new Result("SEMANTIC_COSINE", items);
    }
    private String clubText(Club club) { return club.getName() + "\n" + club.getCategory() + "\n" + club.getDescription() + "\n" + club.getTags(); }
    public record Result(String method, List<Item> items) {}
    public record Item(Long clubId, String slug, String name, String description, String requirements, String schedule, String place, double score) {}
}
