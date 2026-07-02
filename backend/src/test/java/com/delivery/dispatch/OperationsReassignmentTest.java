package com.delivery.dispatch;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.security.SecurityConfig;
import com.delivery.dispatch.controller.OperationsDispatchController;
import com.delivery.dispatch.dto.DispatchJobResponse;
import com.delivery.dispatch.entity.DispatchJobStatus;
import com.delivery.dispatch.service.DispatchService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OperationsDispatchController.class)
@Import(SecurityConfig.class)
class OperationsReassignmentTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DispatchService dispatchService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void opsCanReassignAndNonOpsCannot() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        when(dispatchService.reassign(jobId, zoneId))
                .thenReturn(new DispatchJobResponse(jobId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DispatchJobStatus.ASSIGNED, null));

        mockMvc.perform(post("/api/v1/ops/dispatch/jobs/{jobId}/reassign", jobId)
                        .with(jwt().authorities(() -> "ROLE_OPERATIONS"))
                        .contentType("application/json")
                        .content("{\"operatingZoneId\":\"" + zoneId + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ops/dispatch/jobs/{jobId}/reassign", jobId)
                        .with(jwt().authorities(() -> "ROLE_CUSTOMER"))
                        .contentType("application/json")
                        .content("{\"operatingZoneId\":\"" + zoneId + "\"}"))
                .andExpect(status().isForbidden());
    }
}
