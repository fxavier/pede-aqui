package com.delivery.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.auth.controller.CustomerRegistrationController;
import com.delivery.auth.controller.MerchantRegistrationController;
import com.delivery.auth.service.CustomerRegistrationService;
import com.delivery.auth.service.MerchantRegistrationService;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Weak passwords are refused with 400 before Keycloak is called; a Keycloak policy rejection surfaces as 400. */
@WebMvcTest({CustomerRegistrationController.class, MerchantRegistrationController.class})
@Import(SecurityConfig.class)
class RegistrationPasswordPolicyApiTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private CustomerRegistrationService customerService;
    @MockitoBean private MerchantRegistrationService merchantService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void customerWithWeakPasswordGets400ValidationFailedOnPassword() throws Exception {
        mockMvc.perform(post("/api/v1/customers/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ana\",\"lastName\":\"M\",\"email\":\"ana@example.test\",\"password\":\"password1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')].message")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "must have at least 10 characters", "must contain at least one upper-case letter")));
        verify(customerService, never()).register(any());
    }

    @Test
    void merchantWithWeakPasswordGets400ValidationFailedOnPassword() throws Exception {
        mockMvc.perform(post("/api/v1/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Loja\",\"companySlug\":\"loja\",\"defaultCurrency\":\"MZN\",\"firstName\":\"Ana\","
                                + "\"lastName\":\"M\",\"email\":\"ana@example.test\",\"password\":\"segredo-123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must contain at least one upper-case letter"));
        verify(merchantService, never()).register(any());
    }

    @Test
    void keycloakPolicyRejectionIsA400PasswordPolicyViolation() throws Exception {
        when(customerService.register(any())).thenThrow(
                new BusinessException("password_policy_violation", "Password does not meet the password policy", HttpStatus.BAD_REQUEST));
        mockMvc.perform(post("/api/v1/customers/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ana\",\"lastName\":\"M\",\"email\":\"ana@example.test\",\"password\":\"Segredo1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("password_policy_violation"));
    }
}
