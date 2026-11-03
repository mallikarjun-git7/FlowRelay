package com.queueforge.api;

import com.queueforge.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "FLOWRELAY_REQUIRE_API_KEY=true",
    "queueforge.security.admin-api-key=integration-only-key"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class ApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousApiRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/jobs"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidApiKeyIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                .header("X-QueueForge-Admin-Key", "wrong-key"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validApiKeyCanReadJobs() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                .header("X-QueueForge-Admin-Key", "integration-only-key"))
            .andExpect(status().isOk());
    }

    @Test
    void validApiKeyCanReachProtectedPostEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                .header("X-QueueForge-Admin-Key", "integration-only-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"queue\":\"\",\"type\":\"test\",\"payload\":null}"))
            .andExpect(status().isBadRequest());
    }
}
