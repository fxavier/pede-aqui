package com.delivery.delivery;

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
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.config.DeliveryCodeProperties;
import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.mapper.DeliveryMapper;
import com.delivery.delivery.repository.DeliveryEventRepository;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.delivery.service.DeliveryAccessGuard;
import com.delivery.delivery.service.DeliveryCodeHasher;
import com.delivery.delivery.service.DeliveryService;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
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

/** Unit-level checks of DeliveryService completion, ownership, lockout and PIN hashing (INC-02). */
class DeliveryConfirmationTest {
    private static final String CODE = "482913";
    private static final String WRONG = "000001";
    private static final String COURIER_USER = "kc-courier";
    private final com.delivery.order.service.OrderStockService orderStockService = mock(com.delivery.order.service.OrderStockService.class);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final DeliveryEventRepository eventRepository = mock(DeliveryEventRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final AppUserProfileRepository profileRepository = mock(AppUserProfileRepository.class);
    private final CourierRepository courierRepository = mock(CourierRepository.class);
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final DeliveryCodeHasher hasher = hasher("unit-test-secret");
    private DeliveryService service;
    private Order order;

    @BeforeEach
    void setUp() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DeliveryCodeProperties properties = new DeliveryCodeProperties();
        properties.setMaxCodeAttempts(5);
        service = new DeliveryService(deliveryRepository, eventRepository, orderRepository, new DeliveryMapper(), tenantContext,
                new DeliveryAccessGuard(profileRepository, courierRepository, tenantContext), hasher, properties, auditLogService,
                orderStockService);
        order = new Order(orderId, tenantId, "PA-2", UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, new BigDecimal("12.00"), "checkout-key", hasher.hash(CODE), CODE);
        when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
        Courier courier = mock(Courier.class);
        when(courier.getId()).thenReturn(courierId);
        when(profileRepository.findByTenantIdAndKeycloakUserId(tenantId, COURIER_USER))
                .thenReturn(Optional.of(new AppUserProfile(profileId, tenantId, COURIER_USER, "c@x.mz", "Courier", Set.of())));
        when(courierRepository.findByTenantIdAndUserProfileId(tenantId, profileId)).thenReturn(Optional.of(courier));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void correctCodeFromAssignedCourierDeliversAndClearsPlainPin() {
        Delivery delivery = delivery(DeliveryStatus.ARRIVED_AT_CUSTOMER, courierId);
        actAs(COURIER_USER, "ROLE_COURIER");

        assertThat(service.complete(delivery.getId(), CODE).status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveryConfirmationCodeDisplay()).isNull();
    }

    @Test
    void completingStraightFromAcceptedCommitsTheReservedStock() {
        // INC-02 allows /complete from ACCEPTED (no PICKED_UP PATCH), so INC-07's stock commit must also run here.
        Delivery delivery = delivery(DeliveryStatus.ACCEPTED, courierId);
        actAs(COURIER_USER, "ROLE_COURIER");

        service.complete(delivery.getId(), CODE);

        verify(orderStockService).commit(order);
    }

    @Test
    void incorrectCodeIsRejectedAndAttemptIsRecorded() {
        Delivery delivery = delivery(DeliveryStatus.ARRIVED_AT_CUSTOMER, courierId);
        actAs(COURIER_USER, "ROLE_COURIER");

        assertThatThrownBy(() -> service.complete(delivery.getId(), WRONG))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("invalid_delivery_code");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        assertThat(delivery.getConfirmationAttempts()).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ARRIVED_AT_CUSTOMER);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    void lockedCodeRejectsEvenTheCorrectPin() {
        Delivery delivery = delivery(DeliveryStatus.ARRIVED_AT_CUSTOMER, courierId);
        ReflectionTestUtils.setField(delivery, "confirmationAttempts", 5);
        actAs(COURIER_USER, "ROLE_COURIER");

        assertThatThrownBy(() -> service.complete(delivery.getId(), CODE))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("delivery_code_locked");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.LOCKED);
                });
        assertThat(delivery.getConfirmationAttempts()).isEqualTo(5);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void unassignedCourierIsForbidden() {
        Delivery delivery = delivery(DeliveryStatus.ARRIVED_AT_CUSTOMER, UUID.randomUUID());
        actAs(COURIER_USER, "ROLE_COURIER");

        assertThatThrownBy(() -> service.complete(delivery.getId(), CODE))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.updateStatus(delivery.getId(), DeliveryStatus.FAILED_DELIVERY, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ARRIVED_AT_CUSTOMER);
    }

    @Test
    void invalidLifecycleTransitionIsConflict() {
        Delivery delivery = delivery(DeliveryStatus.ACCEPTED, courierId);
        actAs(COURIER_USER, "ROLE_COURIER");

        assertThatThrownBy(() -> service.updateStatus(delivery.getId(), DeliveryStatus.PICKED_UP, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void assignedCourierAdvancesLifecycleWithoutAudit() {
        Delivery delivery = delivery(DeliveryStatus.ACCEPTED, courierId);
        actAs(COURIER_USER, "ROLE_COURIER");

        assertThat(service.updateStatus(delivery.getId(), DeliveryStatus.ARRIVED_AT_VENDOR, null, null).status())
                .isEqualTo(DeliveryStatus.ARRIVED_AT_VENDOR);
        verify(auditLogService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void opsStatusUpdateIsAudited() {
        Delivery delivery = delivery(DeliveryStatus.ACCEPTED, courierId);
        actAs("kc-ops", "ROLE_OPS");

        service.updateStatus(delivery.getId(), DeliveryStatus.ARRIVED_AT_VENDOR, null, null);

        verify(auditLogService).log(eq("DELIVERY_STATUS_UPDATED"), eq("delivery"), eq(delivery.getId().toString()), eq("ACCEPTED->ARRIVED_AT_VENDOR"), eq("SUCCESS"));
    }

    @Test
    void unlockResetsCounterAndIsAudited() {
        Delivery delivery = delivery(DeliveryStatus.ARRIVED_AT_CUSTOMER, courierId);
        ReflectionTestUtils.setField(delivery, "confirmationAttempts", 5);
        actAs("kc-ops", "ROLE_OPS");

        assertThat(service.unlockCode(delivery.getId()).confirmationAttempts()).isZero();
        verify(auditLogService).log(eq("DELIVERY_CODE_UNLOCKED"), eq("delivery"), eq(delivery.getId().toString()), eq("attempts=5"), eq("SUCCESS"));
    }

    // --- Order no longer deliverable (refunded / cancelled / legacy rejected): 409 order_not_deliverable, no effects ---

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = OrderStatus.class, names = {"REFUNDED", "CANCELLED", "REJECTED_BY_VENDOR"})
    void completionOnANonDeliverableOrderIsConflictWithoutEffects(OrderStatus terminal) {
        ReflectionTestUtils.setField(order, "status", terminal);
        Delivery delivery = delivery(DeliveryStatus.ARRIVED_AT_CUSTOMER, courierId);
        actAs(COURIER_USER, "ROLE_COURIER");

        // Correct PIN and wrong PIN alike: refused before the PIN is compared, so the counter never moves.
        for (String pin : List.of(CODE, WRONG)) {
            assertThatThrownBy(() -> service.complete(delivery.getId(), pin))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getCode()).isEqualTo("order_not_deliverable");
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    });
        }
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ARRIVED_AT_CUSTOMER);
        assertThat(delivery.getConfirmationAttempts()).isZero();
        assertThat(order.getStatus()).isEqualTo(terminal);
        verify(eventRepository, never()).save(any());
        verify(orderStockService, never()).commit(any());
        verify(auditLogService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = OrderStatus.class, names = {"REFUNDED", "CANCELLED", "REJECTED_BY_VENDOR"})
    void statusUpdateOnANonDeliverableOrderIsConflictWithoutEffectsForCourierAndOps(OrderStatus terminal) {
        ReflectionTestUtils.setField(order, "status", terminal);
        Delivery delivery = delivery(DeliveryStatus.ARRIVED_AT_VENDOR, courierId);

        actAs(COURIER_USER, "ROLE_COURIER");
        assertThatThrownBy(() -> service.updateStatus(delivery.getId(), DeliveryStatus.PICKED_UP, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("order_not_deliverable");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        actAs("kc-ops", "ROLE_OPS");
        assertThatThrownBy(() -> service.updateStatus(delivery.getId(), DeliveryStatus.PICKED_UP, "proof-key", BigDecimal.ONE))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("order_not_deliverable"));

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ARRIVED_AT_VENDOR);
        assertThat(order.getStatus()).isEqualTo(terminal);
        verify(eventRepository, never()).save(any());
        verify(orderStockService, never()).commit(any());
        verify(orderStockService, never()).release(any());
        verify(auditLogService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void ownershipIsCheckedBeforeTheOrderDeliverabilityConflict() {
        ReflectionTestUtils.setField(order, "status", OrderStatus.REFUNDED);
        Delivery delivery = delivery(DeliveryStatus.ARRIVED_AT_CUSTOMER, UUID.randomUUID());
        actAs(COURIER_USER, "ROLE_COURIER");

        assertThatThrownBy(() -> service.complete(delivery.getId(), CODE))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.updateStatus(delivery.getId(), DeliveryStatus.FAILED_DELIVERY, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void hasherUsesHmacAndAcceptsSha256OnlyForLegacyRows() throws Exception {
        String sha256 = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(CODE.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(hasher.hash(CODE)).isNotEqualTo(sha256).hasSize(64);
        assertThat(hasher.hash(CODE)).isNotEqualTo(hasher("another-secret").hash(CODE));
        assertThat(hasher.matches(CODE, hasher.hash(CODE), false)).isTrue();
        assertThat(hasher.matches(WRONG, hasher.hash(CODE), false)).isFalse();
        assertThat(hasher.matches(CODE, sha256, true)).isTrue();
        assertThat(hasher.matches(CODE, sha256, false)).isFalse();
        assertThat(hasher.matches(null, sha256, true)).isFalse();
        assertThat(hasher.generateCode()).matches("[0-9]{6}");
    }

    @Test
    void hasherRefusesToStartWithoutSecret() {
        assertThatThrownBy(() -> hasher(" ")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> hasher(null)).isInstanceOf(IllegalStateException.class);
    }

    private Delivery delivery(DeliveryStatus status, UUID assignedCourierId) {
        Delivery delivery = new Delivery(UUID.randomUUID(), tenantId, orderId, hasher.hash(CODE));
        delivery.assignCourier(assignedCourierId);
        delivery.updateStatus(status);
        when(deliveryRepository.findByTenantIdAndId(tenantId, delivery.getId())).thenReturn(Optional.of(delivery));
        return delivery;
    }

    private void actAs(String keycloakUserId, String... authorities) {
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of(keycloakUserId));
        TestingAuthenticationToken auth = new TestingAuthenticationToken(keycloakUserId, "n/a", List.of(authorities).toArray(String[]::new));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static DeliveryCodeHasher hasher(String secret) {
        DeliveryCodeProperties properties = new DeliveryCodeProperties();
        properties.setCodeHmacSecret(secret);
        return new DeliveryCodeHasher(properties);
    }
}
