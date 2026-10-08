package com.campus.business.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiServiceClient ai;

    public AiController(AiServiceClient ai) { this.ai = ai; }

    @GetMapping("/status")
    public JsonNode status() { return ai.get("status"); }

    @GetMapping("/knowledge")
    public JsonNode knowledge() { return ai.get("knowledge"); }

    @GetMapping("/knowledge/{id}")
    public JsonNode document(@PathVariable Long id) { return ai.get("knowledge/" + id); }

    @PostMapping("/knowledge")
    public JsonNode upload(@RequestParam MultipartFile file) throws IOException {
        return ai.upload(file.getOriginalFilename(), file.getBytes());
    }

    @PostMapping("/chat")
    public JsonNode chat(@RequestBody Map<String, Object> body) { return ai.chat(body); }

    @GetMapping("/conversations/{id}")
    public JsonNode history(@PathVariable String id) {
        // Restrict the path component; never concatenate arbitrary user paths into the upstream URI.
        java.util.UUID.fromString(id);
        return ai.get("conversations/" + id);
    }
}
