package com.delivery.vendor.api;

import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Read-only vendor display-name lookup for other modules (ADR-0002 vendor API), so they can label records with the
 * store name without reaching into the vendor repository or entity. No tenant filtering: callers pass vendor ids
 * they already obtained from their own tenant-scoped rows.
 */
@Service
public class VendorNames {
    private final VendorRepository vendorRepository;

    public VendorNames(VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    /** Name per vendor id; ids that do not exist are absent from the map. */
    public Map<UUID, String> namesOf(Set<UUID> vendorIds) {
        if (vendorIds.isEmpty()) {
            return Map.of();
        }
        return vendorRepository.findAllById(vendorIds).stream().collect(Collectors.toMap(Vendor::getId, Vendor::getName));
    }
}
