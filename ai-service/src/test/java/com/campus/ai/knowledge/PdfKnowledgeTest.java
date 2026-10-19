package com.campus.ai.knowledge;

import com.campus.ai.client.ModelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:pdf_knowledge;DB_CLOSE_DELAY=-1", "ai.mode=ollama", "ai.embedding-provider=ollama", "ai.embedding.model=pdf-fixture", "ai.redis-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PdfKnowledgeTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
    @Autowired DocumentRepository documents; @Autowired ChunkRepository chunks;
    @MockitoBean ModelClient models;
    @BeforeEach void setup() {
        chunks.deleteAll(); documents.deleteAll();
        when(models.embeddingVersion()).thenReturn("pdf-fixture-v1");
        when(models.embed(anyList())).thenAnswer(call -> ((List<?>) call.getArgument(0)).stream().map(t -> new double[]{1, 0}).toList());
        when(models.chat(anyList())).thenReturn("The camera workshop is Monday. [1]");
    }
    MockHttpServletRequestBuilder actor(MockHttpServletRequestBuilder request, long id, String role, String clubs) {
        return request.header("X-Internal-Token", "protocol-fixture-internal-token").header("X-Actor-Id", id)
                .header("X-Actor-Role", role).header("X-Actor-Clubs", clubs).header("X-Actor-Managed-Clubs", role.equals("MANAGER") ? clubs : "");
    }
    MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "campus-rules.pdf", "application/octet-stream", bytes); }
    byte[] pdf() throws Exception { return KnowledgeTextExtractorTest.pdf("The camera workshop is Monday. Bring a camera.", 2, null); }
    long uploadPublic(byte[] bytes) throws Exception {
        var response = mvc.perform(actor(multipart("/internal/knowledge").file(file(bytes)), 99, "PLATFORM_ADMIN", ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.embeddingVersion").value("pdf-fixture-v1")).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray()).path("id").asLong();
    }
    @Test void pdfExtractionIndexRetrievalReferencesAndDeduplication() throws Exception {
        byte[] bytes = pdf(); long id = uploadPublic(bytes); assertThat(uploadPublic(bytes)).isEqualTo(id);
        assertThat(documents.count()).isEqualTo(1); assertThat(chunks.findAll()).allMatch(c -> c.getVectorJson() != null);
        mvc.perform(actor(get("/internal/knowledge/" + id), 13, "STUDENT", ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("Bring a camera.")));
        mvc.perform(actor(post("/internal/chat"), 13, "STUDENT", "").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"camera workshop\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.references[0].documentId").value(id))
                .andExpect(jsonPath("$.references[0].documentName").value("campus-rules.pdf"));
        verify(models).chat(anyList());
    }
    @Test void privatePdfKeepsMembershipAndUploadFences() throws Exception {
        var response = mvc.perform(actor(multipart("/internal/knowledge").file(file(pdf())).param("visibility", "CLUB").param("clubId", "8002"), 11, "MANAGER", "8002"))
                .andExpect(status().isOk()).andReturn();
        long id = mapper.readTree(response.getResponse().getContentAsByteArray()).path("id").asLong();
        mvc.perform(actor(get("/internal/knowledge/" + id), 12, "STUDENT", "8002")).andExpect(status().isOk());
        mvc.perform(actor(get("/internal/knowledge/" + id), 13, "STUDENT", "")).andExpect(status().isNotFound());
        mvc.perform(actor(get("/internal/knowledge/" + id), 99, "PLATFORM_ADMIN", "")).andExpect(status().isNotFound());
        mvc.perform(actor(post("/internal/chat"), 13, "STUDENT", "").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"camera workshop\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.references").isEmpty());
        verify(models, never()).chat(anyList());
        mvc.perform(actor(multipart("/internal/knowledge").file(file(pdf())), 12, "STUDENT", "8002")).andExpect(status().isForbidden());
        mvc.perform(actor(multipart("/internal/knowledge").file(file(pdf())).param("visibility", "CLUB").param("clubId", "8003"), 11, "MANAGER", "8002")).andExpect(status().isForbidden());
    }
    @Test void rejectedPdfDoesNotSaveOrCallModelsAndEmbeddingFailureIsAtomic() throws Exception {
        for (byte[] bytes : new byte[][]{KnowledgeTextExtractorTest.pdf("", 1, null), KnowledgeTextExtractorTest.pdf("Private", 1, "secret"), {1, 2, 3}}) {
            mvc.perform(actor(multipart("/internal/knowledge").file(file(bytes)), 99, "PLATFORM_ADMIN", "")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(models); assertThat(documents.count()).isZero(); assertThat(chunks.count()).isZero();
        when(models.embed(anyList())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "fixture embedding offline"));
        mvc.perform(actor(multipart("/internal/knowledge").file(file(pdf())), 99, "PLATFORM_ADMIN", "")).andExpect(status().isServiceUnavailable());
        assertThat(documents.count()).isZero(); assertThat(chunks.count()).isZero();
    }
}
