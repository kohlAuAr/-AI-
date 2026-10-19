package com.campus.ai.config;

import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionGuard implements CommandLineRunner {
    private final Environment environment;
    private final String token, masterKey;
    public ProductionGuard(Environment environment, @Value("${service.internal-token:}") String token, @Value("${ai-admin.master-key:}") String masterKey) {
        this.environment = environment; this.token = token; this.masterKey = masterKey;
    }
    @Override public void run(String... args) {
        if (environment.acceptsProfiles(Profiles.of("demo")) || token.length() < 32 || !environment.getProperty("spring.datasource.url", "").startsWith("jdbc:mysql:")) throw new IllegalStateException("Invalid production profile, MySQL or internal secret configuration");
        try { if (Base64.getDecoder().decode(masterKey).length == 32) return; }
        catch (IllegalArgumentException ignored) { }
        throw new IllegalStateException("Production requires an independent AES-256 master key");
    }
}
