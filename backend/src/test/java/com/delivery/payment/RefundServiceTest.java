package com.delivery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.config.PaymentMockConfirmationProperties;
import com.delivery.payment.dto.RefundRequest;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.entity.Refund;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.mapper.PaymentMapper;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
import com.delivery.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Unit tests for refund request/approval rules (INC-06, AS-06, COR-04). */
class RefundServiceTest {
    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private PaymentRepository paymentRepository;
    private RefundRepository refundRepository;
    private OrderRepository orderRepository;
    private TenantContext tenantContext;
    private AuditLogService auditLogService;
    private DeliveryRepository deliveryRepository;
    private PaymentService service;
    private Payment payment;
    private Order order;
    private final List<Refund> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        refundRepository = mock(RefundRepository.class);
        orderRepository = mock(OrderRepository.class);
        tenantContext = mock(TenantContext.class);
        auditLogService = mock(AuditLogService.class);
        deliveryRepository = mock(DeliveryRepository.class);
        stored.clear();
        service = new PaymentService(paymentRepository, orderRepository, refundRepository, auditLogService,
                new PaymentMapper(), tenantContext, mock(AppUserProfileRepository.class), new PaymentMockConfirmationProperties(), deliveryRepository);

        payment = new Payment(UUID.randomUUID(), tenantA, orderId, new BigDecimal("100.00"), "pay-key");
        payment.confirm();
        order = new Order(orderId, tenantA, "PA-1", UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("90.00"), BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"), "checkout-key", "hash", "000000");
        order.markPaymentConfirmed();

        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantA));
        when(paymentRepository.findByTenantIdAndId(tenantA, payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.findByTenantIdAndIdForUpdate(tenantA, payment.getId())).thenReturn(Optional.of(payment));
        when(orderRepository.findByTenantIdAndId(tenantA, orderId)).thenReturn(Optional.of(order));
        when(refundRepository.findByTenantIdAndPaymentId(tenantA, payment.getId())).thenAnswer(inv -> List.copyOf(stored));
        when(refundRepository.findByTenantIdAndIdempotencyKey(any(), any())).thenAnswer(inv -> stored.stream()
                .filter(r -> r.getTenantId().equals(inv.getArgument(0)) && r.getIdempotencyKey().equals(inv.getArgument(1)))
                .findFirst());
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> {
            Refund refund = inv.getArgument(0);
            stored.add(refund);
            return refund;
        });
        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(inv -> {
            Refund refund = inv.getArgument(0);
            stored.add(refund);
            return refund;
        });
    }

    // ---- AS-06: tenant isolation of approve/reject ----

    @Test
    void financeOfTenantACannotApproveRefundOfTenantB() {
        Refund foreign = foreignRefund();

        assertStatus(() -> service.approveRefund(foreign.getId()), HttpStatus.NOT_FOUND);
        assertThat(foreign.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        verify(refundRepository, never()).findById(any());
    }

    @Test
    void financeOfTenantACannotRejectRefundOfTenantB() {
        Refund foreign = foreignRefund();

        assertStatus(() -> service.rejectRefund(foreign.getId()), HttpStatus.NOT_FOUND);
        assertThat(foreign.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        verify(refundRepository, never()).findById(any());
    }

    // ---- COR-04: amount validation ----

    @Test
    void refundAbovePaymentAmountIsUnprocessable() {
        assertStatus(() -> request("100.01", "k1"), HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(stored).isEmpty();
    }

    @Test
    void zeroOrNegativeRefundIsUnprocessable() {
        assertStatus(() -> request("0", "k1"), HttpStatus.UNPROCESSABLE_ENTITY);
        assertStatus(() -> request("-5", "k2"), HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(stored).isEmpty();
    }

    @Test
    void sumOfNonRejectedRefundsCannotExceedPayment() {
        request("60.00", "k1");
        assertStatus(() -> request("40.01", "k2"), HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(request("40.00", "k3").status()).isEqualTo(RefundStatus.REQUESTED);
    }

    @Test
    void rejectedRefundsDoNotCountTowardsTheCap() {
        RefundResponse first = request("80.00", "k1");
        stubTenantLookup();
        service.rejectRefund(first.id());

        assertThat(request("100.00", "k2").status()).isEqualTo(RefundStatus.REQUESTED);
    }

    // ---- characterization: idempotency by key ----

    @Test
    void sameIdempotencyKeyReturnsTheSameRefundOnce() {
        RefundResponse first = request("30.00", "same-key");
        RefundResponse second = request("30.00", "same-key");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(stored).hasSize(1);
    }

    @Test
    void idempotencyKeyOfAnotherPaymentIsAConflict() {
        stored.add(new Refund(UUID.randomUUID(), tenantA, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, "other",
                RefundStatus.REQUESTED, "taken-key"));

        assertStatus(() -> request("10.00", "taken-key"), HttpStatus.CONFLICT);
    }

    // ---- COR-04: approval drives the order ----

    @Test
    void fullApprovalMarksOrderRefunded() {
        RefundResponse refund = request("100.00", "k1");
        stubTenantLookup();

        assertThat(service.approveRefund(refund.id()).status()).isEqualTo(RefundStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void partialApprovalKeepsOrderStatusAndLaterApprovalCompletingTheTotalMarksRefunded() {
        RefundResponse first = request("40.00", "k1");
        RefundResponse second = request("60.00", "k2");
        stubTenantLookup();

        service.approveRefund(first.id());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);

        service.approveRefund(second.id());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void vendorRejectedOrderInRefundPendingBecomesRefundedOnFullApproval() {
        order.markRefundPending();
        RefundResponse refund = request("100.00", "vendor-rejection-" + orderId);
        stubTenantLookup();

        service.approveRefund(refund.id());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void rejectedRefundCannotBeApproved() {
        RefundResponse refund = request("10.00", "k1");
        stubTenantLookup();
        service.rejectRefund(refund.id());

        assertStatus(() -> service.approveRefund(refund.id()), HttpStatus.CONFLICT);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
    }

    @Test
    void approvingTwiceIsIdempotent() {
        RefundResponse refund = request("100.00", "k1");
        stubTenantLookup();

        service.approveRefund(refund.id());
        assertThat(service.approveRefund(refund.id()).status()).isEqualTo(RefundStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    // ---- seller rejection after partial refund: financial state on Payment ----

    @Test
    void approvalsMovePaymentToPartiallyThenFullyRefunded() {
        RefundResponse first = request("40.00", "k1");
        RefundResponse second = request("60.00", "k2");
        stubTenantLookup();

        service.approveRefund(first.id());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);

        service.approveRefund(second.id());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FULLY_REFUNDED);
    }

    @Test
    void cancelledOrderStaysCancelledOnFullApproval() {
        order.markCancelledBySellerRejection("Sem stock");
        RefundResponse refund = request("100.00", "k1");
        stubTenantLookup();

        service.approveRefund(refund.id());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FULLY_REFUNDED);
        verify(auditLogService, never()).log(org.mockito.ArgumentMatchers.eq("ORDER_REFUNDED"), any(), any(), any(), any());
    }

    // ---- V042 follow-up: the plain PIN on a fully refunded order ----

    @Test
    void fullApprovalClearsThePlainPinWhenNoCourierIsEngaged() {
        // No delivery row at all (refund before dispatch).
        RefundResponse refund = request("100.00", "k1");
        stubTenantLookup();

        service.approveRefund(refund.id());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getDeliveryConfirmationCodeDisplay()).isNull();
    }

    @Test
    void fullApprovalClearsThePlainPinWhenTheDeliveryIsNoLongerCourierEngaged() {
        for (DeliveryStatus status : List.of(DeliveryStatus.DISPATCH_PENDING, DeliveryStatus.REJECTED, DeliveryStatus.DELIVERED,
                DeliveryStatus.FAILED_DELIVERY, DeliveryStatus.CANCELLED)) {
            setUp();
            stubDelivery(status);
            RefundResponse refund = request("100.00", "k-" + status);
            stubTenantLookup();

            service.approveRefund(refund.id());

            assertThat(order.getDeliveryConfirmationCodeDisplay()).as("PIN cleared when delivery is %s", status).isNull();
        }
    }

    @Test
    void fullApprovalKeepsThePlainPinWhileACourierIsEngaged() {
        // DeliveryService.complete checks only the delivery status, so an engaged courier may still complete with the PIN.
        for (DeliveryStatus status : List.of(DeliveryStatus.ASSIGNED, DeliveryStatus.ACCEPTED, DeliveryStatus.ARRIVED_AT_VENDOR,
                DeliveryStatus.REASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.ON_ROUTE_TO_CUSTOMER, DeliveryStatus.ARRIVED_AT_CUSTOMER)) {
            setUp();
            stubDelivery(status);
            RefundResponse refund = request("100.00", "k-" + status);
            stubTenantLookup();

            service.approveRefund(refund.id());

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(order.getDeliveryConfirmationCodeDisplay()).as("PIN kept while delivery is %s", status).isEqualTo("000000");
        }
    }

    @Test
    void partialApprovalLeavesThePlainPinAlone() {
        RefundResponse first = request("40.00", "k1");
        stubTenantLookup();

        service.approveRefund(first.id());

        assertThat(order.getDeliveryConfirmationCodeDisplay()).isEqualTo("000000");
    }

    @Test
    void remainingRefundCoversOnlyWhatIsLeftAndNothingWhenFullyCovered() {
        request("30.00", "k1");

        assertThat(service.requestRemainingRefund(payment.getId(), "reject", "seller-reject"))
                .hasValueSatisfying(r -> assertThat(r.amount()).isEqualByComparingTo("70.00"));
        // Same key again: the existing refund, no duplicate.
        assertThat(service.requestRemainingRefund(payment.getId(), "reject", "seller-reject"))
                .hasValueSatisfying(r -> assertThat(r.amount()).isEqualByComparingTo("70.00"));
        assertThat(service.requestRemainingRefund(payment.getId(), "reject", "other-key")).isEmpty();
        assertThat(stored).hasSize(2);
    }

    /** The order's single delivery row (uq_deliveries_tenant_order) in the given status. */
    private void stubDelivery(DeliveryStatus status) {
        Delivery delivery = new Delivery(UUID.randomUUID(), tenantA, orderId, "hash");
        delivery.updateStatus(status);
        when(deliveryRepository.findByTenantIdAndOrderId(tenantA, orderId)).thenReturn(Optional.of(delivery));
    }

    private RefundResponse request(String amount, String key) {
        return service.requestRefund(payment.getId(), new RefundRequest(new BigDecimal(amount), "reason", key));
    }

    /** Routes tenant-scoped refund lookups to the in-memory store. */
    private void stubTenantLookup() {
        when(refundRepository.findByTenantIdAndId(any(), any())).thenAnswer(inv -> stored.stream()
                .filter(r -> r.getTenantId().equals(inv.getArgument(0)) && r.getId().equals(inv.getArgument(1)))
                .findFirst());
    }

    /** A refund of tenant B that an unscoped findById would happily return. */
    private Refund foreignRefund() {
        Refund foreign = new Refund(UUID.randomUUID(), tenantB, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, "foreign",
                RefundStatus.REQUESTED, "foreign-key");
        when(refundRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        when(refundRepository.findByTenantIdAndId(tenantA, foreign.getId())).thenReturn(Optional.empty());
        return foreign;
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(status));
    }
}
