package com.campus.ai;

import com.campus.ai.knowledge.*;
import com.campus.ai.chat.ConversationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:ai_test;DB_CLOSE_DELAY=-1", "ai.mode=local", "ai.redis-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KnowledgeChatTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ChunkRepository chunks;
    @Autowired DocumentRepository documents;
    @Autowired ConversationRepository conversations;

    @BeforeEach
    void cleanTestData() {
        conversations.deleteAll(); chunks.deleteAll(); documents.deleteAll();
    }

    @Test
    void uploadRetrieveAndPersistReferencesAcrossHistory() throws Exception {
        var file = new MockMultipartFile("file", "photography.md", "text/markdown", "摄影社招新时间为十月，摄影新手可以参加构图交流。".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/internal/knowledge").file(file)).andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("PUBLIC_DEMO"));
        mvc.perform(multipart("/internal/knowledge").file(file)).andExpect(status().isOk());
        assertThat(documents.count()).isEqualTo(1);
        String response = mvc.perform(post("/internal/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"摄影社招新时间\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("LOCAL"))
                .andExpect(jsonPath("$.retrieval").value("KEYWORD"))
                .andExpect(jsonPath("$.references[0].documentName").value("photography.md"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = mapper.readTree(response).get("conversationId").asText();
        mvc.perform(get("/internal/conversations/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].references[0].documentName").value("photography.md"));
        assertThat(conversations.count()).isEqualTo(1);
    }

    @Test
    void emptyEvidenceDoesNotInventReferences() throws Exception {
        mvc.perform(post("/internal/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"谁可以参加摄影活动\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.references").isEmpty());
    }

    @Test
    void badTextAndUnsupportedFilesAreRejectedWithoutSaving() throws Exception {
        mvc.perform(multipart("/internal/knowledge").file(new MockMultipartFile("file", "bad.txt", "text/plain", new byte[]{(byte) 0xc3, 0x28}))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/internal/knowledge").file(new MockMultipartFile("file", "bad.pdf", "application/pdf", new byte[]{1}))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
        mvc.perform(multipart("/internal/knowledge").file(new MockMultipartFile("file", "blank.md", "text/plain", "  ".getBytes(StandardCharsets.UTF_8)))).andExpect(status().isBadRequest());
        assertThat(documents.count()).isZero();
    }

    @Test
    void invalidConversationOrBlankQuestionIsRejected() throws Exception {
        mvc.perform(post("/internal/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\" \"}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/internal/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"test\",\"conversationId\":\"../status\"}" )).andExpect(status().isBadRequest());
    }
}
