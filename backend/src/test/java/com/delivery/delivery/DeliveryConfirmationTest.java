package com.delivery.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.mapper.DeliveryMapper;
import com.delivery.delivery.repository.DeliveryEventRepository;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.delivery.service.DeliveryService;
import com.delivery.order.entity.Order;
import com.delivery.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryConfirmationTest {
    @Test
    void correctCodeDeliversOrderAndDelivery() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Delivery delivery = new Delivery(UUID.randomUUID(), tenantId, orderId, hash("123456"));
        Order order = new Order(orderId, tenantId, "PA-2", UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, new BigDecimal("12.00"), "checkout-key", hash("123456"), "123456");
        DeliveryService service = service(tenantId, delivery, order);

        assertThat(service.complete(delivery.getId(), "123456").status()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void incorrectCodeIsRejectedAndAttemptIsRecorded() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Delivery delivery = new Delivery(UUID.randomUUID(), tenantId, UUID.randomUUID(), hash("123456"));
        DeliveryService service = service(tenantId, delivery, null);

        assertThatThrownBy(() -> service.complete(delivery.getId(), "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Delivery confirmation code is invalid");
        assertThat(delivery.getConfirmationAttempts()).isEqualTo(1);
    }

    private DeliveryService service(UUID tenantId, Delivery delivery, Order order) {
        DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
        DeliveryEventRepository eventRepository = mock(DeliveryEventRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(deliveryRepository.findByTenantIdAndId(tenantId, delivery.getId())).thenReturn(Optional.of(delivery));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        if (order != null) when(orderRepository.findByTenantIdAndId(tenantId, order.getId())).thenReturn(Optional.of(order));
        return new DeliveryService(deliveryRepository, eventRepository, orderRepository, new DeliveryMapper(), tenantContext);
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
