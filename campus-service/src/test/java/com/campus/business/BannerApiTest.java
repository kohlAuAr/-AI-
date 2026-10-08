package com.campus.business;

import com.campus.business.banner.*;
import com.campus.business.club.*;
import com.campus.business.activity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:banner_test;MODE=MySQL;DB_CLOSE_DELAY=-1", "campus.ai-base-url=http://127.0.0.1:1"})
@AutoConfigureMockMvc
class BannerApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ClubRepository clubs;
    @Autowired ActivityRepository activities;
    @Autowired BannerRepository banners;
    MockHttpSession admin, student, manager;
    Long photo;
    @BeforeEach
    void setup() throws Exception {
        admin = login("platform_admin"); student = login("student"); manager = login("photo_manager");
        photo = clubs.findBySlug("photo").orElseThrow().getId();
    }
    MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", username).param("password", "CampusDemo123!"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    Map<String, Object> body(String type, Long id) { return new HashMap<>(Map.of("title", "首页测试海报", "targetType", type, "targetId", id, "sortOrder", 5)); }
    MockMultipartFile metadata(Map<String, Object> body) throws Exception { return new MockMultipartFile("metadata", "metadata.json", "application/json", mapper.writeValueAsBytes(body)); }
    MockMultipartFile image() throws Exception {
        var output = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(120, 50, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("image", "poster.png", "image/png", output.toByteArray());
    }
    long create(Map<String, Object> body) throws Exception {
        var response = mvc.perform(multipart("/api/platform/banners").file(metadata(body)).file(image()).session(admin).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.displayStatus").value("OFFLINE")).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray()).path("id").asLong();
    }
    void enable(long id) throws Exception { mvc.perform(post("/api/platform/banners/" + id + "/visibility").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isOk()); }
    boolean visible(long id) throws Exception {
        var rows = mapper.readTree(mvc.perform(get("/api/banners")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        for (var row : rows) if (row.path("id").asLong() == id) return true;
        return false;
    }
    @Test
    void roleBoundariesCsrfAndRegistrationCannotElevate() throws Exception {
        mvc.perform(get("/api/platform/banners")).andExpect(status().isUnauthorized());
        for (var session : List.of(student, manager)) {
            mvc.perform(get("/api/platform/banners").session(session)).andExpect(status().isForbidden());
            mvc.perform(get("/api/platform/banner-targets").session(session)).andExpect(status().isForbidden());
            mvc.perform(multipart("/api/platform/banners").file(metadata(body("CLUB", photo))).file(image()).session(session).with(csrf())).andExpect(status().isForbidden());
        }
        mvc.perform(multipart("/api/platform/banners").file(metadata(body("CLUB", photo))).file(image()).session(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/banner-targets").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/session").session(admin)).andExpect(jsonPath("$.user.role").value("PLATFORM_ADMIN")).andExpect(jsonPath("$.user.managedClubIds").isEmpty());
        mvc.perform(get("/api/manage/clubs/" + photo + "/members").session(admin)).andExpect(status().isForbidden());
        mvc.perform(post("/api/recruitment/applications").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"clubId\":" + photo + ",\"reason\":\"不能冒充学生\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username", "banner_" + UUID.randomUUID().toString().substring(0, 8), "password", "TestPassword123!", "name", "测试学生", "major", "计算机", "role", "PLATFORM_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
    }
    @Test
    void createEditImageAndReversibleVisibility() throws Exception {
        long id = create(body("CLUB", photo)); assertFalse(visible(id));
        mvc.perform(get("/api/banners/" + id + "/image")).andExpect(status().isNotFound());
        byte[] original = mvc.perform(get("/api/platform/banners/" + id + "/image").session(admin)).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andReturn().getResponse().getContentAsByteArray();
        mvc.perform(get("/api/platform/banners/" + id + "/image").session(student)).andExpect(status().isForbidden());
        enable(id); assertTrue(visible(id));
        mvc.perform(get("/api/banners/" + id + "/image")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        var edited = body("CLUB", photo); edited.put("title", "修改后的海报"); edited.put("sortOrder", 1);
        mvc.perform(multipart("/api/platform/banners/" + id).file(metadata(edited)).with(r -> { r.setMethod("PUT"); return r; }).session(admin).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("修改后的海报")).andExpect(jsonPath("$.sortOrder").value(1));
        assertArrayEquals(original, mvc.perform(get("/api/banners/" + id + "/image")).andReturn().getResponse().getContentAsByteArray());
        mvc.perform(post("/api/platform/banners/" + id + "/visibility").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        assertFalse(visible(id)); assertTrue(banners.existsById(id));
        mvc.perform(get("/api/banners/" + id + "/image")).andExpect(status().isNotFound());
    }
    @Test
    void invalidFilesParametersAndKnowledgeLimit() throws Exception {
        for (byte[] bytes : List.of("<svg onload='alert(1)'/>".getBytes(), new byte[0], new byte[2 * 1024 * 1024 + 1])) {
            mvc.perform(multipart("/api/platform/banners").file(metadata(body("CLUB", photo))).file(new MockMultipartFile("image", "fake.png", "image/png", bytes)).session(admin).with(csrf()))
                    .andExpect(status().is(bytes.length > 2 * 1024 * 1024 ? 413 : 400));
        }
        for (String type : List.of("ACTIVITY", "RECRUITMENT")) {
            long target = type.equals("ACTIVITY") ? activities.findByStatusOrderByStartTimeAsc("SAMPLE").get(0).getId() : clubs.findBySlug("read").orElseThrow().getId();
            mvc.perform(multipart("/api/platform/banners").file(metadata(body(type, target))).file(image()).session(admin).with(csrf())).andExpect(status().isBadRequest());
        }
        var invalid = body("CLUB", photo); invalid.put("startsAt", "2099-01-02T12:00:00"); invalid.put("endsAt", "2099-01-01T12:00:00");
        mvc.perform(multipart("/api/platform/banners").file(metadata(invalid)).file(image()).session(admin).with(csrf())).andExpect(status().isBadRequest());
        invalid = body("CLUB", photo); invalid.put("sortOrder", -1);
        mvc.perform(multipart("/api/platform/banners").file(metadata(invalid)).file(image()).session(admin).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/ai/knowledge").file(new MockMultipartFile("file", "oversize.txt", "text/plain", new byte[128 * 1024 + 1])).with(csrf())).andExpect(status().isPayloadTooLarge());
    }
    @Test
    void scheduleExpirationAndStableOrdering() throws Exception {
        var scheduled = body("CLUB", photo); scheduled.put("startsAt", LocalDateTime.now().plusDays(2).toString());
        long future = create(scheduled); enable(future); assertFalse(visible(future));
        long expired = create(body("CLUB", photo)); enable(expired);
        var old = body("CLUB", photo); old.put("endsAt", LocalDateTime.now().minusMinutes(1).toString());
        mvc.perform(multipart("/api/platform/banners/" + expired).file(metadata(old)).with(r -> { r.setMethod("PUT"); return r; }).session(admin).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.displayStatus").value("EXPIRED"));
        assertFalse(visible(expired));
        mvc.perform(post("/api/platform/banners/" + expired + "/visibility").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isConflict());
        var ordered = body("CLUB", photo); ordered.put("sortOrder", 0); long first = create(ordered); long second = create(ordered); enable(first); enable(second);
        var list = mapper.readTree(mvc.perform(get("/api/banners")).andReturn().getResponse().getContentAsByteArray());
        assertEquals(first, list.get(0).path("id").asLong()); assertEquals(second, list.get(1).path("id").asLong());
    }
    @Test
    void cancelledStartedAndClosedTargetsDisappear() throws Exception {
        var club = clubs.save(new Club("轮播测试社团" + UUID.randomUUID(), "测试", "测试资料", "摄影", "示例校区"));
        club.configureRecruitment(null, true, "测试", "测试", "测试"); clubs.saveAndFlush(club);
        long recruit = create(body("RECRUITMENT", club.getId())); enable(recruit); assertTrue(visible(recruit));
        club.configureRecruitment(null, false, "测试", "测试", "测试"); clubs.saveAndFlush(club); assertFalse(visible(recruit));
        for (boolean cancelled : List.of(true, false)) {
            var activity = Activity.draft(photo, "轮播关联活动", "测试", "学生中心", LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(2), 30, 1L);
            activity.publish(); activities.saveAndFlush(activity);
            long banner = create(body("ACTIVITY", activity.getId())); enable(banner); assertTrue(visible(banner));
            if (cancelled) activity.cancel("测试取消", 1L);
            else activity.update(new ActivityController.DraftRequest("已开始的活动", "测试", "测试", LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusHours(1), 30), 1L);
            activities.saveAndFlush(activity); assertFalse(visible(banner));
            mvc.perform(get("/api/banners/" + banner + "/image")).andExpect(status().isNotFound());
        }
    }
}
