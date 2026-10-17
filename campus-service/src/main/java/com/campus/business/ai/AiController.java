package com.campus.business.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiServiceClient ai;
    private final AiIdentity identity;

    public AiController(AiServiceClient ai, AiIdentity identity) { this.ai = ai; this.identity = identity; }

    @GetMapping("/status")
    public JsonNode status() { return ai.get("status"); }

    @GetMapping("/knowledge")
    public JsonNode knowledge(Principal user) { return ai.get("knowledge", identity.current(user)); }

    @GetMapping("/knowledge/{id}")
    public JsonNode document(Principal user, @PathVariable Long id) { return ai.get("knowledge/" + id, identity.current(user)); }

    @PostMapping("/knowledge")
    public JsonNode upload(Principal user, @RequestParam MultipartFile file, @RequestParam(defaultValue = "PUBLIC") String visibility, @RequestParam(required = false) Long clubId) throws IOException {
        if (file.getSize() > 128 * 1024) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "资料文件不能超过 128KB");
        var actor = identity.current(user);
        boolean allowed = visibility.equals("PUBLIC") ? actor.role().equals("PLATFORM_ADMIN") && clubId == null
                : visibility.equals("CLUB") && actor.role().equals("MANAGER") && clubId != null && actor.managedClubs().contains(clubId);
        if (!allowed) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "仅平台管理员可上传公开资料，社团资料由所属负责人上传");
        return ai.upload(file.getOriginalFilename(), file.getBytes(), visibility, clubId, actor);
    }

    @PostMapping("/chat")
    public JsonNode chat(Principal user, @RequestBody Map<String, Object> body) { return ai.ask("chat", body, identity.current(user)); }

    @GetMapping("/conversations/{id}")
    public JsonNode history(Principal user, @PathVariable String id) {
        // Restrict the path component; never concatenate arbitrary user paths into the upstream URI.
        java.util.UUID.fromString(id);
        return ai.get("conversations/" + id, identity.current(user));
    }
    @PostMapping("/assistant")
    public JsonNode assistant(Principal user, @RequestBody Map<String, Object> body) { return ai.ask("assistant", body, identity.current(user)); }
    @GetMapping("/conversations")
    public JsonNode sessions(Principal user) { return ai.get("conversations", identity.current(user)); }
}
