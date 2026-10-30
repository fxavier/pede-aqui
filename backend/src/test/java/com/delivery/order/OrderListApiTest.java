package com.delivery.order;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.security.SecurityConfig;
import com.delivery.order.controller.OrderController;
import com.delivery.order.dto.AdminOrderPageResponse;
import com.delivery.order.service.OrderService;
import com.delivery.order.service.OrderTrackingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/** INC-04: the legacy array contract of GET /orders is kept; GET /orders/page is the additive paginated variant. */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderListApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderTrackingService trackingService;

    @MockBean
    private com.delivery.order.service.OrderListingService listingService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void legacyListStillReturnsAJsonArray() throws Exception {
        when(orderService.listForCurrentContext()).thenReturn(new com.delivery.common.pagination.CappedList<>(List.of(), false));

        mockMvc.perform(get("/api/v1/orders").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VENDOR_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void pagedListForwardsPagingParamsForVendorStaff() throws Exception {
        when(orderService.listPageForCurrentContext(2, 50)).thenReturn(new AdminOrderPageResponse(List.of(), 2, 50, 0));

        mockMvc.perform(get("/api/v1/orders/page").param("page", "2").param("size", "50")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VENDOR_STAFF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(50));

        verify(orderService).listPageForCurrentContext(2, 50);
    }

    @Test
    void pagedListDefaultsToFirstPageOfTwenty() throws Exception {
        when(orderService.listPageForCurrentContext(0, 20)).thenReturn(new AdminOrderPageResponse(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/orders/page").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPS"))))
                .andExpect(status().isOk());

        verify(orderService).listPageForCurrentContext(0, 20);
    }

    @Test
    void customerCannotListOrders() throws Exception {
        mockMvc.perform(get("/api/v1/orders/page").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderService);
    }
}
