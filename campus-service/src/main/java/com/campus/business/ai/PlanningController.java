package com.campus.business.ai;

import com.campus.business.club.ClubController;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PlanningController {
    private final AiIdentity identity; private final ClubController clubs; private final AiServiceClient ai;
    public PlanningController(AiIdentity identity, ClubController clubs, AiServiceClient ai) { this.identity = identity; this.clubs = clubs; this.ai = ai; }
    @PostMapping("/api/ai/plans")
    public JsonNode plan(Principal user, @Valid @RequestBody Input input) {
        var actor = identity.current(user);
        if (!actor.role().equals("MANAGER") || !actor.managedClubs().contains(input.clubId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能策划自己负责社团的活动");
        var club = clubs.detail(input.clubId());
        return ai.ask("plans", Map.of("clubId", club.id(), "clubName", club.name(), "clubDescription", club.description(), "requirement", input.requirement()), actor);
    }
    public record Input(@NotNull @Positive Long clubId, @NotBlank @Size(max = 2000) String requirement) {}
}
