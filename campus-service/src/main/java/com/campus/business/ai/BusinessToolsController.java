package com.campus.business.ai;

import com.campus.business.activity.ActivityService;
import com.campus.business.club.*;
import com.campus.business.identity.AccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Deliberately read-only allowlist. Model output cannot select arbitrary paths or write methods. */
@RestController
@RequestMapping("/internal/ai/tools")
public class BusinessToolsController {
    private final ClubController clubs; private final ActivityService activities; private final AccountRepository accounts;
    public BusinessToolsController(ClubController clubs, ActivityService activities, AccountRepository accounts) { this.clubs = clubs; this.activities = activities; this.accounts = accounts; }
    @PostMapping
    public Object call(@Valid @RequestBody Input input) {
        var account = accounts.findById(input.actorId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号不可用"));
        String query = input.query() == null ? "" : input.query().trim().toLowerCase(Locale.ROOT);
        return switch (input.tool()) {
            case "search_clubs" -> Map.of("items", clubs.list().stream().filter(row -> matches(query, row.name() + " " + row.description() + " " + row.tags())).limit(10).toList(), "scope", "公开社团资料，最多 10 条");
            case "get_club" -> clubs.detail(requiredId(input.id()));
            case "search_activities" -> Map.of("items", activities.published().stream().filter(row -> matches(query, row.title() + " " + row.description() + " " + row.location())).limit(10).toList(), "scope", "已发布活动，最多 10 条，不表示均未截止");
            case "get_activity" -> activities.detail(requiredId(input.id()));
            case "my_registrations" -> Map.of("items", activities.mine(account::getUsername).stream().limit(20).toList(), "scope", "当前登录账号的最近 20 条报名记录");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的只读工具");
        };
    }
    private Long requiredId(Long id) { if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询详情需要有效 id"); return id; }
    private boolean matches(String query, String text) {
        if (query.isBlank()) return true;
        String lower = text.toLowerCase(Locale.ROOT); if (lower.contains(query)) return true;
        // Small-campus lexical lookup; the LLM may refine its query after an empty result.
        for (String term : query.split("[\\s，,。！？!?]+")) if (term.length() >= 2 && lower.contains(term)) return true;
        return false;
    }
    public record Input(@NotNull @Positive Long actorId, @NotBlank @Size(max = 40) String tool, @Size(max = 120) String query, Long id) {}
}
