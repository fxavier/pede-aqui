package com.delivery.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.security.TenantContext;
import com.delivery.notification.api.NotificationChannel;
import com.delivery.notification.api.NotificationDispatcher;
import com.delivery.notification.api.NotificationRequest;
import com.delivery.notification.entity.Notification;
import com.delivery.notification.entity.NotificationDelivery.Status;
import com.delivery.notification.entity.NotificationTemplate;
import com.delivery.notification.entity.UserNotificationPreference;
import com.delivery.notification.repository.NotificationDeliveryStore;
import com.delivery.notification.repository.NotificationDeliveryStore.NewDelivery;
import com.delivery.notification.repository.NotificationRepository;
import com.delivery.notification.repository.UserNotificationPreferenceRepository;
import com.delivery.notification.service.NotificationService;
import com.delivery.notification.service.NotificationTemplateService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** LAC-48 dispatcher rules: active templates pick channels, preferences filter, idempotency, in-app fan-out never leaks the message. */
class NotificationDispatcherTest {
    private final UUID tenant = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();
    private final NotificationTemplateService templates = mock(NotificationTemplateService.class);
    private final NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserNotificationPreferenceRepository preferences = mock(UserNotificationPreferenceRepository.class);
    private final List<NewDelivery> inserted = new ArrayList<>();
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(templates, store, notifications, preferences);
        when(templates.effective(any(), any(), any())).thenReturn(Optional.empty());
        when(store.insertIfAbsent(any())).thenAnswer(invocation -> {
            NewDelivery delivery = invocation.getArgument(0);
            if (inserted.stream().anyMatch(row -> row.idempotencyKey().equals(delivery.idempotencyKey()))) {
                return Optional.empty();
            }
            inserted.add(delivery);
            return Optional.of(UUID.randomUUID());
        });
        when(preferences.findByTenantIdAndUserProfileId(tenant, profile)).thenReturn(Optional.empty());
    }

    @Test
    void onlyChannelsWithAnActiveTemplateAreWrittenAndPreferencesSkipOptedOutChannels() {
        template("order_rejected", NotificationChannel.PUSH, true, "Pedido {orderReference} recusado");
        template("order_rejected", NotificationChannel.SMS, true, "SMS {orderReference}");
        template("order_rejected", NotificationChannel.EMAIL, false, "inactive");

        int written = dispatcher.dispatch(request("order_rejected", NotificationDispatcher.ALL_CHANNELS, "fact-1"));

        assertThat(written).isEqualTo(2);
        assertThat(inserted).extracting(NewDelivery::channel).containsExactlyInAnyOrder(NotificationChannel.PUSH, NotificationChannel.SMS);
        NewDelivery push = row(NotificationChannel.PUSH);
        assertThat(push.status()).isEqualTo(Status.QUEUED);
        assertThat(push.body()).isEqualTo("Pedido PA-7 recusado");
        assertThat(push.idempotencyKey()).isEqualTo("fact-1:PUSH");
        NewDelivery sms = row(NotificationChannel.SMS); // V018 default: SMS off
        assertThat(sms.status()).isEqualTo(Status.SKIPPED);
        assertThat(sms.lastError()).isEqualTo("preference_disabled");
        verify(notifications, never()).save(any());
    }

    @Test
    void storedPreferencesAreHonoured() {
        UserNotificationPreference row = new UserNotificationPreference(UUID.randomUUID(), tenant, profile);
        row.updatePreferences(false, true, false);
        when(preferences.findByTenantIdAndUserProfileId(tenant, profile)).thenReturn(Optional.of(row));
        template("order_rejected", NotificationChannel.PUSH, true, "p");
        template("order_rejected", NotificationChannel.SMS, true, "s");

        dispatcher.dispatch(request("order_rejected", NotificationDispatcher.ALL_CHANNELS, "fact-2"));

        assertThat(row(NotificationChannel.PUSH).status()).isEqualTo(Status.SKIPPED);
        assertThat(row(NotificationChannel.SMS).status()).isEqualTo(Status.QUEUED);
    }

    @Test
    void repeatedDispatchOfTheSameFactWritesNothingNew() {
        template("order_accepted", NotificationChannel.PUSH, true, "p");
        template("order_accepted", NotificationChannel.IN_APP, true, "in-app {orderReference}");

        assertThat(dispatcher.dispatch(request("order_accepted", NotificationDispatcher.ALL_CHANNELS, "fact-3"))).isEqualTo(2);
        assertThat(dispatcher.dispatch(request("order_accepted", NotificationDispatcher.ALL_CHANNELS, "fact-3"))).isZero();

        assertThat(inserted).hasSize(2);
        assertThat(row(NotificationChannel.IN_APP).status()).isEqualTo(Status.SENT);
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture()); // once: the duplicate wrote no second inbox row
        assertThat(saved.getValue().getMessage()).isEqualTo("in-app PA-7");
    }

    @Test
    void inAppNotificationsFanOutToExternalChannelsWithoutReusingTheirMessage() {
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenant));
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationService service = new NotificationService(notifications, mock(AppUserProfileRepository.class), tenantContext);
        service.setDispatcher(dispatcher);
        template("order_delivery_code", NotificationChannel.PUSH, false, "never");
        template("order_rejected", NotificationChannel.PUSH, true, "Pedido {orderReference} recusado");

        service.create(profile, "CUSTOMER", "order_delivery_code", "Delivery Confirmation Code", "Your code is 123456.", "PA-7");
        service.create(profile, "CUSTOMER", "order_rejected", "Order Rejected", "Reason: private text", "PA-7");

        assertThat(inserted).singleElement().satisfies(row -> {
            assertThat(row.templateKey()).isEqualTo("order_rejected");
            assertThat(row.body()).isEqualTo("Pedido PA-7 recusado").doesNotContain("private text");
            assertThat(row.idempotencyKey()).startsWith("inapp:").endsWith(":PUSH");
        });
        assertThat(inserted).noneMatch(row -> row.body().contains("123456"));
        verify(templates, never()).effective(eq(tenant), eq("order_delivery_code"), eq(NotificationChannel.IN_APP));
    }

    private NewDelivery row(NotificationChannel channel) {
        return inserted.stream().filter(row -> row.channel() == channel).findFirst().orElseThrow();
    }

    private NotificationRequest request(String key, Set<NotificationChannel> channels, String dedupKey) {
        return new NotificationRequest(tenant, profile, "CUSTOMER", key, "PA-7", Map.of("orderReference", "PA-7"), channels, dedupKey);
    }

    private void template(String key, NotificationChannel channel, boolean active, String body) {
        NotificationTemplate template = NotificationTemplate.overrideOf(null, key, channel, "pt-PT", 1);
        template.update("Título", body, active, "test", false);
        when(templates.effective(tenant, key, channel)).thenReturn(Optional.of(template));
    }
}
