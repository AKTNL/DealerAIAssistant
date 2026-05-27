package com.brand.agentpoc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.dto.response.ModelConfigTestResponse;
import com.brand.agentpoc.service.ModelConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ModelConfigControllerTest {

    private MockMvc mockMvc;
    private ModelConfigService modelConfigService;

    @BeforeEach
    void setUp() {
        modelConfigService = mock(ModelConfigService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new ModelConfigController(modelConfigService))
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsTheSuccessfulConnectionTestResult() throws Exception {
        when(modelConfigService.testConnection(any()))
                .thenReturn(new ModelConfigTestResponse(true, "Connection test succeeded."));

        mockMvc.perform(post("/api/model-config/test")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"baseUrl":"https://api.openai.com","apiKey":"sk-test","model":"gpt-4o-mini"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Connection test succeeded."));
    }

    @Test
    void rejectsIncompleteModelSettings() throws Exception {
        mockMvc.perform(post("/api/model-config/test")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"baseUrl":"","apiKey":"sk-test","model":"gpt-4o-mini"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsFailureDetailsFromTheService() throws Exception {
        when(modelConfigService.testConnection(any()))
                .thenReturn(new ModelConfigTestResponse(false, "Authentication failed."));

        mockMvc.perform(post("/api/model-config/test")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"baseUrl":"https://api.openai.com","apiKey":"sk-test","model":"gpt-4o-mini"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication failed."));
    }
}
