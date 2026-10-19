package com.campus.business.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:account_security;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AccountSecurityApiTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordService passwordChanges;
    @Autowired PlatformTransactionManager transactions;
    Account account;
    String source;
    static final String OLD = "OldPassword123!", NEW = "NewPassword456!";

    @BeforeEach void setup() {
        String id = UUID.randomUUID().toString().replace("-", "");
        source = "test-" + id;
        account = accounts.saveAndFlush(new Account("auth_" + id, passwords.encode(OLD), "安全验收（虚构）", "软件工程", "STUDENT"));
    }
    MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request) {
        return request.with(r -> { r.setRemoteAddr(source); return r; });
    }
    MockHttpSession login(String password) throws Exception {
        return (MockHttpSession) mvc.perform(from(post("/api/auth/login")).with(csrf()).param("username", account.getUsername()).param("password", password))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    MockHttpServletRequestBuilder change(MockHttpSession session, String oldPassword, String newPassword) throws Exception {
        return from(post("/api/auth/password")).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("currentPassword", oldPassword, "newPassword", newPassword)));
    }

    @Test void changingPasswordRevokesAllOldSessionsAndOnlyNewPasswordWorks() throws Exception {
        MockHttpSession first = login(OLD), second = login(OLD);
        String result = mvc.perform(change(first, OLD, NEW)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("{\"status\":\"OK\"}", result);
        assertTrue(first.isInvalid());
        mvc.perform(from(get("/api/profile")).session(second)).andExpect(status().isUnauthorized());
        assertTrue(second.isInvalid());
        Account changed = accounts.findById(account.getId()).orElseThrow();
        assertEquals(1, changed.getCredentialVersion());
        assertNotEquals(NEW, changed.getPasswordHash()); assertTrue(passwords.matches(NEW, changed.getPasswordHash()));
        mvc.perform(from(post("/api/auth/login")).with(csrf()).param("username", account.getUsername()).param("password", OLD)).andExpect(status().isUnauthorized());
        mvc.perform(from(get("/api/profile")).session(login(NEW))).andExpect(status().isOk());
    }

    @Test void badOldPasswordOrInvalidNewPasswordPreservesPasswordAndBothSessions() throws Exception {
        MockHttpSession first = login(OLD), second = login(OLD);
        for (String invalid : new String[]{"short", OLD, "中".repeat(25)})
            mvc.perform(change(first, OLD, invalid)).andExpect(status().isBadRequest());
        mvc.perform(change(first, "WrongPassword", NEW)).andExpect(status().isBadRequest());
        assertEquals(0, accounts.findById(account.getId()).orElseThrow().getCredentialVersion());
        mvc.perform(from(get("/api/profile")).session(first)).andExpect(status().isOk());
        mvc.perform(from(get("/api/profile")).session(second)).andExpect(status().isOk());
        assertTrue(passwords.matches(OLD, accounts.findById(account.getId()).orElseThrow().getPasswordHash()));
    }

    @Test void passwordChangeRequiresLoginAndCsrfAndCannotTargetAnotherAccount() throws Exception {
        String payload = mapper.writeValueAsString(Map.of("currentPassword", OLD, "newPassword", NEW, "username", "student", "userId", 1));
        mvc.perform(from(post("/api/auth/password")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isUnauthorized());
        MockHttpSession session = login(OLD);
        mvc.perform(from(post("/api/auth/password")).session(session).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
        String otherHash = accounts.findByUsername("student").orElseThrow().getPasswordHash();
        mvc.perform(from(post("/api/auth/password")).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
        assertEquals(otherHash, accounts.findByUsername("student").orElseThrow().getPasswordHash());
        assertTrue(passwords.matches(NEW, accounts.findById(account.getId()).orElseThrow().getPasswordHash()));
    }

    @Test void fiveFailedLoginsBlockCorrectPasswordTooWithoutRevealingAccountExistence() throws Exception {
        for (int i = 0; i < 5; i++)
            mvc.perform(from(post("/api/auth/login")).with(csrf()).param("username", account.getUsername()).param("password", "wrong"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("账号或密码不正确"));
        mvc.perform(from(post("/api/auth/login")).with(csrf()).param("username", account.getUsername()).param("password", OLD).header("X-Forwarded-For", "203.0.113.5"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        mvc.perform(from(post("/api/auth/login")).with(csrf()).param("username", "missing_" + account.getUsername()).param("password", "wrong"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("账号或密码不正确"));
    }

    @Test void registrationRateLimitAlsoCoversInvalidBodies() throws Exception {
        for (int i = 0; i < 10; i++)
            mvc.perform(from(post("/api/auth/register")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        mvc.perform(from(post("/api/auth/register")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test void concurrentlySavingProfileCannotRestoreOldPasswordOrSessionVersion() throws Exception {
        CountDownLatch loaded = new CountDownLatch(1), changed = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> profile = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Account oldSnapshot = accounts.findById(account.getId()).orElseThrow();
                loaded.countDown();
                try { assertTrue(changed.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                oldSnapshot.updateProfile("修改后的姓名", "修改后的专业", "", "");
            }));
            assertTrue(loaded.await(10, TimeUnit.SECONDS));
            passwordChanges.change(account::getUsername, OLD, NEW);
            changed.countDown(); profile.get(10, TimeUnit.SECONDS);
            Account result = accounts.findById(account.getId()).orElseThrow();
            assertEquals("修改后的姓名", result.getName());
            assertTrue(passwords.matches(NEW, result.getPasswordHash())); assertEquals(1, result.getCredentialVersion());
        } finally { changed.countDown(); pool.shutdownNow(); }
    }
}
