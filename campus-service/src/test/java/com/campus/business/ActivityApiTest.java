package com.campus.business;

import com.campus.business.activity.*;
import com.campus.business.club.ClubRepository;
import com.campus.business.identity.*;
import com.campus.business.registration.*;
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

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:activity_test;DB_CLOSE_DELAY=-1", "campus.ai-base-url=http://127.0.0.1:1"})
@AutoConfigureMockMvc
class ActivityApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired ClubRepository clubs;
    @Autowired ActivityRepository activities;
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean RegistrationRepository registrations;
    MockHttpSession studentSession, secondSession, managerSession, outsiderSession;
    Account student;
    Long photo;
    @BeforeEach
    void setup() throws Exception {
        student = accounts.save(new Account("activity-" + UUID.randomUUID(), passwords.encode("TestPassword123!"), "活动测试学生", "计算机科学", "STUDENT"));
        Account second = accounts.save(new Account("activity-" + UUID.randomUUID(), passwords.encode("TestPassword123!"), "另一测试学生", "数字媒体", "STUDENT"));
        studentSession = login(student.getUsername(), "TestPassword123!"); secondSession = login(second.getUsername(), "TestPassword123!");
        managerSession = login("photo_manager", "CampusDemo123!"); outsiderSession = login("code_manager", "CampusDemo123!");
        photo = clubs.findBySlug("photo").orElseThrow().getId();
    }
    MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", username).param("password", password))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    Map<String, Object> draftBody(int capacity) {
        return Map.of("title", "手机摄影交流", "description", "练习校园构图", "location", "学生中心 204", "capacity", capacity,
                "startTime", LocalDateTime.now().plusDays(3).toString(), "registrationDeadline", LocalDateTime.now().plusDays(2).toString());
    }
    long draft(int capacity) throws Exception {
        MvcResult result = mvc.perform(post("/api/manage/clubs/" + photo + "/activities").session(managerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(draftBody(capacity))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    ResultActions publish(MockHttpSession session, long id) throws Exception {
        return mvc.perform(post("/api/manage/activities/" + id + "/publish").session(session).with(csrf()));
    }
    ResultActions signup(MockHttpSession session, long id) throws Exception {
        return mvc.perform(post("/api/activities/" + id + "/registrations").session(session).with(csrf()));
    }
    ResultActions cancel(MockHttpSession session, long id) throws Exception {
        return mvc.perform(post("/api/activities/" + id + "/registrations/cancel").session(session).with(csrf()));
    }
    @Test
    void draftPublishSignupCancelAndFreshLoginWithoutAi() throws Exception {
        long id = draft(2);
        mvc.perform(get("/api/activities/" + id)).andExpect(status().isNotFound());
        signup(studentSession, id).andExpect(status().isNotFound());
        publish(managerSession, id).andExpect(status().isOk()).andExpect(jsonPath("$.publishedAt").isNotEmpty());
        assertEquals(accounts.findByUsername("photo_manager").orElseThrow().getId(), activities.findById(id).orElseThrow().getCreatedBy());
        publish(managerSession, id).andExpect(status().isConflict());
        signup(studentSession, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REGISTERED"));
        signup(studentSession, id).andExpect(status().isConflict());
        mvc.perform(get("/api/activities/" + id)).andExpect(jsonPath("$.enrolled").value(1));
        MockHttpSession fresh = login(student.getUsername(), "TestPassword123!");
        mvc.perform(get("/api/registrations/mine").session(fresh)).andExpect(jsonPath("$[0].activityId").value(id));
        mvc.perform(get("/api/manage/activities/" + id + "/registrations").session(managerSession)).andExpect(jsonPath("$[0].name").value("活动测试学生"));
        cancel(secondSession, id).andExpect(status().isNotFound());
        cancel(fresh, id).andExpect(status().isOk()).andExpect(jsonPath("$.cancelledAt").isNotEmpty());
        cancel(fresh, id).andExpect(status().isConflict());
        mvc.perform(get("/api/activities/" + id)).andExpect(jsonPath("$.enrolled").value(0));
        signup(fresh, id).andExpect(status().isOk());
        assertEquals(1, registrations.findByUserIdOrderByIdDesc(student.getId()).size());
    }
    @Test
    void authenticationCsrfScopesAndNoParticipantLeak() throws Exception {
        long id = draft(2);
        mvc.perform(get("/api/registrations/mine")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/activities/" + id + "/registrations").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/activities/" + id + "/registrations").session(studentSession)).andExpect(status().isForbidden());
        publish(outsiderSession, id).andExpect(status().isForbidden());
        publish(studentSession, id).andExpect(status().isForbidden());
        mvc.perform(get("/api/manage/clubs/" + photo + "/activities").session(outsiderSession)).andExpect(status().isForbidden());
        publish(managerSession, id).andExpect(status().isOk());
        signup(managerSession, id).andExpect(status().isForbidden());
        signup(studentSession, id).andExpect(status().isOk());
        mvc.perform(get("/api/manage/activities/" + id + "/registrations").session(outsiderSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/manage/activities/" + id + "/registrations").session(studentSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/registrations/mine").session(secondSession)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/manage/clubs/" + photo + "/activities").session(outsiderSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(draftBody(1)))).andExpect(status().isForbidden());
    }
    @Test
    void validationFullCapacityAndReleasedSeat() throws Exception {
        for (Map<String, Object> invalid : List.of(new HashMap<>(draftBody(0)), new HashMap<>(draftBody(501)), new HashMap<>(draftBody(1)))) {
            if (invalid.get("capacity").equals(1)) invalid.put("title", " ");
            mvc.perform(post("/api/manage/clubs/" + photo + "/activities").session(managerSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        }
        Map<String, Object> wrongTime = new HashMap<>(draftBody(1));
        wrongTime.put("registrationDeadline", LocalDateTime.now().plusDays(4).toString());
        mvc.perform(post("/api/manage/clubs/" + photo + "/activities").session(managerSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(wrongTime))).andExpect(status().isBadRequest());
        long id = draft(1); publish(managerSession, id).andExpect(status().isOk());
        signup(studentSession, id).andExpect(status().isOk()); signup(secondSession, id).andExpect(status().isConflict());
        cancel(studentSession, id).andExpect(status().isOk()); signup(secondSession, id).andExpect(status().isOk());
        assertEquals(1, registrations.countByActivityIdAndStatus(id, "REGISTERED"));
    }
    @Test
    void deadlineStartedDraftAndMissingActivity() throws Exception {
        Activity expired = Activity.draft(photo, "截止活动", "说明", "教室", LocalDateTime.now().plusDays(1), LocalDateTime.now().minusHours(1), 1, 1L);
        expired = activities.saveAndFlush(expired);
        publish(managerSession, expired.getId()).andExpect(status().isBadRequest());
        expired.publish(); activities.saveAndFlush(expired);
        signup(studentSession, expired.getId()).andExpect(status().isConflict());
        Activity started = Activity.draft(photo, "已开始活动", "说明", "教室", LocalDateTime.now().minusHours(1), LocalDateTime.now().minusHours(2), 1, 1L);
        started.publish(); started = activities.saveAndFlush(started);
        registrations.saveAndFlush(new Registration(started.getId(), student.getId()));
        cancel(studentSession, started.getId()).andExpect(status().isConflict());
        signup(studentSession, 999999).andExpect(status().isNotFound());
    }
    @Test
    void failedRegistrationDoesNotOccupySeat() throws Exception {
        long id = draft(1); publish(managerSession, id).andExpect(status().isOk());
        doThrow(new DataIntegrityViolationException("simulated registration failure")).when(registrations).saveAndFlush(any(Registration.class));
        signup(studentSession, id).andExpect(status().isConflict());
        assertEquals(0, registrations.countByActivityIdAndStatus(id, "REGISTERED"));
    }
    @Test
    void twoStudentsRaceForLastSeat() throws Exception {
        long id = draft(1); publish(managerSession, id).andExpect(status().isOk());
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> { start.await(); return signup(studentSession, id).andReturn().getResponse().getStatus(); });
            Future<Integer> second = pool.submit(() -> { start.await(); return signup(secondSession, id).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertEquals(List.of(200, 409), java.util.stream.Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)).sorted().toList());
            assertEquals(1, registrations.countByActivityIdAndStatus(id, "REGISTERED"));
        } finally { pool.shutdownNow(); }
    }
    @Test
    void sameStudentConcurrentSignupIsNotDuplicated() throws Exception {
        long id = draft(2); publish(managerSession, id).andExpect(status().isOk());
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> action = () -> { start.await(); return signup(studentSession, id).andReturn().getResponse().getStatus(); };
            Future<Integer> first = pool.submit(action), second = pool.submit(action); start.countDown();
            assertEquals(List.of(200, 409), java.util.stream.Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)).sorted().toList());
            assertEquals(1, registrations.countByActivityIdAndStatus(id, "REGISTERED"));
        } finally { pool.shutdownNow(); }
    }
}
