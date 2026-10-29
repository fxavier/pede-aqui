package com.delivery.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.vendor.api.StoreAvailabilityService;
import com.delivery.vendor.api.StoreClosedException;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.entity.VendorClosure;
import com.delivery.vendor.entity.VendorOpeningHour;
import com.delivery.vendor.repository.VendorClosureRepository;
import com.delivery.vendor.repository.VendorOpeningHourRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * LAC-27: opening hours (Africa/Maputo, UTC+2, no DST), exceptional closures and the optional available flag.
 * Reference week: Monday 2026-11-02 … Sunday 2026-11-08.
 */
class StoreAvailabilityServiceTest {
    private static final ZoneId MAPUTO = ZoneId.of("Africa/Maputo");
    private static final UUID TENANT = UUID.randomUUID();

    private final VendorRepository vendorRepository = mock(VendorRepository.class);
    private final VendorOpeningHourRepository hourRepository = mock(VendorOpeningHourRepository.class);
    private final VendorClosureRepository closureRepository = mock(VendorClosureRepository.class);
    private final List<VendorOpeningHour> hours = new ArrayList<>();
    private final List<VendorClosure> closures = new ArrayList<>();
    private Vendor vendor;

    @BeforeEach
    void setUp() {
        vendor = new Vendor(UUID.randomUUID(), TENANT, "Loja", null, null, null, "Dona", "1", "84", "Rua", "", null);
        when(vendorRepository.findAllById(any())).thenAnswer(inv -> List.of(vendor));
        when(hourRepository.findByVendorIdIn(anyCollection())).thenReturn(hours);
        when(closureRepository.findByVendorIdInAndEndsAtAfter(anyCollection(), any())).thenAnswer(inv -> {
            Instant at = inv.getArgument(1);
            return closures.stream().filter(c -> c.getEndsAt().isAfter(at)).toList();
        });
    }

    @Test
    void vendorWithoutOpeningHoursIsAlwaysOpen() {
        assertThat(service(false).isOpen(vendor.getId(), maputo(2026, 11, 2, 3, 0))).isTrue();
    }

    @Test
    void openingHoursAreEvaluatedInMaputoTime() {
        weekdays(LocalTime.of(8, 0), LocalTime.of(20, 0));
        StoreAvailabilityService service = service(false);

        // 06:30 UTC = 08:30 Maputo → open; 05:30 UTC = 07:30 Maputo → closed.
        assertThat(service.isOpen(vendor.getId(), Instant.parse("2026-11-02T06:30:00Z"))).isTrue();
        assertThat(service.isOpen(vendor.getId(), Instant.parse("2026-11-02T05:30:00Z"))).isFalse();
        // closesAt is exclusive
        assertThat(service.isOpen(vendor.getId(), maputo(2026, 11, 2, 19, 59))).isTrue();
        assertThat(service.isOpen(vendor.getId(), maputo(2026, 11, 2, 20, 0))).isFalse();
    }

    @Test
    void dayWithoutRuleOrMarkedClosedIsClosedAndNextOpeningIsTheNextOpenDay() {
        weekdays(LocalTime.of(8, 0), LocalTime.of(20, 0)); // Mon–Fri
        hours.add(new VendorOpeningHour(UUID.randomUUID(), TENANT, vendor.getId(), 6, null, null, true)); // Saturday closed
        // Sunday has no row

        Instant saturdayNoon = maputo(2026, 11, 7, 12, 0);
        StoreAvailabilityService.Availability state = service(false).availability(List.of(vendor.getId()), saturdayNoon).get(vendor.getId());

        assertThat(state.open()).isFalse();
        assertThat(state.nextOpeningAt()).isEqualTo(maputo(2026, 11, 9, 8, 0)); // Monday 08:00
    }

    @Test
    void closureCoveringTheInstantClosesTheStoreUntilItEnds() {
        Instant now = maputo(2026, 11, 2, 12, 0);
        closures.add(closure(now.minusSeconds(3600), now.plusSeconds(7200)));
        StoreAvailabilityService service = service(false);

        StoreAvailabilityService.Availability state = service.availability(List.of(vendor.getId()), now).get(vendor.getId());
        assertThat(state.open()).isFalse();
        assertThat(state.nextOpeningAt()).isEqualTo(now.plusSeconds(7200));
        assertThat(service.isOpen(vendor.getId(), now.plusSeconds(7200))).isTrue(); // endsAt is exclusive
    }

    @Test
    void closureEndingAfterHoursReopensAtTheNextOpeningTime() {
        weekdays(LocalTime.of(8, 0), LocalTime.of(20, 0));
        Instant now = maputo(2026, 11, 2, 12, 0);
        closures.add(closure(maputo(2026, 11, 2, 9, 0), maputo(2026, 11, 2, 22, 0)));

        StoreAvailabilityService.Availability state = service(false).availability(List.of(vendor.getId()), now).get(vendor.getId());

        assertThat(state.open()).isFalse();
        assertThat(state.nextOpeningAt()).isEqualTo(maputo(2026, 11, 3, 8, 0));
    }

    @Test
    void availableFlagIsOnlyEnforcedWhenConfigured() {
        Instant now = maputo(2026, 11, 2, 12, 0);
        // new Vendor(...) starts with available = false
        assertThat(service(false).isOpen(vendor.getId(), now)).isTrue();
        assertThat(service(true).isOpen(vendor.getId(), now)).isFalse();
        vendor.setAvailability(true);
        assertThat(service(true).isOpen(vendor.getId(), now)).isTrue();
    }

    @Test
    void unknownVendorIsClosed() {
        when(vendorRepository.findAllById(any())).thenReturn(List.of());
        assertThat(service(false).isOpen(UUID.randomUUID(), Instant.now())).isFalse();
    }

    @Test
    void requireOpenThrows422StoreClosedWithTheNextOpening() {
        Instant now = maputo(2026, 11, 2, 12, 0);
        closures.add(closure(now.minusSeconds(60), now.plusSeconds(600)));
        StoreAvailabilityService service = new StoreAvailabilityService(vendorRepository, hourRepository, closureRepository,
                MAPUTO, false, Clock.fixed(now, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.requireOpen(vendor.getId()))
                .isInstanceOfSatisfying(StoreClosedException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("store_closed");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getNextOpeningAt()).isEqualTo(now.plusSeconds(600));
                })
                .isInstanceOf(BusinessException.class);
    }

    private void weekdays(LocalTime opensAt, LocalTime closesAt) {
        for (int day = 1; day <= 5; day++) {
            hours.add(new VendorOpeningHour(UUID.randomUUID(), TENANT, vendor.getId(), day, opensAt, closesAt, false));
        }
    }

    private VendorClosure closure(Instant startsAt, Instant endsAt) {
        return new VendorClosure(UUID.randomUUID(), TENANT, vendor.getId(), startsAt, endsAt, "Inventário", "ops");
    }

    private StoreAvailabilityService service(boolean enforceAvailableFlag) {
        return new StoreAvailabilityService(vendorRepository, hourRepository, closureRepository, MAPUTO, enforceAvailableFlag, Clock.systemUTC());
    }

    private static Instant maputo(int year, int month, int day, int hour, int minute) {
        return java.time.LocalDateTime.of(year, month, day, hour, minute).atZone(MAPUTO).toInstant();
    }
}
