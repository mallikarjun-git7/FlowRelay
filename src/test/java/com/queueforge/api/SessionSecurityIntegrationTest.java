package com.queueforge.api;

import com.queueforge.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
class SessionSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(
                formLogin().user("admin").password("LocalTestPassword123"))
            .andExpect(status().is3xxRedirection())
            .andReturn().getRequest().getSession(false);
    }

    @Test
    void authenticatedSessionCanAccessApi() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/v1/jobs").session(session))
            .andExpect(status().isOk());
    }

    @Test
    void sessionPostWithoutCsrfIsRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/v1/jobs").session(session)
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void sessionPostWithCsrfReachesValidation() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/v1/jobs").session(session)
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/logout").session(session).with(csrf()))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/api/v1/jobs").session(session))
            .andExpect(status().isUnauthorized());
    }
}
