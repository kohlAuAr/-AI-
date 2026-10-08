package com.campus.ai.config;

import com.campus.ai.knowledge.KnowledgeService;
import java.io.IOException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class DemoKnowledge implements CommandLineRunner {
    private final KnowledgeService knowledge;
    private final AiSettings settings;

    public DemoKnowledge(KnowledgeService knowledge, AiSettings settings) {
        this.knowledge = knowledge;
        this.settings = settings;
    }

    @Override
    public void run(String... args) throws IOException {
        // Real provider mode never calls a paid API automatically during startup.
        if (settings.modelEnabled()) return;
        for (String name : new String[]{"programming-club.md", "activity-guide.md"}) {
            try (var stream = new ClassPathResource("samples/" + name).getInputStream()) {
                knowledge.ingest(name, stream.readAllBytes());
            }
        }
    }
}
