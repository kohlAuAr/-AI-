package com.campus.business;

import com.campus.business.config.ProductionBootstrap;
import com.campus.business.identity.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionBootstrapTest {
    @Test void initializesOnlyEmptyDatabaseAndNeverResetsOrPromotesAccounts() {
        var accounts = mock(AccountRepository.class); var encoder = new BCryptPasswordEncoder();
        var env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:mysql://localhost/fixture")
                .withProperty("campus.redis-enabled", "true").withProperty("campus.ai-daily-quota-enabled", "true");
        var bootstrap = new ProductionBootstrap(accounts, encoder, env, "campus_admin", "synthetic-password-only-123", "x".repeat(32));
        bootstrap.run(); verify(accounts).saveAndFlush(argThat(a -> a.getRole().equals("PLATFORM_ADMIN") && encoder.matches("synthetic-password-only-123", a.getPasswordHash())));
        clearInvocations(accounts); when(accounts.count()).thenReturn(1L);
        assertThatThrownBy(bootstrap::run).hasMessageContaining("Nonempty"); verify(accounts, never()).saveAndFlush(any());
        when(accounts.existsByRole("PLATFORM_ADMIN")).thenReturn(true);
        bootstrap.run(); verify(accounts, never()).saveAndFlush(any());
        when(accounts.findByUsername("student")).thenReturn(java.util.Optional.of(new Account("student", encoder.encode("CampusDemo123!"), "注册同学", "", "STUDENT")));
        bootstrap.run(); // A public student choosing a demo-looking username cannot prevent production restarts.
        when(accounts.findByUsername("platform_admin")).thenReturn(java.util.Optional.of(new Account("platform_admin", encoder.encode("CampusDemo123!"), "演示管理员", "", "PLATFORM_ADMIN")));
        assertThatThrownBy(bootstrap::run).hasMessageContaining("demo management");
        env.setActiveProfiles("prod", "demo"); assertThatThrownBy(bootstrap::run).hasMessageContaining("demo");
    }
}
