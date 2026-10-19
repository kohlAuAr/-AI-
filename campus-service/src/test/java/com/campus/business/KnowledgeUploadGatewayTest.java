package com.campus.business;

import com.campus.business.ai.AiServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:knowledge_upload_gateway;DB_CLOSE_DELAY=-1", "campus.redis-enabled=false"})
@AutoConfigureMockMvc
class KnowledgeUploadGatewayTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
    @MockitoBean AiServiceClient ai;
    MockHttpSession admin, student;
    @BeforeEach void setup() throws Exception {
        admin = login("platform_admin"); student = login("student");
        when(ai.upload(anyString(), any(), anyString(), nullable(Long.class), any())).thenReturn(mapper.readTree("{\"id\":1}"));
    }
    MockHttpSession login(String name) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", name).param("password", "CampusDemo123!"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    MockMultipartFile file(String name, int size) { return new MockMultipartFile("file", name, "application/octet-stream", new byte[size]); }
    @Test void pdfSizeAllowanceDoesNotRelaxTextOrBannerLimits() throws Exception {
        mvc.perform(multipart("/api/ai/knowledge").file(file("rules.PDF", 5 * 1024 * 1024)).session(admin).with(csrf())).andExpect(status().isOk());
        verify(ai).upload(eq("rules.PDF"), any(), eq("PUBLIC"), isNull(), any());
        clearInvocations(ai);
        mvc.perform(multipart("/api/ai/knowledge").file(file("rules.pdf", 5 * 1024 * 1024 + 1)).session(admin).with(csrf())).andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/ai/knowledge").file(file("rules.md", 128 * 1024 + 1)).session(admin).with(csrf())).andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/ai/knowledge").file(file("rules.pdf", 200000)).session(student).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/ai/knowledge").file(file("rules.pdf", 200000)).session(admin)).andExpect(status().isForbidden());
        verifyNoInteractions(ai);
    }
}
