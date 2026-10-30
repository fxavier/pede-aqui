package com.delivery.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.auth.api.UserAccounts;
import com.delivery.notification.api.NotificationChannel;
import com.delivery.notification.config.NotificationProperties;
import com.delivery.notification.entity.DeviceToken;
import com.delivery.notification.entity.NotificationDelivery.Status;
import com.delivery.notification.entity.UserNotificationPreference;
import com.delivery.notification.repository.DeviceTokenRepository;
import com.delivery.notification.repository.NotificationDeliveryStore;
import com.delivery.notification.repository.NotificationDeliveryStore.ClaimedDelivery;
import com.delivery.notification.repository.UserNotificationPreferenceRepository;
import com.delivery.notification.sender.EmailSender;
import com.delivery.notification.sender.PushSender;
import com.delivery.notification.sender.SendResult;
import com.delivery.notification.sender.SmsSender;
import com.delivery.notification.service.NotificationDeliveryWorker;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** LAC-48 delivery job rules with mocked providers: send, retry with backoff, give up, skip, invalid token cleanup. */
class NotificationDeliveryWorkerTest {
    private final UUID tenant = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();
    private final NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
    private final PushSender push = mock(PushSender.class);
    private final SmsSender sms = mock(SmsSender.class);
    private final EmailSender email = mock(EmailSender.class);
    private final DeviceTokenRepository devices = mock(DeviceTokenRepository.class);
    private final UserNotificationPreferenceRepository preferences = mock(UserNotificationPreferenceRepository.class);
    private final UserAccounts userAccounts = mock(UserAccounts.class);
    private final NotificationProperties properties = new NotificationProperties();
    private NotificationDeliveryWorker worker;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        properties.setMaxAttempts(3);
        properties.setBaseBackoff(Duration.ofSeconds(10));
        worker = new NotificationDeliveryWorker(store, tm, properties, push, sms, email, devices, preferences, userAccounts);
        when(preferences.findByTenantIdAndUserProfileId(tenant, profile)).thenReturn(Optional.empty());
        when(store.recordResult(any(), anyInt(), any(), any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void pushIsSentToTheDevicesAndRecordedMasked() {
        UUID id = claim(NotificationChannel.PUSH, 1);
        when(devices.findForRecipient(eq(tenant), eq(profile), any())).thenReturn(List.of(device("token-aaaa1111")));
        when(push.send(eq("ANDROID"), eq("token-aaaa1111"), any(), any(), any())).thenReturn(SendResult.sent("msg-1"));

        var summary = worker.deliverDue();

        assertThat(summary.results()).containsEntry(Status.SENT, 1);
        verify(store).recordResult(id, 1, Status.SENT, null, null, "msg-1", "****1111");
    }

    @Test
    void transientFailureIsRetriedWithExponentialBackoffThenFails() {
        UUID id = claim(NotificationChannel.PUSH, 2);
        when(devices.findForRecipient(eq(tenant), eq(profile), any())).thenReturn(List.of(device("token-x")));
        when(push.send(any(), any(), any(), any(), any())).thenReturn(SendResult.retryable("provider_timeout"));

        worker.deliverDue();
        verify(store).recordResult(id, 2, Status.QUEUED, Duration.ofSeconds(20), "provider_timeout", null, null);

        UUID last = claim(NotificationChannel.PUSH, 3); // attempt 3 of max 3
        worker.deliverDue();
        verify(store).recordResult(last, 3, Status.FAILED, null, "provider_timeout", null, null);
    }

    @Test
    void providerExceptionsAreRetriedNotPropagated() {
        UUID id = claim(NotificationChannel.SMS, 1);
        when(userAccounts.contactOf(tenant, profile)).thenReturn(Optional.of(new UserAccounts.ContactPoints("a@b.mz", "+258841234567")));
        smsOptIn();
        when(sms.send(anyString(), any(), any())).thenThrow(new IllegalStateException("boom"));

        worker.deliverDue();

        verify(store).recordResult(id, 1, Status.QUEUED, Duration.ofSeconds(10), "provider_exception", null, "+25884*****67");
    }

    @Test
    void invalidTokensAreDeletedAndTheDeliveryFails() {
        UUID id = claim(NotificationChannel.PUSH, 1);
        DeviceToken stale = device("stale-token-9999");
        when(devices.findForRecipient(eq(tenant), eq(profile), any())).thenReturn(List.of(stale));
        when(push.send(any(), any(), any(), any(), any())).thenReturn(SendResult.invalidRecipient("unregistered"));

        worker.deliverDue();

        verify(devices).deleteInvalid(stale.getId());
        verify(store).recordResult(id, 1, Status.FAILED, null, "invalid_recipient", null, null);
    }

    @Test
    void missingRecipientOrOptOutIsSkippedWithoutCallingTheProvider() {
        UUID noDevice = claim(NotificationChannel.PUSH, 1);
        when(devices.findForRecipient(eq(tenant), eq(profile), any())).thenReturn(List.of());
        worker.deliverDue();
        verify(store).recordResult(noDevice, 1, Status.SKIPPED, null, "no_device", null, null);

        UUID noPhone = claim(NotificationChannel.SMS, 1);
        smsOptIn();
        when(userAccounts.contactOf(tenant, profile)).thenReturn(Optional.of(new UserAccounts.ContactPoints("a@b.mz", null)));
        worker.deliverDue();
        verify(store).recordResult(noPhone, 1, Status.SKIPPED, null, "no_verified_phone", null, null);

        UUID optedOut = claim(NotificationChannel.EMAIL, 1); // e-mail is off by default
        worker.deliverDue();
        verify(store).recordResult(optedOut, 1, Status.SKIPPED, null, "preference_disabled", null, null);
        verify(push, never()).send(any(), any(), any(), any(), any());
        verify(sms, never()).send(any(), any(), any());
        verify(email, never()).send(any(), any(), any(), any());
    }

    @Test
    void aLostClaimIsCountedAndNotOverwritten() {
        claim(NotificationChannel.PUSH, 1);
        when(devices.findForRecipient(eq(tenant), eq(profile), any())).thenReturn(List.of());
        when(store.recordResult(any(), anyInt(), any(), isNull(), any(), any(), any())).thenReturn(false);

        assertThat(worker.deliverDue().lostClaims()).isEqualTo(1);
    }

    private void smsOptIn() {
        UserNotificationPreference row = new UserNotificationPreference(UUID.randomUUID(), tenant, profile);
        row.updatePreferences(true, true, false);
        when(preferences.findByTenantIdAndUserProfileId(tenant, profile)).thenReturn(Optional.of(row));
    }

    private UUID claim(NotificationChannel channel, int attempts) {
        UUID id = UUID.randomUUID();
        when(store.claimDue(anyInt(), any())).thenReturn(List.of(
                new ClaimedDelivery(id, tenant, profile, channel, "order_rejected", "Título", "Corpo", attempts)));
        return id;
    }

    private DeviceToken device(String token) {
        return new DeviceToken(UUID.randomUUID(), tenant, profile, "ANDROID", token, "hash-" + token);
    }
}
