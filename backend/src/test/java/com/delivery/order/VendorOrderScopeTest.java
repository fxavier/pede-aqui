package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.repository.CartRepository;
import com.delivery.cart.service.CartService;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.config.DeliveryCodeProperties;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.delivery.service.DeliveryCodeHasher;
import com.delivery.inventory.service.InventoryService;
import com.delivery.notification.service.NotificationService;
import com.delivery.order.dto.AdminOrderPageResponse;
import com.delivery.order.dto.AdminOrderResponse;
import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.repository.OrderRepository;
import com.delivery.marketing.api.CheckoutDiscount;
import com.delivery.order.service.OrderService;
import com.delivery.order.service.OrderStockService;
import com.delivery.payment.config.PaymentMockConfirmationProperties;
import com.delivery.payment.dto.RefundRequest;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.Refund;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.mapper.PaymentMapper;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
import com.delivery.payment.service.PaymentService;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;

/** INC-04 (SEC-03, SEC-04, COR-07): vendor users are confined to their own vendor's orders. */
class VendorOrderScopeTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID vendorA = UUID.randomUUID();
    private final UUID vendorB = UUID.randomUUID();
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final VendorRepository vendorRepository = mock(VendorRepository.class);
    private final AppUserProfileRepository userProfileRepository = mock(AppUserProfileRepository.class);
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final OrderStockService orderStockService = mock(OrderStockService.class);
    private final OrderService service;

    VendorOrderScopeTest() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        service = new OrderService(
                mock(CartRepository.class), orderRepository, paymentRepository, mock(DeliveryRepository.class),
                mock(InventoryService.class), userProfileRepository, vendorRepository, notificationService,
                paymentService, mock(AuditLogService.class), new OrderMapper(userProfileRepository, tenantContext),
                tenantContext, mock(SkuRepository.class), mock(ProductRepository.class),
                (cart, customerId) -> CheckoutDiscount.none(), testCodeHasher(),
                orderStockService, mock(CartService.class), mock(PlatformTransactionManager.class), new com.delivery.order.service.DeliveryAddressResolver(mock(com.delivery.customer.repository.CustomerAddressRepository.class), true), com.delivery.cart.PricingFixtures.platformDefaults(),
                com.delivery.vendor.MerchantScopeFixtures.legacyFallback(tenantContext, vendorRepository), mock(com.delivery.delivery.api.DeliveryRetirementService.class), mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    private static DeliveryCodeHasher testCodeHasher() {
        DeliveryCodeProperties properties = new DeliveryCodeProperties();
        properties.setCodeHmacSecret("test-only-hmac-secret");
        return new DeliveryCodeHasher(properties);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // --- SEC-03: vendor transitions on another vendor's order -> 404, nothing mutated ---

    @Test
    void vendorAdminCannotAcceptAnotherVendorsOrder() {
        authenticate(vendorA, "ROLE_VENDOR_ADMIN");
        Order order = stored(paymentConfirmed(vendorB));

        assertThatThrownBy(() -> service.acceptByVendor(order.getId())).isInstanceOf(NotFoundException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
    }

    @Test
    void vendorAdminCannotRejectAnotherVendorsOrderNorTriggerItsRefund() {
        authenticate(vendorA, "ROLE_VENDOR_ADMIN");
        Order order = stored(paymentConfirmed(vendorB));

        assertThatThrownBy(() -> service.rejectByVendor(order.getId(), "Sem stock")).isInstanceOf(NotFoundException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
        verify(paymentService, never()).requestRefund(any(), any());
        verify(paymentService, never()).requestRemainingRefund(any(), anyString(), anyString());
        verify(notificationService, never()).create(any(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(orderStockService, never()).release(any()); // INC-04 x INC-07: no stock side effect either
    }

    @Test
    void vendorStaffCannotPrepareOrMarkReadyAnotherVendorsOrder() {
        authenticate(vendorA, "ROLE_VENDOR_STAFF");
        Order accepted = stored(paymentConfirmed(vendorB));
        accepted.markAcceptedByVendor();
        Order preparing = stored(paymentConfirmed(vendorB));
        preparing.markAcceptedByVendor();
        preparing.markPreparing();

        assertThatThrownBy(() -> service.markPreparing(accepted.getId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.markReadyForPickup(preparing.getId())).isInstanceOf(NotFoundException.class);
        assertThat(accepted.getStatus()).isEqualTo(OrderStatus.ACCEPTED_BY_VENDOR);
        assertThat(preparing.getStatus()).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    void vendorWithoutClaimInSingleVendorTenantIsScopedToThatVendor() {
        authenticate(null, "ROLE_VENDOR_ADMIN");
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(vendor(vendorA)));
        Order foreign = stored(paymentConfirmed(vendorB));
        Order own = stored(paymentConfirmed(vendorA));

        assertThatThrownBy(() -> service.acceptByVendor(foreign.getId())).isInstanceOf(NotFoundException.class);
        assertThat(service.acceptByVendor(own.getId()).status()).isEqualTo(OrderStatus.ACCEPTED_BY_VENDOR);
    }

    @Test
    void unresolvableVendorScopeFailsClosedWith403() {
        authenticate(null, "ROLE_VENDOR_STAFF");
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(vendor(vendorA), vendor(vendorB)));
        Order order = stored(paymentConfirmed(vendorA));

        assertThatThrownBy(() -> service.acceptByVendor(order.getId()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(service::listForCurrentContext)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
    }

    // --- Characterization: own-vendor fulfilment keeps working ---

    @Test
    void vendorStaffRunsTheFullFulfilmentFlowOnOwnOrder() {
        authenticate(vendorA, "ROLE_VENDOR_STAFF");
        Order order = stored(paymentConfirmed(vendorA));

        assertThat(service.acceptByVendor(order.getId()).status()).isEqualTo(OrderStatus.ACCEPTED_BY_VENDOR);
        assertThat(service.markPreparing(order.getId()).status()).isEqualTo(OrderStatus.PREPARING);
        assertThat(service.markReadyForPickup(order.getId()).status()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
    }

    // --- Unified seller rejection: CANCELLED + SELLER_REJECTED; the refund of the remainder lives on the Refund record ---

    @Test
    void ownVendorRejectionCancelsAsSellerRejectedAndRequestsTheRemainingRefund() {
        authenticate(vendorA, "ROLE_VENDOR_ADMIN");
        Order order = stored(paymentConfirmed(vendorA));
        Payment payment = mock(Payment.class);
        UUID paymentId = UUID.randomUUID();
        when(payment.getId()).thenReturn(paymentId);
        when(payment.getAmount()).thenReturn(new BigDecimal("115.00"));
        when(paymentRepository.findByTenantIdAndOrderIdForUpdate(tenantId, order.getId())).thenReturn(Optional.of(payment));

        var response = service.rejectByVendor(order.getId(), "Sem ingredientes");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo(CancellationReason.SELLER_REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.SELLER_REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo("Sem ingredientes");
        verify(paymentService).requestRemainingRefund(paymentId, "Vendor rejection: Sem ingredientes", "seller-reject-" + order.getId());
        verify(paymentService, never()).requestRefund(any(), any());
        verify(orderStockService).release(order); // INC-07 (COR-01): rejection returns the reservation
    }

    @Test
    void rejectionThroughRealPaymentServicePersistsRequestedFullRefundAndCancelsOrder() {
        authenticate(vendorA, "ROLE_VENDOR_ADMIN");
        Order order = stored(paymentConfirmed(vendorA));
        Payment payment = new Payment(UUID.randomUUID(), tenantId, order.getId(), new BigDecimal("115.00"), "pay-idem");
        when(paymentRepository.findByTenantIdAndOrderIdForUpdate(tenantId, order.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.findByTenantIdAndId(tenantId, payment.getId())).thenReturn(Optional.of(payment));
        RefundRepository refundRepository = mock(RefundRepository.class);
        when(refundRepository.findByTenantIdAndIdempotencyKey(any(), anyString())).thenReturn(Optional.empty());
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByTenantIdAndIdForUpdate(tenantId, payment.getId())).thenReturn(Optional.of(payment));
        AuditLogService paymentAudit = mock(AuditLogService.class);
        PaymentService realPayments = new PaymentService(paymentRepository, new com.delivery.order.service.OrderPaymentHooks(orderRepository, org.mockito.Mockito.mock(com.delivery.delivery.api.DeliveryRetirementService.class), org.mockito.Mockito.mock(com.delivery.order.service.OrderStockService.class), paymentAudit), refundRepository,
                paymentAudit, new PaymentMapper(), tenantContext, userProfileRepository,
                new PaymentMockConfirmationProperties());
        OrderService withRealPayments = new OrderService(
                mock(CartRepository.class), orderRepository, paymentRepository, mock(DeliveryRepository.class),
                mock(InventoryService.class), userProfileRepository, vendorRepository, notificationService,
                realPayments, mock(AuditLogService.class), new OrderMapper(userProfileRepository, tenantContext),
                tenantContext, mock(SkuRepository.class), mock(ProductRepository.class),
                (cart, customerId) -> CheckoutDiscount.none(), testCodeHasher(),
                orderStockService, mock(CartService.class), mock(PlatformTransactionManager.class), new com.delivery.order.service.DeliveryAddressResolver(mock(com.delivery.customer.repository.CustomerAddressRepository.class), true), com.delivery.cart.PricingFixtures.platformDefaults(),
                com.delivery.vendor.MerchantScopeFixtures.legacyFallback(tenantContext, vendorRepository), mock(com.delivery.delivery.api.DeliveryRetirementService.class), mock(org.springframework.context.ApplicationEventPublisher.class));

        withRealPayments.rejectByVendor(order.getId(), "Fechado");

        ArgumentCaptor<Refund> saved = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("115.00");
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo("seller-reject-" + order.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.SELLER_REJECTED);
    }

    // --- SEC-04: listing is confined to the vendor; OPS/ADMIN keep the tenant-wide view ---

    @Test
    void vendorListContainsOnlyOwnVendorOrders() {
        authenticate(vendorA, "ROLE_VENDOR_ADMIN");
        Order own = paymentConfirmed(vendorA);
        Order foreign = paymentConfirmed(vendorB);
        when(orderRepository.findByTenantId(org.mockito.ArgumentMatchers.eq(tenantId), any(Pageable.class))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(own, foreign)));
        when(orderRepository.findByTenantIdAndVendorId(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(vendorA), any(Pageable.class))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(own)));

        List<AdminOrderResponse> rows = service.listForCurrentContext().items();

        assertThat(rows).extracting(AdminOrderResponse::id).containsExactly(own.getId());
    }

    @Test
    void opsAndAdminStillSeeAllTenantOrders() {
        Order a = paymentConfirmed(vendorA);
        Order b = paymentConfirmed(vendorB);
        when(orderRepository.findByTenantId(org.mockito.ArgumentMatchers.eq(tenantId), any(Pageable.class))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(a, b)));

        for (String role : List.of("ROLE_OPS", "ROLE_ADMIN")) {
            authenticate(null, role);
            assertThat(service.listForCurrentContext().items()).extracting(AdminOrderResponse::id).containsExactly(a.getId(), b.getId());
        }
        verify(orderRepository, never()).findByTenantIdAndVendorId(any(), any(), any(Pageable.class));
    }

    @Test
    void vendorListShowsOwnCustomersNamesUnmaskedLikeSales() {
        authenticate(vendorA, "ROLE_VENDOR_ADMIN");
        Order own = paymentConfirmed(vendorA);
        when(orderRepository.findByTenantIdAndVendorId(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(vendorA), any(Pageable.class))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(own)));
        when(userProfileRepository.findAllById(Set.of(own.getCustomerId()))).thenReturn(List.of(
                new AppUserProfile(own.getCustomerId(), tenantId, "kc", "ana@example.test", "Ana Lima", Set.of())));

        assertThat(service.listForCurrentContext().items().get(0).customerName()).isEqualTo("Ana Lima");
    }

    @Test
    void pagedListIsVendorScopedAndCapsPageSize() {
        authenticate(vendorA, "ROLE_VENDOR_STAFF");
        Order own = paymentConfirmed(vendorA);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(orderRepository.findByTenantIdAndVendorId(any(UUID.class), any(UUID.class), pageable.capture()))
                .thenAnswer(inv -> new PageImpl<>(List.of(own), inv.getArgument(2), 1));

        AdminOrderPageResponse page = service.listPageForCurrentContext(0, 1000);

        assertThat(page.content()).extracting(AdminOrderResponse::id).containsExactly(own.getId());
        assertThat(page.size()).isEqualTo(100);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        verify(orderRepository).findByTenantIdAndVendorId(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(vendorA), any(Pageable.class));
    }

    @Test
    void pagedListForOpsIsTenantWide() {
        authenticate(null, "ROLE_OPS");
        Order a = paymentConfirmed(vendorA);
        when(orderRepository.findByTenantId(any(UUID.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(a), inv.getArgument(1), 1));

        AdminOrderPageResponse page = service.listPageForCurrentContext(-3, 0);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.content()).hasSize(1);
        verify(orderRepository).findByTenantId(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 1, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))));
    }

    private void authenticate(UUID vendorClaim, String... roles) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject("user")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (vendorClaim != null) {
            builder.claim("vendor_id", vendorClaim.toString());
        }
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(builder.build(), AuthorityUtils.createAuthorityList(roles)));
    }

    private Order stored(Order order) {
        when(orderRepository.findByTenantIdAndId(tenantId, order.getId())).thenReturn(Optional.of(order));
        return order;
    }

    private Vendor vendor(UUID id) {
        return new Vendor(id, tenantId, "Loja", null, null, null, "Owner", null, null, null, null, null);
    }

    private Order paymentConfirmed(UUID vendorId) {
        Order order = new Order(UUID.randomUUID(), tenantId, "PA-TEST-" + UUID.randomUUID(), UUID.randomUUID(), vendorId,
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("5.00"), BigDecimal.ZERO,
                new BigDecimal("115.00"), "idem-" + UUID.randomUUID(), "hash", "000000");
        order.markPaymentConfirmed();
        return order;
    }
}
