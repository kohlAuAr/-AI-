package com.campus.business;

import com.campus.business.club.*;
import com.campus.business.identity.*;
import com.campus.business.membership.*;
import com.campus.business.recruitment.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:recruitment_test;DB_CLOSE_DELAY=-1", "campus.ai-base-url=http://127.0.0.1:1"})
@AutoConfigureMockMvc
class RecruitmentApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired ClubRepository clubs;
    @Autowired ApplicationRepository applications;
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean MembershipRepository memberships;
    Account student;
    MockHttpSession studentSession;
    Long photo;

    @BeforeEach
    void setup() throws Exception {
        student = accounts.save(new Account("test-" + UUID.randomUUID(), passwords.encode("TestPassword123!"), "测试学生", "计算机科学", "STUDENT"));
        studentSession = login(student.getUsername(), "TestPassword123!");
        photo = clubs.findBySlug("photo").orElseThrow().getId();
    }
    MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf()).param("username", username).param("password", password))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
    long apply(Long clubId) throws Exception {
        MvcResult result = mvc.perform(post("/api/recruitment/applications").session(studentSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("clubId", clubId, "reason", "希望学习摄影"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.userId").value(student.getId())).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    ResultActions review(MockHttpSession session, long id, boolean approved) throws Exception {
        return mvc.perform(post("/api/manage/applications/" + id + "/review").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":" + approved + ",\"feedback\":\"欢迎加入\"}"));
    }
    @Test
    void applyReviewMembershipAndFreshLogin() throws Exception {
        long id = apply(photo);
        MockHttpSession manager = login("photo_manager", "CampusDemo123!");
        mvc.perform(get("/api/manage/clubs/" + photo + "/applications").session(manager)).andExpect(status().isOk());
        review(manager, id, true).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("approved"));
        assertTrue(memberships.existsByUserIdAndClubId(student.getId(), photo));
        assertNotNull(applications.findById(id).orElseThrow().getReviewedBy());
        mvc.perform(post("/api/auth/logout").session(studentSession).with(csrf())).andExpect(status().isOk());
        MockHttpSession fresh = login(student.getUsername(), "TestPassword123!");
        mvc.perform(get("/api/recruitment/my-applications").session(fresh)).andExpect(jsonPath("$[0].status").value("approved"));
        mvc.perform(get("/api/memberships/mine").session(fresh)).andExpect(jsonPath("$[0].clubId").value(photo));
        mvc.perform(get("/api/manage/clubs/" + photo + "/members").session(manager)).andExpect(status().isOk());
    }
    @Test
    void authenticationCsrfAndPasswordsAreEnforced() throws Exception {
        mvc.perform(get("/api/recruitment/my-applications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "student").param("password", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/recruitment/applications").session(studentSession).contentType(MediaType.APPLICATION_JSON).content("{\"clubId\":" + photo + ",\"reason\":\"摄影\"}")).andExpect(status().isForbidden());
        String sessionJson = mvc.perform(get("/api/auth/session").session(studentSession)).andExpect(jsonPath("$.user.name").value("测试学生")).andReturn().getResponse().getContentAsString();
        assertFalse(sessionJson.contains("password"));
        assertTrue(passwords.matches("TestPassword123!", accounts.findById(student.getId()).orElseThrow().getPasswordHash()));
        mvc.perform(post("/api/auth/logout").session(studentSession).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/memberships/mine")).andExpect(status().isUnauthorized());
    }
    @Test
    void duplicateAndAlreadyJoinedApplicationsAreBlocked() throws Exception {
        long id = apply(photo);
        mvc.perform(post("/api/recruitment/applications").session(studentSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clubId\":" + photo + ",\"reason\":\"再次申请\"}")).andExpect(status().isConflict());
        MockHttpSession manager = login("photo_manager", "CampusDemo123!");
        review(manager, id, true).andExpect(status().isOk());
        review(manager, id, false).andExpect(status().isConflict());
        mvc.perform(post("/api/recruitment/applications").session(studentSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clubId\":" + photo + ",\"reason\":\"重复入社\"}")).andExpect(status().isConflict());
    }
    @Test
    void wrongClubManagerAndStudentCannotReview() throws Exception {
        long id = apply(photo);
        MockHttpSession otherManager = login("code_manager", "CampusDemo123!");
        review(otherManager, id, true).andExpect(status().isForbidden());
        review(studentSession, id, true).andExpect(status().isForbidden());
        mvc.perform(get("/api/manage/clubs/" + photo + "/applications").session(otherManager)).andExpect(status().isForbidden());
        mvc.perform(get("/api/manage/clubs/" + photo + "/members").session(otherManager)).andExpect(status().isForbidden());
        assertEquals("pending", applications.findById(id).orElseThrow().getStatus());
        assertFalse(memberships.existsByUserIdAndClubId(student.getId(), photo));
    }
    @Test
    void validationClosedRecruitmentAndMissingRecords() throws Exception {
        for (String reason : List.of(" ", "x".repeat(501))) {
            mvc.perform(post("/api/recruitment/applications").session(studentSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("clubId", photo, "reason", reason)))).andExpect(status().isBadRequest());
        }
        Long closed = clubs.findBySlug("read").orElseThrow().getId();
        mvc.perform(post("/api/recruitment/applications").session(studentSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("clubId", closed, "reason", "喜欢阅读")))).andExpect(status().isConflict());
        mvc.perform(post("/api/recruitment/applications").session(studentSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clubId\":999999,\"reason\":\"摄影\"}")).andExpect(status().isNotFound());
        long id = apply(photo);
        MockHttpSession manager = login("photo_manager", "CampusDemo123!");
        mvc.perform(post("/api/manage/applications/" + id + "/review").session(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }
    @Test
    void rejectedAndWithdrawnApplicationsAllowNewHistoryRows() throws Exception {
        long first = apply(photo);
        review(login("photo_manager", "CampusDemo123!"), first, false).andExpect(status().isOk());
        assertFalse(memberships.existsByUserIdAndClubId(student.getId(), photo));
        long second = apply(photo);
        assertNotEquals(first, second);
        MockHttpSession other = login("student2", "CampusDemo123!");
        mvc.perform(post("/api/recruitment/applications/" + second + "/withdraw").session(other).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/recruitment/my-applications").session(other)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/recruitment/applications/" + second + "/withdraw").session(studentSession).with(csrf())).andExpect(status().isOk());
        long third = apply(photo);
        assertEquals(3, applications.findByUserIdOrderByIdDesc(student.getId()).size());
        assertNotEquals(second, third);
    }
    @Test
    void failedMemberWriteRollsBackApproval() throws Exception {
        long id = apply(photo);
        MockHttpSession manager = login("photo_manager", "CampusDemo123!");
        doThrow(new DataIntegrityViolationException("simulated member write failure")).when(memberships).saveAndFlush(any(Membership.class));
        review(manager, id, true).andExpect(status().isConflict());
        assertEquals("pending", applications.findById(id).orElseThrow().getStatus());
        assertFalse(memberships.existsByUserIdAndClubId(student.getId(), photo));
    }
    @Test
    void simultaneousApplicationsCreateOnlyOnePendingRecord() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> submit = () -> {
                start.await();
                return mvc.perform(post("/api/recruitment/applications").session(studentSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clubId\":" + photo + ",\"reason\":\"并发申请\"}")).andReturn().getResponse().getStatus();
            };
            Future<Integer> first = pool.submit(submit), second = pool.submit(submit);
            start.countDown();
            assertEquals(List.of(200, 409), java.util.stream.Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)).sorted().toList());
            assertEquals(1, applications.findByUserIdOrderByIdDesc(student.getId()).size());
        } finally { pool.shutdownNow(); }
    }
    @Test
    void simultaneousReviewsAllowOnlyOneDecision() throws Exception {
        long id = apply(photo);
        MockHttpSession manager = login("photo_manager", "CampusDemo123!");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> approval = pool.submit(() -> { start.await(); return review(manager, id, true).andReturn().getResponse().getStatus(); });
            Future<Integer> rejection = pool.submit(() -> { start.await(); return review(manager, id, false).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertEquals(List.of(200, 409), java.util.stream.Stream.of(approval.get(10, TimeUnit.SECONDS), rejection.get(10, TimeUnit.SECONDS)).sorted().toList());
            String status = applications.findById(id).orElseThrow().getStatus();
            assertEquals(status.equals("approved"), memberships.existsByUserIdAndClubId(student.getId(), photo));
        } finally { pool.shutdownNow(); }
    }
}
