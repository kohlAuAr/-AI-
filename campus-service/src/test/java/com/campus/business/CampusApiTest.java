package com.campus.business;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:campus_test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class CampusApiTest {
    @Autowired MockMvc mvc;

    @Test
    void sampleListsAndPendingModulesAreHonest() throws Exception {
        mvc.perform(get("/api/clubs")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].demo").value(true));
        mvc.perform(get("/api/activities")).andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("SAMPLE"));
        mvc.perform(get("/api/system")).andExpect(jsonPath("$.security").value("LOCAL_DEMO_ONLY"))
                .andExpect(jsonPath("$.modules[3].status").value("PLANNED"));
    }

    @Test
    void missingClubIsNotFound() throws Exception {
        mvc.perform(get("/api/clubs/99999")).andExpect(status().isNotFound());
    }

    @Test
    void invalidHistoryPathIsRejectedBeforeCallingAi() throws Exception {
        mvc.perform(get("/api/ai/conversations/not-a-uuid")).andExpect(status().isBadRequest());
    }
}
