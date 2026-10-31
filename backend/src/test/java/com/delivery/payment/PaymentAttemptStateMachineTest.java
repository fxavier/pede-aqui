package com.delivery.payment;

import static com.delivery.payment.entity.PaymentAttemptStatus.EXPIRED;
import static com.delivery.payment.entity.PaymentAttemptStatus.FAILED;
import static com.delivery.payment.entity.PaymentAttemptStatus.INITIATED;
import static com.delivery.payment.entity.PaymentAttemptStatus.PENDING_CUSTOMER;
import static com.delivery.payment.entity.PaymentAttemptStatus.SUCCEEDED;
import static com.delivery.payment.entity.PaymentAttemptStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.payment.entity.PaymentAttempt;
import com.delivery.payment.entity.PaymentAttemptStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pins the payment attempt state machine (LAC-32, W7-A) and the attempt's transition/backoff rules. */
class PaymentAttemptStateMachineTest {

    private static final Map<PaymentAttemptStatus, Set<PaymentAttemptStatus>> TABLE = Map.of(
            INITIATED, EnumSet.of(PENDING_CUSTOMER, SUCCEEDED, FAILED, EXPIRED, UNKNOWN),
            PENDING_CUSTOMER, EnumSet.of(SUCCEEDED, FAILED, EXPIRED, UNKNOWN),
            UNKNOWN, EnumSet.of(PENDING_CUSTOMER, SUCCEEDED, FAILED, EXPIRED),
            EXPIRED, EnumSet.of(SUCCEEDED),
            SUCCEEDED, EnumSet.noneOf(PaymentAttemptStatus.class),
            FAILED, EnumSet.noneOf(PaymentAttemptStatus.class));

    @Test
    void transitionTableIsExactlyTheDocumentedOne() {
        for (PaymentAttemptStatus from : PaymentAttemptStatus.values()) {
            for (PaymentAttemptStatus to : PaymentAttemptStatus.values()) {
                assertThat(from.canTransitionTo(to)).as(from + " -> " + to).isEqualTo(TABLE.get(from).contains(to));
            }
        }
    }

    @Test
    void openStatusesAreTheOnesStillWaitingForAnAnswer() {
        assertThat(PaymentAttemptStatus.OPEN).containsExactlyInAnyOrder(INITIATED, PENDING_CUSTOMER, UNKNOWN);
        assertThat(SUCCEEDED.isOpen()).isFalse();
        assertThat(EXPIRED.isOpen()).isFalse();
    }

    @Test
    void finalTransitionStopsPollingAndRepeatsAreIgnored() {
        Instant t0 = Instant.parse("2026-10-31T10:00:00Z");
        PaymentAttempt attempt = attempt(t0);
        assertThat(attempt.getNextPollAt()).isEqualTo(t0.plusSeconds(60));

        assertThat(attempt.transitionTo(PENDING_CUSTOMER, null, t0.plusSeconds(1))).isTrue();
        assertThat(attempt.transitionTo(PENDING_CUSTOMER, null, t0.plusSeconds(2))).as("repeat").isFalse();
        assertThat(attempt.transitionTo(SUCCEEDED, null, t0.plusSeconds(3))).isTrue();
        assertThat(attempt.getCompletedAt()).isEqualTo(t0.plusSeconds(3));
        assertThat(attempt.getNextPollAt()).isNull();
        assertThat(attempt.transitionTo(FAILED, "LATE", t0.plusSeconds(4))).as("FAILED after SUCCEEDED").isFalse();
        assertThat(attempt.getStatus()).isEqualTo(SUCCEEDED);
        assertThat(attempt.getErrorCode()).isNull();
    }

    @Test
    void expiredAttemptStillAcceptsAPspConfirmedLateSuccess() {
        Instant t0 = Instant.parse("2026-10-31T10:00:00Z");
        PaymentAttempt attempt = attempt(t0);
        assertThat(attempt.transitionTo(EXPIRED, "attempt_ttl_elapsed", t0.plusSeconds(200))).isTrue();
        assertThat(attempt.transitionTo(FAILED, null, t0.plusSeconds(201))).isFalse();
        assertThat(attempt.transitionTo(SUCCEEDED, null, t0.plusSeconds(300))).isTrue();
    }

    @Test
    void pollBackoffDoublesUpToTheCap() {
        Instant t0 = Instant.parse("2026-10-31T10:00:00Z");
        PaymentAttempt attempt = attempt(t0);
        Duration base = Duration.ofSeconds(15);
        Duration max = Duration.ofMinutes(30);
        attempt.schedulePoll(t0, base, max);
        assertThat(attempt.getNextPollAt()).isEqualTo(t0.plusSeconds(30));
        attempt.schedulePoll(t0, base, max);
        assertThat(attempt.getNextPollAt()).isEqualTo(t0.plusSeconds(60));
        for (int i = 0; i < 30; i++) {
            attempt.schedulePoll(t0, base, max);
        }
        assertThat(attempt.getNextPollAt()).isEqualTo(t0.plus(max));
    }

    @Test
    void providerReferenceIsWrittenOnce() {
        PaymentAttempt attempt = attempt(Instant.now());
        attempt.recordProviderReference("SBX-1", Instant.now());
        attempt.recordProviderReference("SBX-2", Instant.now());
        assertThat(attempt.getProviderReference()).isEqualTo("SBX-1");
    }

    private static PaymentAttempt attempt(Instant now) {
        return new PaymentAttempt(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "sandbox", "PA1",
                new BigDecimal("100.00"), "MZN", "h", "567", now, now.plusSeconds(60));
    }
}
