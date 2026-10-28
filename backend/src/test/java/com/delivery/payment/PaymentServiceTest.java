package com.delivery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.config.PaymentMockConfirmationProperties;
import com.delivery.payment.dto.RefundRequest;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.entity.Refund;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.mapper.PaymentMapper;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
import com.delivery.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests for mock payment confirmation (INC-01, AS-01) and refund requests. */
class PaymentServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID ownerProfileId = UUID.randomUUID();
    private final String ownerSubject = "kc-owner";

    private PaymentRepository paymentRepository;
    private OrderRepository orderRepository;
    private AuditLogService auditLogService;
    private TenantContext tenantContext;
    private AppUserProfileRepository userProfileRepository;
    private PaymentMockConfirmationProperties properties;
    private PaymentService service;
    private Payment payment;
    private Order order;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        orderRepository = mock(OrderRepository.class);
        auditLogService = mock(AuditLogService.class);
        tenantContext = mock(TenantContext.class);
        userProfileRepository = mock(AppUserProfileRepository.class);
        properties = new PaymentMockConfirmationProperties();
        properties.setEnabled(true);
        service = new PaymentService(paymentRepository, orderRepository, mock(RefundRepository.class), auditLogService,
                new PaymentMapper(), tenantContext, userProfileRepository, properties, mock(com.delivery.delivery.repository.DeliveryRepository.class), mock(com.delivery.order.service.OrderStockService.class));

        payment = new Payment(UUID.randomUUID(), tenantId, orderId, new BigDecimal("21.50"), "pay-key");
        order = new Order(orderId, tenantId, "PA-1", ownerProfileId, UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO, new BigDecimal("12.00"), "checkout-key", "hash", "000000");
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(paymentRepository.findByTenantIdAndIdempotencyKey(tenantId, "pay-key")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByTenantIdAndId(tenantId, payment.getId())).thenReturn(Optional.of(payment));
        when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
        when(userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, ownerSubject)).thenReturn(Optional.of(
                new AppUserProfile(ownerProfileId, tenantId, ownerSubject, "owner@example.test", "Owner", Set.of())));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- characterization: behaviour that must be preserved ----

    @Test
    void duplicateConfirmationReturnsOneConfirmedResultAndAuditsOnce() {
        authenticateAs(ownerSubject, "ROLE_CUSTOMER");

        assertThat(service.confirm(payment.getId(), "pay-key").status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(service.confirm(payment.getId(), "pay-key").status()).isEqualTo(PaymentStatus.CONFIRMED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
        verify(auditLogService, times(1)).log("PAYMENT_CONFIRMED", "payment", payment.getId().toString(), order.getReference(), "SUCCESS");
    }

    @Test
    void idempotencyKeyOfAnotherPaymentIsAConflict() {
        authenticateAs(ownerSubject, "ROLE_CUSTOMER");

        assertThatThrownBy(() -> service.confirm(UUID.randomUUID(), "pay-key"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING_CONFIRMATION);
    }

    @Test
    void paymentOfAnotherTenantIsNotFound() {
        authenticateAs("kc-ops", "ROLE_OPS");
        UUID foreignPaymentId = UUID.randomUUID();
        when(paymentRepository.findByTenantIdAndIdempotencyKey(tenantId, "foreign-key")).thenReturn(Optional.empty());
        when(paymentRepository.findByTenantIdAndId(tenantId, foreignPaymentId)).thenReturn(Optional.empty());

        assertNotFound(() -> service.confirm(foreignPaymentId, "foreign-key"));
    }

    @Test
    void vendorRejectionRefundFlowCreatesRefundRequest() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        Payment refundable = new Payment(paymentId, tenantId, orderId, new BigDecimal("100.00"), "pay-key-2");
        RefundRepository refundRepository = mock(RefundRepository.class);
        when(paymentRepository.findByTenantIdAndIdForUpdate(tenantId, paymentId)).thenReturn(Optional.of(refundable));

        String idempotencyKey = "vendor-rejection-" + orderId;
        Refund refund = new Refund(refundId, tenantId, paymentId, orderId, new BigDecimal("100.00"),
                "Vendor rejection: Sem ingredientes", RefundStatus.REQUESTED, idempotencyKey);
        when(refundRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)).thenReturn(Optional.empty());
        when(refundRepository.saveAndFlush(any(Refund.class))).thenReturn(refund);

        PaymentService refundService = new PaymentService(paymentRepository, mock(OrderRepository.class), refundRepository,
                mock(AuditLogService.class), new PaymentMapper(), tenantContext, userProfileRepository, properties, mock(com.delivery.delivery.repository.DeliveryRepository.class), mock(com.delivery.order.service.OrderStockService.class));

        var response = refundService.requestRefund(paymentId, new RefundRequest(new BigDecimal("100.00"), "Vendor rejection: Sem ingredientes", idempotencyKey));

        assertThat(response.status()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(response.reason()).isEqualTo("Vendor rejection: Sem ingredientes");
        assertThat(response.amount()).isEqualTo(new BigDecimal("100.00"));
    }

    // ---- AS-01: defect reproduction ----

    @Test
    void disabledFlagHidesTheEndpointForEveryone() {
        properties.setEnabled(false);
        authenticateAs("kc-admin", "ROLE_ADMIN");

        assertNotFound(() -> service.confirm(payment.getId(), "pay-key"));
        verifyNoInteractions(paymentRepository, orderRepository, auditLogService);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING_CONFIRMATION);
    }

    @Test
    void anotherCustomerOfTheSameTenantCannotConfirm() {
        UUID otherProfileId = UUID.randomUUID();
        when(userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, "kc-other")).thenReturn(Optional.of(
                new AppUserProfile(otherProfileId, tenantId, "kc-other", "other@example.test", "Other", Set.of())));
        authenticateAs("kc-other", "ROLE_CUSTOMER");

        assertNotFound(() -> service.confirm(payment.getId(), "pay-key"));
        assertUnchanged();
    }

    @Test
    void vendorAdminCannotConfirm() {
        authenticateAs("kc-vendor", "ROLE_VENDOR_ADMIN");

        assertForbidden(() -> service.confirm(payment.getId(), "pay-key"));
        assertUnchanged();
    }

    @Test
    void courierCannotConfirm() {
        authenticateAs("kc-courier", "ROLE_COURIER");

        assertForbidden(() -> service.confirm(payment.getId(), "pay-key"));
        assertUnchanged();
    }

    @Test
    void opsAndAdminCanConfirmOnBehalfOfTheCustomer() {
        authenticateAs("kc-ops", "ROLE_OPS");
        assertThat(service.confirm(payment.getId(), "pay-key").status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);

        Payment second = new Payment(UUID.randomUUID(), tenantId, orderId, BigDecimal.TEN, "pay-key-admin");
        ReflectionTestUtils.setField(order, "status", OrderStatus.PAYMENT_PENDING);
        when(paymentRepository.findByTenantIdAndIdempotencyKey(tenantId, "pay-key-admin")).thenReturn(Optional.of(second));
        authenticateAs("kc-admin", "ROLE_ADMIN");
        assertThat(service.confirm(second.getId(), "pay-key-admin").status()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    void paymentThatIsNoLongerPendingIsAConflict() {
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.FAILED);
        authenticateAs(ownerSubject, "ROLE_CUSTOMER");

        assertThatThrownBy(() -> service.confirm(payment.getId(), "pay-key"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(auditLogService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void cancelledOrderCannotBeRevivedByConfirmingItsPayment() {
        order.markCancelled();
        authenticateAs(ownerSubject, "ROLE_CUSTOMER");

        assertThatThrownBy(() -> service.confirm(payment.getId(), "pay-key"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING_CONFIRMATION);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private void authenticateAs(String subject, String... authorities) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(subject, null, authorities);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of(subject));
    }

    private void assertUnchanged() {
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING_CONFIRMATION);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        verify(auditLogService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
