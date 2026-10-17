package com.campus.ai.knowledge;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import com.campus.ai.security.AiActor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/internal/knowledge")
public class KnowledgeController {
    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) { this.knowledge = knowledge; }

    @GetMapping
    public List<KnowledgeService.DocumentView> list(@RequestAttribute AiActor actor) { return knowledge.list(actor); }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@RequestAttribute AiActor actor, @PathVariable Long id) { return knowledge.detail(id, actor); }

    @PostMapping
    public KnowledgeService.DocumentView upload(@RequestAttribute AiActor actor, @RequestParam MultipartFile file, @RequestParam(defaultValue = "PUBLIC") String visibility, @RequestParam(required = false) Long clubId) throws IOException {
        return knowledge.ingest(file.getOriginalFilename(), file.getBytes(), visibility, clubId, actor);
    }
}
