package com.delivery.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.auth.api.UserAccounts;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.customer.api.CustomerPersonalData;
import com.delivery.notification.api.NotificationPreferences;
import com.delivery.order.api.CustomerOrderPrivacy;
import com.delivery.rating.api.CustomerRatingPrivacy;
import com.delivery.privacy.config.PrivacyProperties;
import com.delivery.privacy.entity.AccountDeletionRequest;
import com.delivery.privacy.entity.AccountDeletionStatus;
import com.delivery.privacy.repository.AccountDeletionRequestRepository;
import com.delivery.privacy.repository.RetentionRunLog;
import com.delivery.privacy.service.AccountErasureService;
import com.delivery.privacy.service.AccountErasureService.Outcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pseudonymisation orchestration of one deletion request (LAC-19): the Keycloak user is disabled before any data is
 * touched, then every module erases its part, the audit row is written and only then is the Keycloak user deleted;
 * an active order postpones without side effects; a Keycloak failure stops before the erasure.
 */
class AccountErasureServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    private final UUID tenant = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();
    private final String sub = "kc-" + UUID.randomUUID();

    private AccountDeletionRequestRepository repository;
    private UserAccounts userAccounts;
    private CustomerPersonalData customerData;
    private NotificationPreferences notifications;
    private CustomerOrderPrivacy orders;
    private CustomerRatingPrivacy ratings;
    private AuditLogService audit;
    private AccountErasureService service;
    private AccountDeletionRequest request;

    @BeforeEach
    void setUp() {
        repository = mock(AccountDeletionRequestRepository.class);
        userAccounts = mock(UserAccounts.class);
        customerData = mock(CustomerPersonalData.class);
        notifications = mock(NotificationPreferences.class);
        orders = mock(CustomerOrderPrivacy.class);
        ratings = mock(CustomerRatingPrivacy.class);
        audit = mock(AuditLogService.class);
        service = new AccountErasureService(repository, userAccounts, customerData, notifications, orders, ratings, audit,
                mock(RetentionRunLog.class), new PrivacyProperties(), new TransactionTemplate(mock(PlatformTransactionManager.class)),
                new SimpleMeterRegistry());
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        request = new AccountDeletionRequest(UUID.randomUUID(), tenant, profile, sub, NOW.minus(Duration.ofDays(31)), NOW.minus(Duration.ofDays(1)));
        when(repository.claimSkipLocked(request.getId())).thenReturn(List.of(request.getId()));
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));
        when(customerData.erase(tenant, profile, sub)).thenReturn(new CustomerPersonalData.Erasure(2, 1));
        when(notifications.erase(tenant, profile)).thenReturn(new NotificationPreferences.Erasure(1, 3, 1));
        when(orders.pseudonymiseCustomerOrders(tenant, profile)).thenReturn(4);
        when(ratings.pseudonymiseCustomerComments(tenant, profile)).thenReturn(2);
        when(userAccounts.pseudonymise(tenant, profile)).thenReturn(true);
    }

    @Test
    void disablesThenErasesEveryModuleThenAuditsThenDeletesTheKeycloakUser() {
        assertThat(service.process(request.getId())).isEqualTo(Outcome.COMPLETED);

        InOrder order = inOrder(userAccounts, customerData, notifications, orders, ratings, audit);
        order.verify(userAccounts).disableKeycloakUser(sub);
        order.verify(customerData).erase(tenant, profile, sub);
        order.verify(notifications).erase(tenant, profile);
        order.verify(orders).pseudonymiseCustomerOrders(tenant, profile);
        order.verify(ratings).pseudonymiseCustomerComments(tenant, profile);
        order.verify(userAccounts).pseudonymise(tenant, profile);
        order.verify(audit).logForTenant(eq(tenant), eq(AuditActions.ACCOUNT_PSEUDONYMISED), eq("user_profile"), eq(profile.toString()),
                eq("deletion_request=" + request.getId()), eq("SUCCESS"), any());
        order.verify(userAccounts).deleteKeycloakUserStrict(sub);
        order.verify(audit).logForTenant(eq(tenant), eq(AuditActions.ACCOUNT_DELETION_COMPLETED), anyString(), anyString(), any(), eq("SUCCESS"), any());
        assertThat(request.getStatus()).isEqualTo(AccountDeletionStatus.COMPLETED);
        assertThat(request.getErasedAt()).isEqualTo(NOW);
        assertThat(request.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void anActiveOrderPostponesWithoutTouchingKeycloakOrData() {
        when(orders.hasActiveOrders(tenant, profile)).thenReturn(true);

        assertThat(service.process(request.getId())).isEqualTo(Outcome.POSTPONED);

        verify(userAccounts, never()).disableKeycloakUser(anyString());
        verify(userAccounts, never()).pseudonymise(any(), any());
        verify(customerData, never()).erase(any(), any(), any());
        assertThat(request.getStatus()).isEqualTo(AccountDeletionStatus.PENDING);
        assertThat(request.getAttempts()).isEqualTo(1);
        assertThat(request.getLastError()).isEqualTo("active_orders_present");
    }

    @Test
    void aKeycloakDisableFailureStopsBeforeTheErasureAndIsRecorded() {
        when(userAccounts.disableKeycloakUser(sub))
                .thenThrow(new BusinessException("keycloak_error", "down", HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(service.process(request.getId())).isEqualTo(Outcome.FAILED);

        verify(customerData, never()).erase(any(), any(), any());
        verify(userAccounts, never()).pseudonymise(any(), any());
        assertThat(request.getStatus()).isEqualTo(AccountDeletionStatus.PENDING);
        assertThat(request.getLastError()).isEqualTo("keycloak_error");
    }

    @Test
    void aRequestStillInItsReflectionPeriodIsSkipped() {
        AccountDeletionRequest future = new AccountDeletionRequest(UUID.randomUUID(), tenant, profile, sub, NOW, NOW.plus(Duration.ofDays(30)));
        when(repository.claimSkipLocked(future.getId())).thenReturn(List.of(future.getId()));
        when(repository.findById(future.getId())).thenReturn(Optional.of(future));

        assertThat(service.process(future.getId())).isEqualTo(Outcome.SKIPPED);
        verify(userAccounts, never()).disableKeycloakUser(anyString());
    }

    @Test
    void aRowHeldByAConcurrentCancelIsSkipped() {
        when(repository.claimSkipLocked(request.getId())).thenReturn(List.of());
        assertThat(service.process(request.getId())).isEqualTo(Outcome.SKIPPED);
        verify(userAccounts, never()).disableKeycloakUser(anyString());
    }

    @Test
    void pseudonymisedValuesAreFixedPlaceholders() {
        assertThat(UserAccounts.pseudonymisedEmail(profile)).isEqualTo("deleted-" + profile + "@deleted.invalid");
        assertThat(UserAccounts.PSEUDONYMISED_DISPLAY_NAME).isEqualTo("Cliente removido");
        assertThat(UserAccounts.splitName("  Ana  ")).containsExactly("Ana", "");
        assertThat(UserAccounts.splitName("Ana Sousa Machava")).containsExactly("Ana", "Sousa Machava");
    }
}
