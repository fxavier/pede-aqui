package com.delivery.vendor.api;

import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.entity.VendorClosure;
import com.delivery.vendor.entity.VendorOpeningHour;
import com.delivery.vendor.repository.VendorClosureRepository;
import com.delivery.vendor.repository.VendorOpeningHourRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides whether a store accepts orders at a given instant (LAC-27). A store is open when:
 * <ul>
 *   <li>its weekly {@code vendor_opening_hours} (V006) allow the local time in the store time zone
 *       ({@code app.store-availability.timezone}, default Africa/Maputo). Day 1 = Monday … 7 = Sunday (ISO).
 *       A vendor without any opening-hour rows is treated as always open (legacy vendors never configured hours);
 *       once hours exist, a day without a row is closed. Intervals are [opensAt, closesAt).</li>
 *   <li>no {@code vendor_closures} (V055) row covers the instant;</li>
 *   <li>and, only when {@code app.store-availability.enforce-available-flag=true}, {@code vendors.available} is true
 *       (off by default: the column defaults to FALSE and no UI toggles it yet, so enforcing it would close every
 *       existing store).</li>
 * </ul>
 * Used by cart addItem, checkout revalidation and vendor search.
 */
@Service
public class StoreAvailabilityService {
    public static final String STORE_CLOSED_CODE = "store_closed";
    private static final int LOOKAHEAD_DAYS = 8;

    private final VendorRepository vendorRepository;
    private final VendorOpeningHourRepository openingHourRepository;
    private final VendorClosureRepository closureRepository;
    private final ZoneId zone;
    private final boolean enforceAvailableFlag;
    private final Clock clock;

    @Autowired
    public StoreAvailabilityService(VendorRepository vendorRepository, VendorOpeningHourRepository openingHourRepository,
                                    VendorClosureRepository closureRepository,
                                    @Value("${app.store-availability.timezone:Africa/Maputo}") String timezone,
                                    @Value("${app.store-availability.enforce-available-flag:false}") boolean enforceAvailableFlag) {
        this(vendorRepository, openingHourRepository, closureRepository, ZoneId.of(timezone), enforceAvailableFlag, Clock.systemUTC());
    }

    /** Test seam: fixed clock and zone. */
    public StoreAvailabilityService(VendorRepository vendorRepository, VendorOpeningHourRepository openingHourRepository,
                                    VendorClosureRepository closureRepository, ZoneId zone, boolean enforceAvailableFlag, Clock clock) {
        this.vendorRepository = vendorRepository;
        this.openingHourRepository = openingHourRepository;
        this.closureRepository = closureRepository;
        this.zone = zone;
        this.enforceAvailableFlag = enforceAvailableFlag;
        this.clock = clock;
    }

    /** Open state and next opening instant of one store. */
    public record Availability(boolean open, Instant nextOpeningAt) {}

    /** True when the store accepts orders at the given instant. */
    @Transactional(readOnly = true)
    public boolean isOpen(UUID vendorId, Instant at) {
        return availability(List.of(vendorId), at).get(vendorId).open();
    }

    /** Throws 422 {@code store_closed} (with {@code details.nextOpeningAt}) unless the store is open now. */
    @Transactional(readOnly = true)
    public void requireOpen(UUID vendorId) {
        Availability availability = availability(List.of(vendorId), clock.instant()).get(vendorId);
        if (!availability.open()) {
            throw new StoreClosedException(vendorId, availability.nextOpeningAt());
        }
    }

    /** Current availability of several stores, loaded in three queries (search). */
    @Transactional(readOnly = true)
    public Map<UUID, Availability> availabilityNow(Collection<UUID> vendorIds) {
        return availability(vendorIds, clock.instant());
    }

    /** Availability of several stores at an instant; unknown vendor ids are reported closed. */
    @Transactional(readOnly = true)
    public Map<UUID, Availability> availability(Collection<UUID> vendorIds, Instant at) {
        Map<UUID, Availability> result = new HashMap<>();
        if (vendorIds.isEmpty()) {
            return result;
        }
        Set<UUID> ids = Set.copyOf(vendorIds);
        Map<UUID, Boolean> availableFlag = vendorRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Vendor::getId, Vendor::isAvailable));
        Map<UUID, List<VendorOpeningHour>> hoursByVendor = openingHourRepository.findByVendorIdIn(ids).stream()
                .collect(Collectors.groupingBy(VendorOpeningHour::getVendorId));
        Map<UUID, List<VendorClosure>> closuresByVendor = closureRepository.findByVendorIdInAndEndsAtAfter(ids, at).stream()
                .collect(Collectors.groupingBy(VendorClosure::getVendorId));
        for (UUID id : ids) {
            Boolean flag = availableFlag.get(id);
            if (flag == null || (enforceAvailableFlag && !flag)) {
                result.put(id, new Availability(false, null)); // unknown vendor, or paused with no known reopening
                continue;
            }
            List<VendorOpeningHour> hours = hoursByVendor.getOrDefault(id, List.of());
            List<VendorClosure> closures = closuresByVendor.getOrDefault(id, List.of());
            boolean open = isOpenAt(hours, closures, at);
            result.put(id, new Availability(open, open ? null : nextOpening(hours, closures, at)));
        }
        return result;
    }

    private boolean isOpenAt(List<VendorOpeningHour> hours, List<VendorClosure> closures, Instant at) {
        return closures.stream().noneMatch(closure -> closure.covers(at)) && withinOpeningHours(hours, at);
    }

    private boolean withinOpeningHours(List<VendorOpeningHour> hours, Instant at) {
        if (hours.isEmpty()) {
            return true;
        }
        ZonedDateTime local = at.atZone(zone);
        LocalTime time = local.toLocalTime();
        int day = local.getDayOfWeek().getValue();
        return hours.stream().anyMatch(rule -> rule.getDayOfWeek() == day && !rule.isClosed()
                && rule.getOpensAt() != null && rule.getClosesAt() != null
                && !time.isBefore(rule.getOpensAt()) && time.isBefore(rule.getClosesAt()));
    }

    /**
     * Earliest instant after {@code at} within the look-ahead window when the store is open again: the candidates are
     * the opening times of the next days and the ends of the closures; the first candidate that is open wins.
     * Null when nothing in the window reopens the store.
     */
    private Instant nextOpening(List<VendorOpeningHour> hours, List<VendorClosure> closures, Instant at) {
        List<Instant> candidates = new ArrayList<>();
        closures.forEach(closure -> candidates.add(closure.getEndsAt()));
        LocalDate today = at.atZone(zone).toLocalDate();
        for (int offset = 0; offset < LOOKAHEAD_DAYS; offset++) {
            LocalDate date = today.plusDays(offset);
            for (VendorOpeningHour rule : hours) {
                if (rule.getDayOfWeek() == date.getDayOfWeek().getValue() && !rule.isClosed() && rule.getOpensAt() != null) {
                    candidates.add(date.atTime(rule.getOpensAt()).atZone(zone).toInstant());
                }
            }
        }
        return candidates.stream()
                .filter(candidate -> candidate.isAfter(at))
                .sorted()
                .filter(candidate -> isOpenAt(hours, closures, candidate))
                .findFirst()
                .orElse(null);
    }
}
