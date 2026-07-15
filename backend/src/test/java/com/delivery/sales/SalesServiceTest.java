package com.delivery.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.MarketplaceRole;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.notification.service.NotificationService;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderItem;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.dto.RefundRequest;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.entity.Refund;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.service.PaymentService;
import com.delivery.sales.repository.PaymentSummary;
import com.delivery.sales.config.SalesStatusOverrideProperties;
import com.delivery.sales.dto.SalesNotificationType;
import com.delivery.sales.dto.SalesPageResponse;
import com.delivery.sales.dto.SalesSearchFilter;
import com.delivery.sales.repository.SalesCommissionReadRepository;
import com.delivery.sales.repository.SalesOrderSearchRepository;
import com.delivery.sales.repository.SalesPaymentReadRepository;
import com.delivery.sales.repository.SalesRefundReadRepository;
import com.delivery.sales.service.SalesAccessGuard;
import com.delivery.sales.service.SalesService;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class SalesServiceTest {
    private static final String OTP = "123456";

    private final UUID tenantId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    private OrderRepository orderRepository;
    private SalesOrderSearchRepository searchRepository;
    private SalesPaymentReadRepository paymentReadRepository;
    private SalesRefundReadRepository refundReadRepository;
    private SalesCommissionReadRepository commissionReadRepository;
    private VendorRepository vendorRepository;
    private AppUserProfileRepository userProfileRepository;
    private PaymentService paymentService;
    private NotificationService notificationService;
    private AuditLogService auditLogService;
    private TenantContext tenantContext;
    private SalesStatusOverrideProperties overrideProperties;
    private SalesService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        searchRepository = mock(SalesOrderSearchRepository.class);
        paymentReadRepository = mock(SalesPaymentReadRepository.class);
        refundReadRepository = mock(SalesRefundReadRepository.class);
        commissionReadRepository = mock(SalesCommissionReadRepository.class);
        vendorRepository = mock(VendorRepository.class);
        userProfileRepository = mock(AppUserProfileRepository.class);
        paymentService = mock(PaymentService.class);
        notificationService = mock(NotificationService.class);
        auditLogService = mock(AuditLogService.class);
        tenantContext = mock(TenantContext.class);
        overrideProperties = new SalesStatusOverrideProperties();
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        service = new SalesService(orderRepository, searchRepository, paymentReadRepository, refundReadRepository,
                commissionReadRepository, vendorRepository, userProfileRepository, paymentService, notificationService,
                auditLogService, tenantContext, overrideProperties, new SalesAccessGuard(vendorRepository));
        authenticateWithRoles("ROLE_ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWithRoles(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "n/a", roles));
    }

    private Order order(OrderStatus status) {
        Order order = new Order(UUID.randomUUID(), tenantId, "PA-1", customerId, vendorId,
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("5.00"), BigDecimal.ZERO,
                new BigDecimal("115.00"), "chk-1", "hash", OTP);
        ReflectionTestUtils.setField(order, "status", status);
        when(orderRepository.findByTenantIdAndId(tenantId, order.getId())).thenReturn(Optional.of(order));
        return order;
    }

    // --- C7: cancel state-machine guard ---

    @Test
    void cancelSucceedsInEveryPreDispatchState() {
        for (OrderStatus status : List.of(OrderStatus.PENDING, OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_CONFIRMED,
                OrderStatus.ACCEPTED_BY_VENDOR, OrderStatus.PREPARING)) {
            Order order = order(status);
            var response = service.cancel(order.getId(), "customer asked");
            assertThat(response.orderStatus()).isEqualTo("CANCELLED");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
        verify(auditLogService, org.mockito.Mockito.times(5)).log(eq("SALE_CANCELLED"), eq("order"), anyString(), anyString(), eq("SUCCESS"));
    }

    @Test
    void cancelRejectedWith409AfterDispatch() {
        for (OrderStatus status : List.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.DISPATCH_PENDING, OrderStatus.ASSIGNED_TO_COURIER,
                OrderStatus.PICKED_UP, OrderStatus.DELIVERING, OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REFUNDED)) {
            Order order = order(status);
            assertThatThrownBy(() -> service.cancel(order.getId(), "too late"))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
            assertThat(order.getStatus()).isEqualTo(status);
        }
    }

    // --- C7: refund cap + idempotency ---

    @Test
    void refundRejectsAmountAbovePaidMinusAlreadyRefunded() {
        Order order = order(OrderStatus.DELIVERED);
        PaymentSummary payment = payment(order, "115.00", PaymentStatus.CONFIRMED);
        when(paymentReadRepository.findByTenantIdAndOrderId(tenantId, order.getId())).thenReturn(List.of(payment));
        when(refundReadRepository.findByTenantIdAndIdempotencyKey(eq(tenantId), anyString())).thenReturn(Optional.empty());
        when(refundReadRepository.findByTenantIdAndOrderId(tenantId, order.getId())).thenReturn(List.of(
                new Refund(UUID.randomUUID(), tenantId, payment.getId(), order.getId(), new BigDecimal("30.00"), "prior", RefundStatus.REQUESTED, "k-prior")));

        assertThatThrownBy(() -> service.refund(order.getId(), new BigDecimal("90.00"), "over cap", "k-1"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(paymentService, never()).requestRefund(any(), any());
    }

    @Test
    void refundWithoutAmountRefundsTheRemainingPaidBalance() {
        Order order = order(OrderStatus.DELIVERED);
        PaymentSummary payment = payment(order, "115.00", PaymentStatus.CONFIRMED);
        when(paymentReadRepository.findByTenantIdAndOrderId(tenantId, order.getId())).thenReturn(List.of(payment));
        when(refundReadRepository.findByTenantIdAndIdempotencyKey(eq(tenantId), anyString())).thenReturn(Optional.empty());
        when(refundReadRepository.findByTenantIdAndOrderId(tenantId, order.getId())).thenReturn(List.of(
                new Refund(UUID.randomUUID(), tenantId, payment.getId(), order.getId(), new BigDecimal("15.00"), "prior", RefundStatus.REFUNDED, "k-prior")));
        when(paymentService.requestRefund(eq(payment.getId()), any())).thenAnswer(invocation -> {
            RefundRequest request = invocation.getArgument(1);
            return new RefundResponse(UUID.randomUUID(), payment.getId(), order.getId(), request.amount(), request.reason(), RefundStatus.REQUESTED);
        });

        RefundResponse response = service.refund(order.getId(), null, "full refund", "k-2");

        assertThat(response.amount()).isEqualByComparingTo("100.00");
        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentService).requestRefund(eq(payment.getId()), captor.capture());
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("k-2");
        verify(auditLogService).log(eq("SALE_REFUNDED"), eq("order"), eq(order.getId().toString()), anyString(), eq("SUCCESS"));
    }

    @Test
    void refundRetryWithSameIdempotencyKeyReturnsSameRefundWithoutDoubleRefund() {
        Order order = order(OrderStatus.DELIVERED);
        Refund existing = new Refund(UUID.randomUUID(), tenantId, UUID.randomUUID(), order.getId(),
                new BigDecimal("40.00"), "first try", RefundStatus.REQUESTED, "retry-key");
        when(refundReadRepository.findByTenantIdAndIdempotencyKey(tenantId, "retry-key")).thenReturn(Optional.of(existing));

        RefundResponse response = service.refund(order.getId(), new BigDecimal("40.00"), "first try", "retry-key");

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.amount()).isEqualByComparingTo("40.00");
        verify(paymentService, never()).requestRefund(any(), any());
    }

    @Test
    void refundKeyBoundToAnotherOrderConflicts() {
        Order order = order(OrderStatus.DELIVERED);
        Refund foreign = new Refund(UUID.randomUUID(), tenantId, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10.00"), "other", RefundStatus.REQUESTED, "shared-key");
        when(refundReadRepository.findByTenantIdAndIdempotencyKey(tenantId, "shared-key")).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.refund(order.getId(), new BigDecimal("10.00"), "dup", "shared-key"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void refundWithoutCapturedPaymentIs422() {
        Order order = order(OrderStatus.PAYMENT_PENDING);
        PaymentSummary pending = payment(order, "115.00", PaymentStatus.PENDING_CONFIRMATION);
        when(paymentReadRepository.findByTenantIdAndOrderId(tenantId, order.getId())).thenReturn(List.of(pending));
        when(refundReadRepository.findByTenantIdAndIdempotencyKey(eq(tenantId), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refund(order.getId(), null, "nothing paid", "k-3"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // --- C7: resend notification never leaks the OTP outside the customer's message ---

    @Test
    void resendDeliveryCodeSendsOtpOnlyToCustomerAndNeverAuditsIt() {
        Order order = order(OrderStatus.DELIVERING);

        service.resendNotification(order.getId(), SalesNotificationType.DELIVERY_CODE);

        ArgumentCaptor<UUID> recipient = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> role = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(recipient.capture(), role.capture(), eq("order_delivery_code"), anyString(), anyString(), eq(order.getReference()));
        assertThat(recipient.getValue()).isEqualTo(customerId);
        assertThat(role.getValue()).isEqualTo("CUSTOMER");

        ArgumentCaptor<String> auditReference = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(eq("SALE_NOTIFICATION_RESENT"), eq("order"), eq(order.getId().toString()), auditReference.capture(), eq("SUCCESS"));
        assertThat(auditReference.getValue()).doesNotContain(OTP);
    }

    @Test
    void resendStatusNotificationTargetsTheCustomer() {
        Order order = order(OrderStatus.PREPARING);

        service.resendNotification(order.getId(), SalesNotificationType.STATUS);

        verify(notificationService).create(eq(customerId), eq("CUSTOMER"), eq("order_status"), anyString(), anyString(), eq(order.getReference()));
    }

    // --- C7: status override gate + allow-list ---

    @Test
    void statusOverrideIsForbiddenWhenGateIsOff() {
        overrideProperties.setEnabled(false);
        Order order = order(OrderStatus.PAYMENT_CONFIRMED);

        assertThatThrownBy(() -> service.statusOverride(order.getId(), "CANCELLED", "stuck"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
    }

    @Test
    void statusOverrideAppliesOnlyAllowListedTransitions() {
        overrideProperties.setEnabled(true);

        Order confirmed = order(OrderStatus.PAYMENT_CONFIRMED);
        assertThat(service.statusOverride(confirmed.getId(), "CANCELLED", "refund path").orderStatus()).isEqualTo("CANCELLED");

        Order accepted = order(OrderStatus.ACCEPTED_BY_VENDOR);
        assertThat(service.statusOverride(accepted.getId(), "PREPARING", "unstick vendor").orderStatus()).isEqualTo("PREPARING");

        Order dispatchPending = order(OrderStatus.DISPATCH_PENDING);
        assertThat(service.statusOverride(dispatchPending.getId(), "ASSIGNED_TO_COURIER", "manual dispatch").orderStatus()).isEqualTo("ASSIGNED_TO_COURIER");

        Order delivering = order(OrderStatus.DELIVERING);
        assertThat(service.statusOverride(delivering.getId(), "DELIVERED", "courier device died").orderStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void statusOverrideRejectsTransitionsOutsideTheAllowList() {
        overrideProperties.setEnabled(true);
        Order order = order(OrderStatus.PAYMENT_CONFIRMED);

        assertThatThrownBy(() -> service.statusOverride(order.getId(), "DELIVERED", "shortcut"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
    }

    @Test
    void statusOverrideRetryToTheSameTargetIsANoOp() {
        overrideProperties.setEnabled(true);
        Order order = order(OrderStatus.PAYMENT_CONFIRMED);

        service.statusOverride(order.getId(), "CANCELLED", "first");
        var retry = service.statusOverride(order.getId(), "CANCELLED", "first");

        assertThat(retry.orderStatus()).isEqualTo("CANCELLED");
        verify(auditLogService, org.mockito.Mockito.times(1)).log(eq("SALE_STATUS_OVERRIDDEN"), eq("order"), anyString(), anyString(), eq("SUCCESS"));
    }

    @Test
    void statusOverrideRejectsUnknownTargetStatus() {
        overrideProperties.setEnabled(true);
        Order order = order(OrderStatus.PAYMENT_CONFIRMED);

        assertThatThrownBy(() -> service.statusOverride(order.getId(), "NOT_A_STATUS", "typo"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- C7: tenant + vendor isolation ---

    @Test
    void ordersOfAnotherTenantAreNotFound() {
        UUID foreignOrderId = UUID.randomUUID();
        when(orderRepository.findByTenantIdAndId(tenantId, foreignOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(foreignOrderId, "cross-tenant"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void vendorAdminCannotActOnAnotherVendorsOrder() {
        authenticateWithRoles("ROLE_VENDOR_ADMIN");
        UUID ownVendorId = UUID.randomUUID();
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(
                new Vendor(ownVendorId, tenantId, "Mine", null, null, null, "Owner", null, null, null, null, null)));
        Order foreignVendorOrder = order(OrderStatus.PAYMENT_CONFIRMED);

        assertThatThrownBy(() -> service.cancel(foreignVendorOrder.getId(), "not mine"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(foreignVendorOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
    }

    @Test
    void vendorAdminSeesOwnVendorsOrderDetail() {
        authenticateWithRoles("ROLE_VENDOR_ADMIN");
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(
                new Vendor(vendorId, tenantId, "Mine", null, null, null, "Owner", null, null, null, null, null)));
        Order order = order(OrderStatus.DELIVERED);
        stubLookupsFor(order);

        var detail = service.detail(order.getId());

        assertThat(detail.vendorId()).isEqualTo(vendorId);
    }

    // --- C7: search projection, SUPPORT masking, totals reconciliation ---

    @Test
    void searchMasksCustomerPiiForSupportAndReconcilesTotals() {
        authenticateWithRoles("ROLE_SUPPORT");
        Order order = order(OrderStatus.DELIVERED);
        order.addItem(new OrderItem(UUID.randomUUID(), order, tenantId, UUID.randomUUID(), "Pizza", "Regular", new BigDecimal("50.00"), 2));
        order.applyDiscount(UUID.randomUUID(), new BigDecimal("15.00"));
        ReflectionTestUtils.setField(order, "status", OrderStatus.DELIVERED);
        when(searchRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Order>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        stubLookupsFor(order);

        SalesPageResponse pageResponse = service.search(new SalesSearchFilter(null, null, null, null, null, null, null, null, 0, 20));

        assertThat(pageResponse.totalElements()).isEqualTo(1);
        var row = pageResponse.content().get(0);
        assertThat(row.customerName()).isEqualTo("A*** L***");
        assertThat(row.vendorName()).isEqualTo("Loja");
        assertThat(row.discountTotal()).isEqualByComparingTo("15.00");
        assertThat(row.total()).isEqualByComparingTo(
                row.subtotal().add(row.fees()).add(row.taxes()).subtract(row.discountTotal()));
        assertThat(row.itemCount()).isEqualTo(2);
        assertThat(row.paymentProvider()).isEqualTo("LOCAL_MOCK");
    }

    @Test
    void searchKeepsCustomerNameForFinance() {
        authenticateWithRoles("ROLE_FINANCE");
        Order order = order(OrderStatus.DELIVERED);
        when(searchRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Order>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        stubLookupsFor(order);

        SalesPageResponse pageResponse = service.search(new SalesSearchFilter(null, null, null, null, null, null, null, null, 0, 20));

        assertThat(pageResponse.content().get(0).customerName()).isEqualTo("Ana Lima");
    }

    @Test
    void detailExposesSnapshotsPromotionRefundsAndCommission() {
        Order order = order(OrderStatus.DELIVERED);
        UUID promotionId = UUID.randomUUID();
        order.addItem(new OrderItem(UUID.randomUUID(), order, tenantId, UUID.randomUUID(), "Pizza", "Regular", new BigDecimal("50.00"), 2));
        order.applyDiscount(promotionId, new BigDecimal("10.00"));
        ReflectionTestUtils.setField(order, "status", OrderStatus.DELIVERED);
        stubLookupsFor(order);
        when(refundReadRepository.findByTenantIdAndOrderId(tenantId, order.getId())).thenReturn(List.of(
                new Refund(UUID.randomUUID(), tenantId, UUID.randomUUID(), order.getId(), new BigDecimal("20.00"), "partial", RefundStatus.REFUNDED, "k")));
        when(commissionReadRepository.findByTenantIdAndOrderId(tenantId, order.getId())).thenReturn(List.of(
                new com.delivery.finance.entity.Commission(UUID.randomUUID(), tenantId, order.getId(), vendorId,
                        new BigDecimal("100.00"), new BigDecimal("0.10"), new BigDecimal("10.00"), "PENDING")));

        var detail = service.detail(order.getId());

        assertThat(detail.items()).hasSize(1);
        assertThat(detail.items().get(0).productNameSnapshot()).isEqualTo("Pizza");
        assertThat(detail.items().get(0).lineTotal()).isEqualByComparingTo("100.00");
        assertThat(detail.appliedPromotionId()).isEqualTo(promotionId);
        assertThat(detail.refunds()).hasSize(1);
        assertThat(detail.commission()).isEqualByComparingTo("10.00");
        assertThat(detail.total()).isEqualByComparingTo(
                detail.subtotal().add(detail.fees()).add(detail.taxes()).subtract(detail.discountTotal()));
    }

    private void stubLookupsFor(Order order) {
        when(vendorRepository.findAllById(any())).thenReturn(List.of(
                new Vendor(vendorId, tenantId, "Loja", null, null, null, "Owner", null, null, null, null, null)));
        when(userProfileRepository.findAllById(any())).thenReturn(List.of(
                new AppUserProfile(customerId, tenantId, "kc-1", "ana@example.com", "Ana Lima", Set.of(MarketplaceRole.CUSTOMER))));
        PaymentSummary payment = payment(order, order.getTotal().toPlainString(), PaymentStatus.CONFIRMED);
        when(paymentReadRepository.findByTenantIdAndOrderIdIn(eq(tenantId), any())).thenReturn(List.of(payment));
        when(paymentReadRepository.findByTenantIdAndOrderId(tenantId, order.getId())).thenReturn(List.of(payment));
    }

    private PaymentSummary payment(Order order, String amount, PaymentStatus status) {
        UUID paymentId = UUID.randomUUID();
        BigDecimal value = new BigDecimal(amount);
        return new PaymentSummary() {
            @Override public UUID getId() { return paymentId; }
            @Override public UUID getOrderId() { return order.getId(); }
            @Override public BigDecimal getAmount() { return value; }
            @Override public String getProvider() { return "LOCAL_MOCK"; }
            @Override public PaymentStatus getStatus() { return status; }
        };
    }
}
