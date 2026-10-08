package com.campus.business;

import com.campus.business.activity.*;
import com.campus.business.club.*;
import com.campus.business.finance.*;
import com.campus.business.identity.*;
import com.campus.business.membership.*;
import com.campus.business.notification.*;
import com.campus.business.registration.*;
import com.fasterxml.jackson.databind.*;
import java.time.*;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:business_completion;DB_CLOSE_DELAY=-1", "campus.ai-base-url=http://127.0.0.1:1"})
@AutoConfigureMockMvc
class BusinessCompletionApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired ClubRepository clubs;
    @Autowired ActivityRepository activities;
    @Autowired RegistrationRepository registrations;
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean MembershipRepository memberships;
    @MockitoSpyBean NotificationRepository notifications;
    @MockitoSpyBean ExpenseRepository expenses;
    Account student, second;
    MockHttpSession studentSession, secondSession, managerSession, outsiderSession;
    Long clubId;
    @BeforeEach
    void setup() throws Exception {
        String hash = accounts.findByUsername("student").orElseThrow().getPasswordHash();
        student = accounts.save(new Account("backend-" + UUID.randomUUID(), hash, "后端测试学生", "计算机科学", "STUDENT"));
        second = accounts.save(new Account("backend-" + UUID.randomUUID(), hash, "另一测试学生", "数字媒体", "STUDENT"));
        studentSession = login(student.getUsername()); secondSession = login(second.getUsername());
        managerSession = login("photo_manager"); outsiderSession = login("code_manager");
        clubId = json(send(post("/api/manage/clubs"), managerSession, clubBody())).get("id").asLong();
    }
    MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", username).param("password", "CampusDemo123!"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    ResultActions send(MockHttpServletRequestBuilder request, MockHttpSession session, Object body) throws Exception {
        if (session != null) request.session(session);
        request.with(csrf());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(request);
    }
    JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    Map<String, Object> clubBody() {
        return new HashMap<>(Map.of("name", "后端测试社团-" + UUID.randomUUID(), "category", "科技实践", "description", "虚构测试资料",
                "tags", "编程,摄影", "campus", "示例校区", "recruiting", true, "requirements", "愿意参与", "schedule", "周三晚", "place", "学生中心"));
    }
    Map<String, Object> draftBody(int minutes, int capacity) {
        LocalDateTime start = LocalDateTime.now().withNano(0).plusMinutes(minutes);
        return new HashMap<>(Map.of("title", "后端验收活动", "description", "虚构活动", "location", "学生中心", "capacity", capacity,
                "startTime", start.toString(), "registrationDeadline", start.minusMinutes(2).toString()));
    }
    long draft(Map<String, Object> body) throws Exception { return json(send(post("/api/manage/clubs/" + clubId + "/activities"), managerSession, body)).get("id").asLong(); }
    long published(int minutes, int capacity) throws Exception {
        long id = draft(draftBody(minutes, capacity)); json(send(post("/api/manage/activities/" + id + "/publish"), managerSession, null)); return id;
    }
    ResultActions signup(long id, MockHttpSession session) throws Exception { return send(post("/api/activities/" + id + "/registrations"), session, null); }
    Map<String, Object> expense(String key, String amount) {
        return new HashMap<>(Map.of("requestId", key, "amount", amount, "description", "材料支出", "occurredOn", LocalDate.now().toString()));
    }
    @Test
    void registrationCannotElevateRoleAndProfileIsPrivate() throws Exception {
        String username = "student_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> body = new HashMap<>(Map.of("username", username, "password", "CampusDemo123!", "name", "注册学生", "major", "计算机", "role", "MANAGER", "managedClubIds", List.of(clubId)));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().isForbidden());
        JsonNode registered = json(send(post("/api/auth/register"), null, body));
        assertEquals("STUDENT", registered.get("role").asText()); assertFalse(registered.has("passwordHash")); assertFalse(registered.has("password"));
        assertTrue(passwords.matches("CampusDemo123!", accounts.findByUsername(username).orElseThrow().getPasswordHash()));
        send(post("/api/auth/register"), null, body).andExpect(status().isConflict());
        MockHttpSession fresh = login(username);
        send(post("/api/manage/clubs"), fresh, clubBody()).andExpect(status().isForbidden());
        Map<String, Object> profile = Map.of("name", "更新姓名", "major", "数字媒体", "interests", List.of(" 摄影 ", "摄影", "编程"), "availableTime", "周三晚上", "role", "MANAGER", "userId", second.getId());
        json(send(put("/api/profile"), fresh, profile));
        mvc.perform(get("/api/profile").session(login(username))).andExpect(jsonPath("$.name").value("更新姓名"))
                .andExpect(jsonPath("$.interests.length()").value(2)).andExpect(jsonPath("$.availableTime").value("周三晚上"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
        mvc.perform(get("/api/profile").session(secondSession)).andExpect(jsonPath("$.name").value("另一测试学生"));
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
    }
    @Test
    void registrationAndProfileValidation() throws Exception {
        Map<String, Object> invalid = new HashMap<>(Map.of("username", "wrong name", "password", "123", "name", " ", "major", "计算机"));
        send(post("/api/auth/register"), null, invalid).andExpect(status().isBadRequest());
        invalid.put("username", "valid_" + UUID.randomUUID().toString().substring(0, 8)); invalid.put("name", "学生"); invalid.put("password", "密".repeat(25));
        send(post("/api/auth/register"), null, invalid).andExpect(status().isBadRequest());
        for (List<String> tags : List.of(List.of("摄影,编程"), Collections.nCopies(13, "摄影"), List.of(" "))) {
            send(put("/api/profile"), studentSession, Map.of("name", "学生", "major", "计算机", "interests", tags, "availableTime", ""))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/profile").session(studentSession).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
    @Test
    void clubOwnershipMaintenanceAndRecruitmentToggle() throws Exception {
        Map<String, Object> body = clubBody(); body.put("recruiting", false);
        send(put("/api/manage/clubs/" + clubId), outsiderSession, body).andExpect(status().isForbidden());
        send(put("/api/manage/clubs/" + clubId), studentSession, body).andExpect(status().isForbidden());
        JsonNode changed = json(send(put("/api/manage/clubs/" + clubId), managerSession, body));
        assertEquals(clubId.longValue(), changed.get("id").asLong()); assertFalse(changed.get("recruiting").asBoolean());
        send(post("/api/recruitment/applications"), studentSession, Map.of("clubId", clubId, "reason", "申请"))
                .andExpect(status().isConflict());
        body.put("recruiting", true); json(send(put("/api/manage/clubs/" + clubId), managerSession, body));
        send(post("/api/recruitment/applications"), studentSession, Map.of("clubId", clubId, "reason", "申请"))
                .andExpect(status().isOk());
        assertTrue(json(send(get("/api/manage/clubs"), managerSession, null)).findValues("id").stream().anyMatch(n -> n.asLong() == clubId));
        send(post("/api/manage/clubs"), managerSession, body).andExpect(status().isConflict());
        send(put("/api/manage/clubs/999999"), managerSession, body).andExpect(status().isNotFound());
    }
    @Test
    void failedManagerMembershipDoesNotLeaveOrphanClub() throws Exception {
        Map<String, Object> body = clubBody();
        doThrow(new DataIntegrityViolationException("simulated membership failure")).when(memberships).saveAndFlush(any(Membership.class));
        send(post("/api/manage/clubs"), managerSession, body).andExpect(status().isConflict());
        assertTrue(clubs.findByName(body.get("name").toString()).isEmpty());
    }
    @Test
    void favoritesAreOwnedAndIdempotent() throws Exception {
        JsonNode first = json(send(put("/api/favorites/" + clubId), studentSession, null));
        JsonNode again = json(send(put("/api/favorites/" + clubId), studentSession, null));
        assertEquals(first.get("id"), again.get("id")); assertEquals(first.get("createdAt"), again.get("createdAt"));
        assertEquals(0, json(send(get("/api/favorites"), secondSession, null)).size());
        json(send(put("/api/favorites/" + clubId), secondSession, null));
        send(delete("/api/favorites/" + clubId), studentSession, null).andExpect(jsonPath("$.changed").value(1));
        send(delete("/api/favorites/" + clubId), studentSession, null).andExpect(jsonPath("$.changed").value(0));
        assertEquals(1, json(send(get("/api/favorites"), secondSession, null)).size());
        send(put("/api/favorites/999999"), studentSession, null).andExpect(status().isNotFound());
        mvc.perform(get("/api/favorites")).andExpect(status().isUnauthorized());
    }
    @Test
    void activityEditingProtectsExistingParticipants() throws Exception {
        Map<String, Object> body = draftBody(60, 2); long id = draft(body);
        body.put("title", "修改后的草稿"); json(send(put("/api/manage/activities/" + id), managerSession, body));
        json(send(post("/api/manage/activities/" + id + "/publish"), managerSession, null));
        signup(id, studentSession).andExpect(status().isOk()); signup(id, secondSession).andExpect(status().isOk());
        body.put("capacity", 1); send(put("/api/manage/activities/" + id), managerSession, body).andExpect(status().isConflict());
        body.put("capacity", 3); body.put("location", "另一教室"); send(put("/api/manage/activities/" + id), managerSession, body).andExpect(status().isConflict());
        body.put("location", "学生中心"); body.put("startTime", LocalDateTime.now().withNano(0).plusHours(2).toString());
        send(put("/api/manage/activities/" + id), managerSession, body).andExpect(status().isConflict());
        body.put("startTime", activities.findById(id).orElseThrow().getStartTime().toString()); body.put("description", "补充内容");
        send(put("/api/manage/activities/" + id), outsiderSession, body).andExpect(status().isForbidden());
        json(send(put("/api/manage/activities/" + id), managerSession, body));
        mvc.perform(get("/api/activities/" + id)).andExpect(jsonPath("$.capacity").value(3)).andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }
    @Test
    void activityCancellationRetainsHistoryAndNotifiesOnlyParticipants() throws Exception {
        long id = published(60, 2); signup(id, studentSession).andExpect(status().isOk()); signup(id, secondSession).andExpect(status().isOk());
        send(post("/api/manage/activities/" + id + "/cancel"), outsiderSession, Map.of("reason", "取消"))
                .andExpect(status().isForbidden());
        send(post("/api/manage/activities/" + id + "/cancel"), managerSession, Map.of("reason", "天气原因"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.enrolled").value(0));
        mvc.perform(get("/api/activities/" + id)).andExpect(jsonPath("$.cancelReason").value("天气原因"));
        assertEquals("CANCELLED", registrations.findByActivityIdAndUserId(id, student.getId()).orElseThrow().getStatus());
        assertEquals(2, notifications.countByUserIdAndReadAtIsNull(student.getId()));
        mvc.perform(get("/api/notifications").session(studentSession)).andExpect(jsonPath("$.items[0].type").value("ACTIVITY_WITHDRAWN"));
        send(post("/api/manage/activities/" + id + "/cancel"), managerSession, Map.of("reason", "重复"))
                .andExpect(status().isConflict());
        signup(id, studentSession).andExpect(status().isNotFound());
        long draftId = draft(draftBody(60, 2)); json(send(post("/api/manage/activities/" + draftId + "/cancel"), managerSession, Map.of("reason", "撤销草稿")));
        mvc.perform(get("/api/activities/" + draftId)).andExpect(status().isNotFound());
    }
    @Test
    void notificationFailureRollsBackActivityCancellation() throws Exception {
        long id = published(60, 2); signup(id, studentSession).andExpect(status().isOk());
        doThrow(new DataIntegrityViolationException("simulated notification failure")).when(notifications).saveAndFlush(any(Notification.class));
        send(post("/api/manage/activities/" + id + "/cancel"), managerSession, Map.of("reason", "取消"))
                .andExpect(status().isConflict());
        assertEquals("PUBLISHED", activities.findById(id).orElseThrow().getStatus());
        assertEquals("REGISTERED", registrations.findByActivityIdAndUserId(id, student.getId()).orElseThrow().getStatus());
        assertEquals(1, notifications.countByUserIdAndReadAtIsNull(student.getId()));
    }
    @Test
    void checkInCodeScopeIdempotenceAndClosure() throws Exception {
        long id = published(10, 2); signup(id, studentSession).andExpect(status().isOk());
        String base = "/api/manage/activities/" + id + "/check-in";
        String code = json(send(post(base + "/open"), managerSession, null)).get("code").asText();
        assertEquals(code, json(send(post(base + "/open"), managerSession, null)).get("code").asText());
        String publicJson = mvc.perform(get("/api/activities/" + id)).andReturn().getResponse().getContentAsString();
        assertFalse(publicJson.contains(code)); assertFalse(publicJson.contains("checkInCode"));
        send(get(base), outsiderSession, null).andExpect(status().isForbidden()); send(get(base), studentSession, null).andExpect(status().isForbidden());
        send(post("/api/activities/" + id + "/check-in"), studentSession, Map.of("code", "wrong"))
                .andExpect(status().isBadRequest());
        send(post("/api/activities/" + id + "/check-in"), secondSession, Map.of("code", code))
                .andExpect(status().isConflict());
        JsonNode signed = json(send(post("/api/activities/" + id + "/check-in"), studentSession, Map.of("code", code)));
        assertFalse(signed.get("checkedInAt").isNull());
        assertEquals(signed.get("checkedInAt"), json(send(post("/api/activities/" + id + "/check-in"), studentSession, Map.of("code", code))).get("checkedInAt"));
        send(post("/api/activities/" + id + "/registrations/cancel"), studentSession, null).andExpect(status().isConflict());
        send(post("/api/manage/activities/" + id + "/cancel"), managerSession, Map.of("reason", "取消"))
                .andExpect(status().isConflict());
        send(get(base), managerSession, null).andExpect(jsonPath("$.checkedIn").value(1));
        json(send(post(base + "/close"), managerSession, null));
        send(post("/api/activities/" + id + "/check-in"), studentSession, Map.of("code", code)).andExpect(status().isConflict());
        String rotated = json(send(post(base + "/open"), managerSession, null)).get("code").asText(); assertNotEquals(code, rotated);
        send(post("/api/activities/" + id + "/check-in"), studentSession, Map.of("code", code)).andExpect(status().isBadRequest());
    }
    @Test
    void checkInRejectsEarlyDraftAndCancelledRegistration() throws Exception {
        long early = published(60, 2);
        send(post("/api/manage/activities/" + early + "/check-in/open"), managerSession, null).andExpect(status().isConflict());
        long draftId = draft(draftBody(10, 2));
        send(post("/api/manage/activities/" + draftId + "/check-in/open"), managerSession, null).andExpect(status().isNotFound());
        long id = published(10, 2); signup(id, studentSession).andExpect(status().isOk());
        json(send(post("/api/activities/" + id + "/registrations/cancel"), studentSession, null));
        String code = json(send(post("/api/manage/activities/" + id + "/check-in/open"), managerSession, null)).get("code").asText();
        send(post("/api/activities/" + id + "/check-in"), studentSession, Map.of("code", code)).andExpect(status().isConflict());
    }
    @Test
    void startedActivityCannotBeEditedOrCancelled() throws Exception {
        Activity a = Activity.draft(clubId, "已开始", "说明", "学生中心", LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusMinutes(2), 2, 1L);
        a.publish(); long id = activities.saveAndFlush(a).getId();
        send(put("/api/manage/activities/" + id), managerSession, draftBody(60, 2)).andExpect(status().isConflict());
        send(post("/api/manage/activities/" + id + "/cancel"), managerSession, Map.of("reason", "取消")).andExpect(status().isConflict());
    }
    @Test
    void financeUsesExactAmountsScopeIdempotencyAndRetainedVoidHistory() throws Exception {
        long id = draft(draftBody(60, 2)); String base = "/api/manage/activities/" + id;
        send(get(base + "/finance"), studentSession, null).andExpect(status().isForbidden());
        send(get(base + "/finance"), outsiderSession, null).andExpect(status().isForbidden());
        assertTrue(json(send(get(base + "/finance"), managerSession, null)).get("remaining").isNull());
        json(send(put(base + "/budget"), managerSession, Map.of("amount", "100.00")));
        String key = UUID.randomUUID().toString(); Map<String, Object> firstBody = expense(key, "60.10");
        JsonNode first = json(send(post(base + "/expenses"), managerSession, firstBody));
        JsonNode retried = json(send(post(base + "/expenses"), managerSession, firstBody));
        assertEquals(first.get("id"), retried.get("id")); assertEquals(first.get("createdAt"), retried.get("createdAt"));
        firstBody.put("amount", "60.11"); send(post(base + "/expenses"), managerSession, firstBody).andExpect(status().isConflict());
        json(send(post(base + "/expenses"), managerSession, expense(UUID.randomUUID().toString(), "40.20")));
        send(get(base + "/finance"), managerSession, null).andExpect(jsonPath("$.spent").value(100.30))
                .andExpect(jsonPath("$.remaining").value(-0.30)).andExpect(jsonPath("$.overBudget").value(true));
        send(get("/api/manage/clubs/" + clubId + "/finance"), managerSession, null).andExpect(jsonPath("$.spentTotal").value(100.30));
        send(post("/api/manage/expenses/" + first.get("id").asLong() + "/void"), outsiderSession, Map.of("reason", "错误"))
                .andExpect(status().isForbidden());
        json(send(post("/api/manage/expenses/" + first.get("id").asLong() + "/void"), managerSession, Map.of("reason", "录入错误")));
        send(post("/api/manage/expenses/" + first.get("id").asLong() + "/void"), managerSession, Map.of("reason", "重复"))
                .andExpect(status().isConflict());
        send(get(base + "/finance"), managerSession, null).andExpect(jsonPath("$.spent").value(40.20))
                .andExpect(jsonPath("$.expenses.length()").value(2)).andExpect(jsonPath("$.remaining").value(59.80));
        assertEquals("VOID", expenses.findById(first.get("id").asLong()).orElseThrow().getStatus());
        firstBody.put("amount", "60.10");
        assertEquals("VOID", json(send(post(base + "/expenses"), managerSession, firstBody)).get("status").asText());
        assertEquals(0, expenses.totalSpent(id).compareTo(new java.math.BigDecimal("40.20")));
    }
    @Test
    void cancelledActivityRetainsCostsAndRejectsReopening() throws Exception {
        long id = published(60, 2); String base = "/api/manage/activities/" + id;
        json(send(put(base + "/budget"), managerSession, Map.of("amount", "10.00")));
        json(send(post(base + "/expenses"), managerSession, expense(UUID.randomUUID().toString(), "9.99")));
        json(send(post(base + "/cancel"), managerSession, Map.of("reason", "取消但已发生费用")));
        json(send(post(base + "/expenses"), managerSession, expense(UUID.randomUUID().toString(), "0.01")));
        send(get(base + "/finance"), managerSession, null).andExpect(jsonPath("$.spent").value(10.00))
                .andExpect(jsonPath("$.remaining").value(0.00)).andExpect(jsonPath("$.expenses.length()").value(2));
        send(post(base + "/publish"), managerSession, null).andExpect(status().isConflict());
        send(put(base), managerSession, draftBody(60, 2)).andExpect(status().isConflict());
        send(post(base + "/check-in/open"), managerSession, null).andExpect(status().isNotFound());
    }
    @Test
    void expiredDeadlineAllowsContentEditWithoutReopeningSignup() throws Exception {
        LocalDateTime start = LocalDateTime.now().withNano(0).plusHours(1), deadline = LocalDateTime.now().withNano(0).minusMinutes(1);
        Activity a = Activity.draft(clubId, "截止后的活动", "原介绍", "学生中心", start, deadline, 2, 1L);
        a.publish(); long id = activities.saveAndFlush(a).getId();
        Map<String, Object> body = new HashMap<>(Map.of("title", "截止后补充说明", "description", "补充介绍", "location", "学生中心", "startTime", start.toString(), "registrationDeadline", deadline.toString(), "capacity", 2));
        json(send(put("/api/manage/activities/" + id), managerSession, body));
        signup(id, studentSession).andExpect(status().isConflict());
        body.put("registrationDeadline", deadline.minusMinutes(1).toString());
        send(put("/api/manage/activities/" + id), managerSession, body).andExpect(status().isBadRequest());
    }
    @Test
    void financeRejectsInvalidAmountsDatesAndMissingRecords() throws Exception {
        long id = draft(draftBody(60, 2)); String base = "/api/manage/activities/" + id;
        for (String amount : List.of("-1.00", "1.001", "100000000.00"))
            send(put(base + "/budget"), managerSession, Map.of("amount", amount)).andExpect(status().isBadRequest());
        for (String amount : List.of("0", "-1", "0.001", "100000000"))
            send(post(base + "/expenses"), managerSession, expense(UUID.randomUUID().toString(), amount)).andExpect(status().isBadRequest());
        Map<String, Object> future = expense(UUID.randomUUID().toString(), "1.00"); future.put("occurredOn", LocalDate.now().plusDays(1).toString());
        send(post(base + "/expenses"), managerSession, future).andExpect(status().isBadRequest());
        send(post("/api/manage/expenses/999999/void"), managerSession, Map.of("reason", "错误"))
                .andExpect(status().isNotFound());
        send(get("/api/manage/activities/999999/finance"), managerSession, null).andExpect(status().isNotFound());
    }
    @Test
    void failedExpenseWriteDoesNotChangeTotals() throws Exception {
        long id = draft(draftBody(60, 2));
        doThrow(new DataIntegrityViolationException("simulated expense failure")).when(expenses).saveAndFlush(any(Expense.class));
        send(post("/api/manage/activities/" + id + "/expenses"), managerSession, expense(UUID.randomUUID().toString(), "9.99"))
                .andExpect(status().isConflict());
        send(get("/api/manage/activities/" + id + "/finance"), managerSession, null).andExpect(jsonPath("$.spent").value(0))
                .andExpect(jsonPath("$.expenses.length()").value(0));
    }
    @Test
    void concurrentExpenseRetryWritesOneEntry() throws Exception {
        long id = draft(draftBody(60, 2)); Map<String, Object> body = expense(UUID.randomUUID().toString(), "9.99");
        race(() -> send(post("/api/manage/activities/" + id + "/expenses"), managerSession, body).andReturn().getResponse().getStatus(), List.of(200, 200));
        assertEquals(1, expenses.findByActivityIdOrderByIdDesc(id).size());
        assertEquals(0, expenses.totalSpent(id).compareTo(new java.math.BigDecimal("9.99")));
    }
    @Test
    void concurrentFavoriteRetryCreatesOneFavorite() throws Exception {
        race(() -> send(put("/api/favorites/" + clubId), studentSession, null).andReturn().getResponse().getStatus(), List.of(200, 200));
        assertEquals(1, json(send(get("/api/favorites"), studentSession, null)).size());
    }
    @Test
    void concurrentRegistrationCannotCreateDuplicateAccounts() throws Exception {
        Map<String, Object> body = Map.of("username", "student_" + UUID.randomUUID().toString().substring(0, 8), "password", "CampusDemo123!", "name", "学生", "major", "计算机");
        race(() -> send(post("/api/auth/register"), null, body).andReturn().getResponse().getStatus(), List.of(200, 409));
    }
    void race(Callable<Integer> action, List<Integer> expected) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> call = () -> { start.await(); return action.call(); };
            Future<Integer> first = pool.submit(call), second = pool.submit(call); start.countDown();
            assertEquals(expected, java.util.stream.Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)).sorted().toList());
        } finally { pool.shutdownNow(); }
    }
}
