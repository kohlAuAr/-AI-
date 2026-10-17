package com.campus.ai;

import com.campus.ai.security.AiActor;
import java.util.Set;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@TestConfiguration
public class AiTestAuth {
    public static final AiActor ACTOR = new AiActor(1, "PLATFORM_ADMIN", Set.of(10L), Set.of());
    @Bean MockMvcBuilderCustomizer authenticatedInternalRequests() {
        return builder -> builder.defaultRequest(get("/").header("X-Internal-Token", "protocol-fixture-internal-token")
                .header("X-Actor-Id", "1").header("X-Actor-Role", "PLATFORM_ADMIN").header("X-Actor-Clubs", "10"));
    }
}
