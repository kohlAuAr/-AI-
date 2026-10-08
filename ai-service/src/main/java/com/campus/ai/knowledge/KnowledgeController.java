package com.campus.ai.knowledge;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/internal/knowledge")
public class KnowledgeController {
    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) { this.knowledge = knowledge; }

    @GetMapping
    public List<KnowledgeService.DocumentView> list() { return knowledge.list(); }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) { return knowledge.detail(id); }

    @PostMapping
    public KnowledgeService.DocumentView upload(@RequestParam MultipartFile file) throws IOException {
        return knowledge.ingest(file.getOriginalFilename(), file.getBytes());
    }
}
