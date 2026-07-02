package com.delivery.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.common.security.TenantContext;
import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.repository.DeliveryEventRepository;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.DispatchJob;
import com.delivery.dispatch.mapper.DispatchMapper;
import com.delivery.dispatch.repository.DispatchJobRepository;
import com.delivery.dispatch.service.CourierService;
import com.delivery.dispatch.service.DispatchService;
import com.delivery.order.entity.Order;
import com.delivery.order.repository.OrderRepository;
import com.delivery.common.service.AuditLogService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispatchServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID zoneId = UUID.randomUUID();

    @Test
    void coversAssignmentRejectionAndReassignment() {
        UUID orderId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        UUID firstCourierId = UUID.randomUUID();
        UUID secondCourierId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        Courier first = new Courier(firstCourierId, tenantId, UUID.randomUUID(), zoneId);
        first.approve();
        first.setAvailable(true);
        Courier second = new Courier(secondCourierId, tenantId, UUID.randomUUID(), zoneId);
        second.approve();
        second.setAvailable(true);

        CourierService courierService = mock(CourierService.class);
        when(courierService.eligibleInZone(zoneId)).thenReturn(List.of(first, second));

        DispatchJobRepository jobRepository = mock(DispatchJobRepository.class);
        when(jobRepository.save(any(DispatchJob.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
        Delivery delivery = new Delivery(deliveryId, tenantId, orderId, "hash");
        when(deliveryRepository.findByTenantIdAndId(tenantId, deliveryId)).thenReturn(Optional.of(delivery));

        OrderRepository orderRepository = mock(OrderRepository.class);
        Order order = new Order(orderId, tenantId, "PA-1", UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10"), "k", "h", "111111");
        when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));

        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));

        DispatchService service = new DispatchService(
                jobRepository,
                courierService,
                deliveryRepository,
                mock(DeliveryEventRepository.class),
                orderRepository,
                new DispatchMapper(),
                mock(AuditLogService.class),
                tenantContext);

        var assigned = service.assign(orderId, deliveryId, zoneId);
        DispatchJob job = new DispatchJob(jobId, tenantId, orderId, deliveryId, assigned.courierId());
        when(jobRepository.findByTenantIdAndId(tenantId, jobId)).thenReturn(Optional.of(job));

        var rejected = service.reject(jobId, "Muito longe");
        var reassigned = service.reassign(jobId, zoneId);

        assertThat(assigned.status().name()).isEqualTo("ASSIGNED");
        assertThat(rejected.status().name()).isEqualTo("REASSIGNABLE");
        assertThat(reassigned.status().name()).isEqualTo("ASSIGNED");
        assertThat(reassigned.courierId()).isEqualTo(secondCourierId);
    }
}
