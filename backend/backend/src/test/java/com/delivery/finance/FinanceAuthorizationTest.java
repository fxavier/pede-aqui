package com.delivery.finance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.security.SecurityConfig;
import com.delivery.finance.controller.FinanceController;
import com.delivery.finance.service.FinanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FinanceController.class)
@Import(SecurityConfig.class)
class FinanceAuthorizationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinanceService financeService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsNonFinanceRoles() throws Exception {
        mockMvc.perform(get("/api/v1/finance/summary").with(jwt().authorities(() -> "ROLE_CUSTOMER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/finance/summary").with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/finance/summary").with(jwt().authorities(() -> "ROLE_COURIER"))).andExpect(status().isForbidden());
    }
}
