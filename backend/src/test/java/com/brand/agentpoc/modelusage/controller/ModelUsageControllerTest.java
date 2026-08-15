package com.brand.agentpoc.modelusage.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.domain.PermissionKey;
import com.brand.agentpoc.modelusage.application.ModelUsageGovernanceService;
import com.brand.agentpoc.modelusage.application.ModelUsageGovernanceService.BudgetView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ModelUsageControllerTest {

    private ModelUsageGovernanceService service;
    private AuthPrincipal actor;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ModelUsageGovernanceService.class);
        actor = new AuthPrincipal(
                2L, 3L, "family", "admin", "Admin", true, false,
                Set.of("ADMIN"), Set.of(PermissionKey.MODEL_USAGE_READ, PermissionKey.MODEL_USAGE_MANAGE),
                7L, "tenant-a", 8L, Set.of(11L));
        when(service.budget(actor)).thenReturn(budget());

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ModelUsageController(service))
                .setCustomArgumentResolvers(principalResolver(actor))
                .setValidator(validator)
                .build();
    }

    @Test
    void readsBudgetForTheAuthenticatedTenantPrincipal() throws Exception {
        mockMvc.perform(get("/api/admin/model-usage/budget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyLimit").value(100))
                .andExpect(jsonPath("$.data.version").value(3));

        verify(service).budget(actor);
    }

    @Test
    void rejectsInvalidHardLimitConfigurationBeforeTheService() throws Exception {
        mockMvc.perform(put("/api/admin/model-usage/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "monthlyLimit":100,
                                  "softThresholdPercent":80,
                                  "hardLimitEnabled":true,
                                  "failOpen":true,
                                  "reservationAmount":-1,
                                  "currency":"USD",
                                  "version":3
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsAnOptimisticBudgetConflictToHttp409() throws Exception {
        when(service.saveBudget(eq(actor), any(), any()))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        mockMvc.perform(put("/api/admin/model-usage/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "monthlyLimit":100,
                                  "softThresholdPercent":80,
                                  "hardLimitEnabled":false,
                                  "failOpen":true,
                                  "reservationAmount":0,
                                  "currency":"USD",
                                  "version":3
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("The model budget changed since it was loaded."));
    }

    private BudgetView budget() {
        return new BudgetView(
                new BigDecimal("100.00000000"), 80, false, true,
                BigDecimal.ZERO, "USD", new BigDecimal("10.00000000"),
                new BigDecimal("10.00"), 0L, "OK", 3L,
                Instant.parse("2026-08-15T02:00:00Z"));
    }

    private HandlerMethodArgumentResolver principalResolver(AuthPrincipal principal) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(AuthPrincipal.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer container,
                    NativeWebRequest request,
                    WebDataBinderFactory binderFactory
            ) {
                return principal;
            }
        };
    }
}
