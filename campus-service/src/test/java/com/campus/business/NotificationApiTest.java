package com.campus.business;

import com.campus.business.activity.*;
import com.campus.business.club.ClubRepository;
import com.campus.business.identity.*;
import com.campus.business.membership.MembershipRepository;
import com.campus.business.notification.*;
import com.campus.business.recruitment.*;
import com.campus.business.registration.RegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
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

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:notification_test;DB_CLOSE_DELAY=-1", "campus.ai-base-url=http://127.0.0.1:1"})
@AutoConfigureMockMvc
class NotificationApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired ClubRepository clubs;
    @Autowired ActivityRepository activities;
    @Autowired RegistrationRepository registrations;
    @Autowired ApplicationRepository applications;
    @Autowired MembershipRepository memberships;
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean NotificationRepository notifications;
    Account student, second;
    MockHttpSession studentSession, secondSession, managerSession;
    Long photo;
    @BeforeEach
    void setup() throws Exception {
        student = account(); second = account();
        studentSession = login(student.getUsername()); secondSession = login(second.getUsername());
        managerSession = login("photo_manager"); photo = clubs.findBySlug("photo").orElseThrow().getId();
    }
    Account account() { return accounts.save(new Account("notice-" + UUID.randomUUID(), passwords.encode("CampusDemo123!"), "通知测试学生", "计算机科学", "STUDENT")); }
    MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", username).param("password", "CampusDemo123!"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    ResultActions postAs(String path, MockHttpSession session) throws Exception { return mvc.perform(post(path).session(session).with(csrf())); }
    long activity(int capacity) {
        Activity a = Activity.draft(photo, "通知验证活动", "说明", "学生中心", LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(2), capacity, 1L);
        a.publish(); return activities.saveAndFlush(a).getId();
    }
    ResultActions review(long id, boolean approved) throws Exception {
        return mvc.perform(post("/api/manage/applications/" + id + "/review").session(managerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("approved", approved, "feedback", "通知测试意见"))));
    }
    long unread(Account account) { return notifications.countByUserIdAndReadAtIsNull(account.getId()); }
    @Test
    void acceptedTransitionsOnlyNotifyOwnerAndRetainEachRealRejoin() throws Exception {
        long id = activity(1);
        String signup = "/api/activities/" + id + "/registrations", cancel = signup + "/cancel";
        postAs(signup, studentSession).andExpect(status().isOk());
        postAs(signup, studentSession).andExpect(status().isConflict());
        postAs(signup, secondSession).andExpect(status().isConflict());
        assertEquals(1, unread(student)); assertEquals(0, unread(second));
        postAs(cancel, studentSession).andExpect(status().isOk());
        postAs(cancel, studentSession).andExpect(status().isConflict());
        postAs(signup, studentSession).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").session(login(student.getUsername())))
                .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.unread").value(3))
                .andExpect(jsonPath("$.items[0].type").value("ACTIVITY_REGISTERED"))
                .andExpect(jsonPath("$.items[1].type").value("ACTIVITY_CANCELLED"))
                .andExpect(jsonPath("$.items[0].targetPath").value("/activities/" + id));
        assertEquals(1, registrations.findByUserIdOrderByIdDesc(student.getId()).size());
    }
    @Test
    void bothReviewResultsNotifyApplicantOnce() throws Exception {
        ClubApplication approved = applications.saveAndFlush(new ClubApplication(student.getId(), photo, "申请"));
        ClubApplication rejected = applications.saveAndFlush(new ClubApplication(second.getId(), photo, "申请"));
        review(approved.getId(), true).andExpect(status().isOk());
        review(approved.getId(), false).andExpect(status().isConflict());
        review(rejected.getId(), false).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").session(studentSession)).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].type").value("APPLICATION_APPROVED")).andExpect(jsonPath("$.items[0].sourceId").value(approved.getId()));
        mvc.perform(get("/api/notifications").session(secondSession)).andExpect(jsonPath("$.items[0].type").value("APPLICATION_REJECTED"));
    }
    @Test
    void scopedPaginationReadAllIdempotenceAndCsrf() throws Exception {
        for (int i = 0; i < 23; i++) notifications.saveAndFlush(new Notification(student.getId(), "ACTIVITY_REGISTERED", 1L, "通知" + i, "正文", "/activities/1"));
        Notification other = notifications.saveAndFlush(new Notification(second.getId(), "ACTIVITY_REGISTERED", 2L, "其他账号", "正文", "/activities/2"));
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notifications/read-all").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notifications/read-all").session(studentSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications?page=-1").session(studentSession)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/notifications").session(studentSession)).andExpect(jsonPath("$.total").value(23))
                .andExpect(jsonPath("$.items.length()").value(20)).andExpect(jsonPath("$.hasMore").value(true));
        MvcResult page = mvc.perform(get("/api/notifications?page=1").session(studentSession)).andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.hasMore").value(false)).andReturn();
        long id = mapper.readTree(page.getResponse().getContentAsString()).get("items").get(0).get("id").asLong();
        postAs("/api/notifications/" + other.getId() + "/read", studentSession).andExpect(status().isNotFound());
        postAs("/api/notifications/999999/read", studentSession).andExpect(status().isNotFound());
        postAs("/api/notifications/" + id + "/read", studentSession).andExpect(status().isOk());
        var timestamp = notifications.findById(id).orElseThrow().getReadAt();
        postAs("/api/notifications/" + id + "/read", studentSession).andExpect(status().isOk());
        assertEquals(timestamp, notifications.findById(id).orElseThrow().getReadAt());
        postAs("/api/notifications/read-all", studentSession).andExpect(jsonPath("$.changed").value(22));
        postAs("/api/notifications/read-all", studentSession).andExpect(jsonPath("$.changed").value(0));
        assertEquals(0, unread(student)); assertEquals(1, unread(second));
    }
    @Test
    void notificationFailureRollsBackReviewMembershipAndSignup() throws Exception {
        long id = activity(1);
        ClubApplication application = applications.saveAndFlush(new ClubApplication(student.getId(), photo, "申请"));
        doThrow(new DataIntegrityViolationException("simulated notification failure")).when(notifications).saveAndFlush(any(Notification.class));
        review(application.getId(), true).andExpect(status().isConflict());
        assertEquals("pending", applications.findById(application.getId()).orElseThrow().getStatus());
        assertFalse(memberships.existsByUserIdAndClubId(student.getId(), photo));
        postAs("/api/activities/" + id + "/registrations", studentSession).andExpect(status().isConflict());
        assertEquals(0, registrations.countByActivityIdAndStatus(id, "REGISTERED"));
        assertEquals(0, unread(student));
    }
    @Test
    void notificationFailureDoesNotCancelExistingRegistration() throws Exception {
        long id = activity(1); String signup = "/api/activities/" + id + "/registrations";
        postAs(signup, studentSession).andExpect(status().isOk());
        doThrow(new DataIntegrityViolationException("simulated notification failure")).when(notifications).saveAndFlush(any(Notification.class));
        postAs(signup + "/cancel", studentSession).andExpect(status().isConflict());
        assertEquals("REGISTERED", registrations.findByActivityIdAndUserId(id, student.getId()).orElseThrow().getStatus());
        assertEquals(1, unread(student));
    }
    @Test
    void concurrentDuplicateSignupProducesOneNotice() throws Exception {
        long id = activity(2); ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> action = () -> { start.await(); return postAs("/api/activities/" + id + "/registrations", studentSession).andReturn().getResponse().getStatus(); };
            Future<Integer> first = pool.submit(action), second = pool.submit(action); start.countDown();
            assertEquals(List.of(200, 409), java.util.stream.Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)).sorted().toList());
            assertEquals(1, unread(student));
        } finally { pool.shutdownNow(); }
    }
}
