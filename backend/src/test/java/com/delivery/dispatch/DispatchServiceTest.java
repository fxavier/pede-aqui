package com.delivery.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.repository.DeliveryEventRepository;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.DispatchJob;
import com.delivery.dispatch.entity.DispatchJobStatus;
import com.delivery.dispatch.mapper.DispatchMapper;
import com.delivery.dispatch.repository.DispatchJobRepository;
import com.delivery.dispatch.service.CourierService;
import com.delivery.dispatch.service.DispatchService;
import com.delivery.order.entity.Order;
import com.delivery.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/** Unit coverage of DispatchService guards (INC-03: SEC-02, COR-02, COR-08) with mocked persistence. */
class DispatchServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID zoneId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();

    private CourierService courierService;
    private DispatchJobRepository jobRepository;
    private DeliveryRepository deliveryRepository;
    private OrderRepository orderRepository;
    private Courier first;
    private Courier second;
    private Order order;
    private Delivery delivery;
    private DispatchService service;

    @BeforeEach
    void setUp() {
        first = approvedCourier();
        second = approvedCourier();
        courierService = mock(CourierService.class);
        when(courierService.eligibleInZone(zoneId)).thenReturn(List.of(first, second));
        when(courierService.requireMine()).thenReturn(first);

        jobRepository = mock(DispatchJobRepository.class);
        when(jobRepository.save(any(DispatchJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepository.saveAndFlush(any(DispatchJob.class))).thenAnswer(inv -> inv.getArgument(0));

        deliveryRepository = mock(DeliveryRepository.class);
        delivery = new Delivery(deliveryId, tenantId, orderId, "hash");
        when(deliveryRepository.findByTenantIdAndId(tenantId, deliveryId)).thenReturn(Optional.of(delivery));

        orderRepository = mock(OrderRepository.class);
        order = new Order(orderId, tenantId, "PA-1", UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10"), "k", "h", "111111");
        when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));

        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));

        service = new DispatchService(
                jobRepository,
                courierService,
                deliveryRepository,
                mock(DeliveryEventRepository.class),
                orderRepository,
                new DispatchMapper(),
                mock(AuditLogService.class),
                tenantContext,
                Duration.ofSeconds(60),
                com.delivery.order.OrderTestStatus.stateMachine());
    }

    private Courier approvedCourier() {
        Courier courier = new Courier(UUID.randomUUID(), tenantId, UUID.randomUUID(), zoneId);
        courier.approve();
        courier.setAvailable(true);
        return courier;
    }

    /** Characterization: the happy path assign → reject (by the assignee) → reassign still works. */
    @Test
    void coversAssignmentRejectionAndReassignment() {
        com.delivery.order.OrderTestStatus.force(order, com.delivery.order.entity.OrderStatus.READY_FOR_PICKUP);
        var assigned = service.assign(orderId, deliveryId, zoneId);
        UUID jobId = UUID.randomUUID();
        DispatchJob job = new DispatchJob(jobId, tenantId, orderId, deliveryId, assigned.courierId());
        when(jobRepository.findByTenantIdAndId(tenantId, jobId)).thenReturn(Optional.of(job));

        var rejected = service.reject(jobId, "Muito longe");
        var reassigned = service.reassign(jobId, zoneId);

        assertThat(assigned.status()).isEqualTo(DispatchJobStatus.ASSIGNED);
        assertThat(assigned.courierId()).isEqualTo(first.getId());
        assertThat(rejected.status()).isEqualTo(DispatchJobStatus.REASSIGNABLE);
        assertThat(reassigned.status()).isEqualTo(DispatchJobStatus.ASSIGNED);
        assertThat(reassigned.courierId()).isEqualTo(second.getId());
    }

    @Test
    void acceptOfJobAssignedToAnotherCourierIsNotFound() {
        UUID jobId = UUID.randomUUID();
        DispatchJob job = new DispatchJob(jobId, tenantId, orderId, deliveryId, second.getId());
        when(jobRepository.findByTenantIdAndId(tenantId, jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.accept(jobId)).isInstanceOf(NotFoundException.class);
        assertThat(job.getStatus()).isEqualTo(DispatchJobStatus.ASSIGNED);
    }

    @Test
    void rejectOfJobAssignedToAnotherCourierIsNotFound() {
        UUID jobId = UUID.randomUUID();
        DispatchJob job = new DispatchJob(jobId, tenantId, orderId, deliveryId, second.getId());
        when(jobRepository.findByTenantIdAndId(tenantId, jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.reject(jobId, "x")).isInstanceOf(NotFoundException.class);
        assertThat(job.getStatus()).isEqualTo(DispatchJobStatus.ASSIGNED);
    }

    @Test
    void rejectOfAcceptedJobIsConflict() {
        UUID jobId = UUID.randomUUID();
        DispatchJob job = new DispatchJob(jobId, tenantId, orderId, deliveryId, first.getId());
        job.accept();
        when(jobRepository.findByTenantIdAndId(tenantId, jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.reject(jobId, "x"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(job.getStatus()).isEqualTo(DispatchJobStatus.ACCEPTED);
    }

    @Test
    void exclusivityIndexViolationsAreMappedToConflictButOtherIntegrityErrorsPropagate() {
        com.delivery.order.OrderTestStatus.force(order, com.delivery.order.entity.OrderStatus.READY_FOR_PICKUP);
        doThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_deliveries_active_courier\""))
                .when(jobRepository).flush();
        assertThatThrownBy(() -> service.assign(orderId, deliveryId, zoneId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(((BusinessException) e).getCode()).isEqualTo("courier_busy");
                });

        com.delivery.order.OrderTestStatus.force(order, com.delivery.order.entity.OrderStatus.READY_FOR_PICKUP);
        doThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_dispatch_jobs_active_delivery\""))
                .when(jobRepository).flush();
        assertThatThrownBy(() -> service.assign(orderId, deliveryId, zoneId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("dispatch_job_already_active"));

        com.delivery.order.OrderTestStatus.force(order, com.delivery.order.entity.OrderStatus.READY_FOR_PICKUP);
        doThrow(new DataIntegrityViolationException("violates foreign key constraint \"fk_other\""))
                .when(jobRepository).flush();
        assertThatThrownBy(() -> service.assign(orderId, deliveryId, zoneId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void assignOfOrderNotReadyForPickupIsConflictAndCreatesNoJob() {
        com.delivery.order.OrderTestStatus.force(order, com.delivery.order.entity.OrderStatus.PREPARING);

        assertThatThrownBy(() -> service.assign(orderId, deliveryId, zoneId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(jobRepository, never()).save(any(DispatchJob.class));
        verify(jobRepository, never()).saveAndFlush(any(DispatchJob.class));
    }
}
