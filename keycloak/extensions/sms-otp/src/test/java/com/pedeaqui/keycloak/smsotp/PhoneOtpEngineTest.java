package com.pedeaqui.keycloak.smsotp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pedeaqui.keycloak.smsotp.PhoneOtpEngine.Challenge;
import com.pedeaqui.keycloak.smsotp.PhoneOtpEngine.Expired;
import com.pedeaqui.keycloak.smsotp.PhoneOtpEngine.Issued;
import com.pedeaqui.keycloak.smsotp.PhoneOtpEngine.Locked;
import com.pedeaqui.keycloak.smsotp.PhoneOtpEngine.Reason;
import com.pedeaqui.keycloak.smsotp.PhoneOtpEngine.Refused;
import com.pedeaqui.keycloak.smsotp.PhoneOtpEngine.Verified;
import com.pedeaqui.keycloak.smsotp.PhoneOtpEngine.WrongCode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhoneOtpEngineTest {
    private static final byte[] SECRET = "unit-test-secret-unit-test-secret-0123".getBytes(StandardCharsets.UTF_8);
    private static final String PHONE = "+258841234567";
    private static final String OTHER_PHONE = "+258821234567";
    private static final String IP = "203.0.113.7";

    private MutableClock clock;
    private InMemoryOtpStore store;
    private PhoneOtpEngine engine;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-29T10:00:00Z"));
        store = new InMemoryOtpStore(clock);
        engine = new PhoneOtpEngine(OtpPolicy.defaults(), store, SECRET, clock, new SecureRandom());
    }

    private Issued issue(String phone, String ip) {
        var result = engine.issue(phone, ip);
        assertThat(result).isInstanceOf(Issued.class);
        return (Issued) result;
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void issuesSixDigitCodeAndStoresOnlyAnHmacThatExpiresAfterFiveMinutes() {
        Issued issued = issue(PHONE, IP);

        assertThat(issued.code()).matches("\\d{6}");
        Challenge challenge = issued.challenge();
        assertThat(challenge.phone()).isEqualTo(PHONE);
        assertThat(challenge.hash()).doesNotContain(issued.code()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(challenge.expiresAtEpochSecond()).isEqualTo(clock.instant().getEpochSecond() + 300);
        assertThat(store.raw().keySet()).noneMatch(k -> k.contains("841234567") || k.contains(IP));
    }

    @Test
    void hashIsBoundToChallengeIdAndPhone() {
        Issued issued = issue(PHONE, IP);
        Challenge c = issued.challenge();
        Challenge otherPhone = new Challenge(c.id(), OTHER_PHONE, c.hash(), c.expiresAtEpochSecond());

        assertThat(engine.verify(otherPhone, issued.code(), IP)).isInstanceOf(WrongCode.class);
        assertThat(engine.verify(c, issued.code(), IP)).isEqualTo(new Verified(PHONE));
    }

    @Test
    void correctCodeWithinTtlVerifies() {
        Issued issued = issue(PHONE, IP);
        clock.advance(Duration.ofSeconds(299));

        assertThat(engine.verify(issued.challenge(), " " + issued.code() + " ", IP)).isEqualTo(new Verified(PHONE));
    }

    @Test
    void codeExpiresAfterTtl() {
        Issued issued = issue(PHONE, IP);
        clock.advance(Duration.ofSeconds(300));

        assertThat(engine.verify(issued.challenge(), issued.code(), IP)).isInstanceOf(Expired.class);
    }

    @Test
    void fifthWrongCodeLocksThePhoneAndEvenTheRightCodeIsRefusedWhileLocked() {
        Issued issued = issue(PHONE, IP);
        String bad = wrong(issued.code());

        assertThat(engine.verify(issued.challenge(), bad, IP)).isEqualTo(new WrongCode(4));
        assertThat(engine.verify(issued.challenge(), bad, IP)).isEqualTo(new WrongCode(3));
        assertThat(engine.verify(issued.challenge(), bad, IP)).isEqualTo(new WrongCode(2));
        assertThat(engine.verify(issued.challenge(), bad, IP)).isEqualTo(new WrongCode(1));
        assertThat(engine.verify(issued.challenge(), bad, IP)).isEqualTo(new Locked(900));

        assertThat(engine.verify(issued.challenge(), issued.code(), IP)).isInstanceOf(Locked.class);
        assertThat(engine.issue(PHONE, "198.51.100.1")).isEqualTo(new Refused(Reason.LOCKED, 900));
        assertThat(engine.phoneLockRemaining(PHONE)).isEqualTo(900);
    }

    @Test
    void failuresAreCountedPerPhoneAcrossLoginSessionsAndIps() {
        Issued first = issue(PHONE, IP);
        for (int i = 0; i < 3; i++) {
            engine.verify(first.challenge(), wrong(first.code()), IP);
        }
        clock.advance(Duration.ofSeconds(61));
        Issued second = issue(PHONE, "198.51.100.1"); // new browser session, new IP

        assertThat(engine.verify(second.challenge(), wrong(second.code()), "198.51.100.1")).isEqualTo(new WrongCode(1));
        assertThat(engine.verify(second.challenge(), wrong(second.code()), "198.51.100.2")).isInstanceOf(Locked.class);
    }

    @Test
    void successResetsTheFailureCounter() {
        Issued issued = issue(PHONE, IP);
        engine.verify(issued.challenge(), wrong(issued.code()), IP);
        engine.verify(issued.challenge(), wrong(issued.code()), IP);
        assertThat(engine.verify(issued.challenge(), issued.code(), IP)).isInstanceOf(Verified.class);

        clock.advance(Duration.ofSeconds(61));
        Issued next = issue(PHONE, IP);
        assertThat(engine.verify(next.challenge(), wrong(next.code()), IP)).isEqualTo(new WrongCode(4));
    }

    @Test
    void lockExpiresAndIsProgressiveWithin24Hours() {
        Issued issued = issue(PHONE, IP);
        for (int i = 0; i < 5; i++) {
            engine.verify(issued.challenge(), wrong(issued.code()), IP);
        }
        clock.advance(Duration.ofSeconds(900));
        assertThat(engine.phoneLockRemaining(PHONE)).isZero();

        Issued again = issue(PHONE, IP);
        for (int i = 0; i < 4; i++) {
            assertThat(engine.verify(again.challenge(), wrong(again.code()), IP)).isInstanceOf(WrongCode.class);
        }
        assertThat(engine.verify(again.challenge(), wrong(again.code()), IP)).isEqualTo(new Locked(1800));
        assertThat(engine.phoneLockRemaining(PHONE)).isEqualTo(1800);
    }

    @Test
    void malformedCodesCountAsFailures() {
        Issued issued = issue(PHONE, IP);

        assertThat(engine.verify(issued.challenge(), "12345", IP)).isEqualTo(new WrongCode(4));
        assertThat(engine.verify(issued.challenge(), null, IP)).isEqualTo(new WrongCode(3));
        assertThat(engine.verify(issued.challenge(), "abcdef", IP)).isEqualTo(new WrongCode(2));
    }

    @Test
    void resendCooldownIsSixtySeconds() {
        issue(PHONE, IP);
        clock.advance(Duration.ofSeconds(20));

        assertThat(engine.issue(PHONE, IP)).isEqualTo(new Refused(Reason.COOLDOWN, 40));
        clock.advance(Duration.ofSeconds(40));
        assertThat(engine.issue(PHONE, IP)).isInstanceOf(Issued.class);
    }

    @Test
    void atMostThreeSmsPerPhonePerFifteenMinutes() {
        for (int i = 0; i < 3; i++) {
            issue(PHONE, "198.51.100." + i);
            clock.advance(Duration.ofSeconds(61));
        }
        assertThat(engine.issue(PHONE, "198.51.100.9")).isEqualTo(new Refused(Reason.PHONE_QUOTA, 0));

        clock.advance(Duration.ofSeconds(900 - 3 * 61 + 1)); // first slot expired (sliding window)
        assertThat(engine.issue(PHONE, IP)).isInstanceOf(Issued.class);
    }

    @Test
    void atMostTenSmsPerIpPerFifteenMinutes() {
        for (int i = 0; i < 10; i++) {
            issue(String.format("+25884%07d", i), IP);
        }
        assertThat(engine.issue("+258849999999", IP)).isEqualTo(new Refused(Reason.IP_QUOTA, 0));
        assertThat(engine.issue("+258849999999", "198.51.100.1")).isInstanceOf(Issued.class);
    }

    @Test
    void ipIsLockedAfterTwentyWrongCodesAcrossPhones() {
        OtpPolicy policy = OtpPolicy.defaults();
        int failures = 0;
        Locked lock = null;
        for (int p = 0; lock == null && p < 10; p++) {
            Issued issued = issue(String.format("+25882%07d", p), IP);
            for (int i = 0; i < 4 && lock == null; i++) { // stay below the per-phone lock
                var r = engine.verify(issued.challenge(), wrong(issued.code()), IP);
                failures++;
                if (r instanceof Locked l) {
                    lock = l;
                }
            }
        }
        assertThat(failures).isEqualTo(policy.failuresPerIp());
        assertThat(lock).isNotNull();
        assertThat(engine.issue("+258831234567", IP)).isInstanceOf(Refused.class);
        assertThat(engine.issue("+258831234567", "198.51.100.1")).isInstanceOf(Issued.class);
    }

    @Test
    void refusesShortSecrets() {
        assertThatThrownBy(() -> new PhoneOtpEngine(OtpPolicy.defaults(), store, new byte[16], clock, new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameCodeUnderAnotherSecretDoesNotVerify() {
        Issued issued = issue(PHONE, IP);
        PhoneOtpEngine other = new PhoneOtpEngine(OtpPolicy.defaults(), new InMemoryOtpStore(clock),
                "another-secret-another-secret-another".getBytes(StandardCharsets.UTF_8), clock, new SecureRandom());

        assertThat(other.verify(issued.challenge(), issued.code(), IP)).isInstanceOf(WrongCode.class);
    }
}
