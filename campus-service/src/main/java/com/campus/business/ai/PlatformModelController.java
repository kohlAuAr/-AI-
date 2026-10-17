package com.campus.business.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/platform/models")
public class PlatformModelController {
    private final AiServiceClient ai;
    private final AiIdentity identity;
    public PlatformModelController(AiServiceClient ai, AiIdentity identity) { this.ai = ai; this.identity = identity; }
    @GetMapping public JsonNode view(Principal principal) { return ai.get("platform/models", identity.current(principal)); }
    @PostMapping("/discover") public JsonNode discover(Principal principal, @RequestBody Map<String, Object> body) { return ai.ask("platform/models/discover", body, identity.current(principal)); }
    @PostMapping("/{purpose}/{action}") public JsonNode operate(Principal principal, @PathVariable String purpose, @PathVariable String action, @RequestBody Map<String, Object> body) {
        if (!java.util.Set.of("CHAT", "EMBEDDING").contains(purpose) || !java.util.Set.of("save", "test", "activate").contains(action)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有该模型配置操作");
        return ai.ask("platform/models/" + purpose + "/" + action, body, identity.current(principal));
    }
}
