package com.delivery.upload;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.security.SecurityConfig;
import com.delivery.upload.controller.UploadController;
import com.delivery.upload.dto.CreateUploadUrlRequest;
import com.delivery.upload.dto.UploadUrlResponse;
import com.delivery.upload.service.UploadService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** LAC-12: the image presign endpoint is role-gated like the document one and requires the declared file size. */
@WebMvcTest(UploadController.class)
@Import(SecurityConfig.class)
class UploadControllerSecurityTest {
    private static final String IMAGE_URL = "/api/v1/uploads/images/presigned-url";
    private static final String BODY = "{\"contentType\":\"image/png\",\"purpose\":\"product\",\"fileName\":\"foto\",\"contentLength\":2048}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UploadService uploadService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(post(IMAGE_URL).contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "FINANCE", "SUPPORT"})
    void rolesOutsideTheUploadersAreForbidden(String role) throws Exception {
        mockMvc.perform(post(IMAGE_URL).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "OPS", "VENDOR_ADMIN", "VENDOR_STAFF", "COURIER"})
    void uploadersGetAPresignedUrl(String role) throws Exception {
        when(uploadService.createImageUploadUrl(any(CreateUploadUrlRequest.class)))
                .thenReturn(new UploadUrlResponse("http://storage/put", "tenants/t/uploads/product/u/1-foto.png", 900, 5_242_880));
        mockMvc.perform(post(IMAGE_URL).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void missingContentLengthIsBadRequest() throws Exception {
        mockMvc.perform(post(IMAGE_URL).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VENDOR_ADMIN")))
                        .contentType("application/json")
                        .content("{\"contentType\":\"image/png\",\"purpose\":\"product\",\"fileName\":\"foto\"}"))
                .andExpect(status().isBadRequest());
    }
}
