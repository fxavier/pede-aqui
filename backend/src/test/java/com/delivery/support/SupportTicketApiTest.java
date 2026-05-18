package com.delivery.support;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import com.delivery.common.security.SecurityConfig;
import com.delivery.support.controller.SupportTicketController;
import com.delivery.support.dto.SupportTicketResponse;
import com.delivery.support.entity.IncidentClassification;
import com.delivery.support.entity.SupportTicketStatus;
import com.delivery.support.service.SupportTicketService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SupportTicketController.class)
@Import(SecurityConfig.class)
class SupportTicketApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SupportTicketService supportTicketService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void customerCannotSeeInternalNotesAndSupportCan() throws Exception {
        UUID id = UUID.randomUUID();
        SupportTicketResponse customerView = new SupportTicketResponse(id, UUID.randomUUID(), "Atraso", "Pedido atrasado", SupportTicketStatus.OPEN, IncidentClassification.DELIVERY, null, null, Instant.now());
        SupportTicketResponse supportView = new SupportTicketResponse(id, UUID.randomUUID(), "Atraso", "Pedido atrasado", SupportTicketStatus.OPEN, IncidentClassification.DELIVERY, "nota interna", "support-1", Instant.now());

        when(supportTicketService.listMine()).thenReturn(List.of(customerView));
        when(supportTicketService.listBackoffice()).thenReturn(List.of(supportView));

        mockMvc.perform(get("/api/v1/support/tickets/mine").with(jwt().authorities(() -> "ROLE_CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].internalNote").value(nullValue()));

        mockMvc.perform(get("/api/v1/support/tickets").with(jwt().authorities(() -> "ROLE_SUPPORT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].internalNote").value("nota interna"));
    }
}
