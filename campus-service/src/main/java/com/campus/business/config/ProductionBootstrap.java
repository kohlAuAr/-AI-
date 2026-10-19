package com.campus.business.config;

import com.campus.business.identity.*;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit first-run administrator only. Never promote an existing account or reset passwords. */
@Component
@Profile("prod")
public class ProductionBootstrap implements CommandLineRunner {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final Environment environment;
    private final String username, password, token;
    public ProductionBootstrap(AccountRepository accounts, PasswordEncoder passwords, Environment environment,
            @Value("${campus.bootstrap-admin-username:campus_admin}") String username,
            @Value("${campus.bootstrap-admin-password:}") String password, @Value("${service.internal-token:}") String token) {
        this.accounts = accounts; this.passwords = passwords; this.environment = environment;
        this.username = username; this.password = password; this.token = token;
    }
    @Override @Transactional
    public void run(String... args) {
        if (environment.acceptsProfiles(Profiles.of("demo"))) throw new IllegalStateException("prod and demo cannot run together");
        if (token.length() < 32) throw new IllegalStateException("Production internal token must have at least 32 characters");
        if (!environment.getProperty("spring.datasource.url", "").startsWith("jdbc:mysql:")) throw new IllegalStateException("Production requires MySQL");
        if (!environment.getProperty("campus.redis-enabled", Boolean.class, false) || !environment.getProperty("campus.ai-daily-quota-enabled", Boolean.class, false)) throw new IllegalStateException("Production AI access controls must be enabled");
        for (String demo : new String[]{"student", "student2", "photo_manager", "code_manager", "platform_admin"}) {
            accounts.findByUsername(demo).ifPresent(account -> { if (!account.getRole().equals("STUDENT") && passwords.matches("CampusDemo123!", account.getPasswordHash())) throw new IllegalStateException("Known demo management credentials are forbidden in production"); });
        }
        if (accounts.existsByRole("PLATFORM_ADMIN")) return;
        if (accounts.count() != 0) throw new IllegalStateException("Nonempty database without administrator: explicit operator recovery required");
        if (!username.matches("[A-Za-z0-9_]{3,64}") || password.length() < 16 || password.getBytes(StandardCharsets.UTF_8).length > 72 || password.equals("CampusDemo123!")) throw new IllegalStateException("Provide a unique administrator username and a 16-72 byte password secret");
        accounts.saveAndFlush(new Account(username, passwords.encode(password), "平台管理员", "", "PLATFORM_ADMIN"));
    }
}
