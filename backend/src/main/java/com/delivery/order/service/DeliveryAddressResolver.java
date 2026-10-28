package com.delivery.order.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.customer.entity.CustomerAddress;
import com.delivery.customer.repository.CustomerAddressRepository;
import com.delivery.order.entity.DeliveryAddressSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Turns the checkout's addressId into an immutable order snapshot (INC-12, AS-02). The address must belong to
 * the checking-out customer in the resolved tenant (404 otherwise, existence not revealed). A missing addressId
 * is only accepted while the legacy compatibility window is open (docs/api/API-MIGRATION.md).
 */
@Component
public class DeliveryAddressResolver {
    private final CustomerAddressRepository addressRepository;
    private final boolean legacyWithoutAddressEnabled;

    public DeliveryAddressResolver(CustomerAddressRepository addressRepository,
                                   @Value("${app.checkout.legacy-without-address-enabled:true}") boolean legacyWithoutAddressEnabled) {
        this.addressRepository = addressRepository;
        this.legacyWithoutAddressEnabled = legacyWithoutAddressEnabled;
    }

    /** Snapshot of the customer's address, or empty for a legacy request without addressId (400 once the window closes). */
    public Optional<DeliveryAddressSnapshot> resolve(UUID tenantId, UUID customerId, UUID addressId) {
        if (addressId == null) {
            if (!legacyWithoutAddressEnabled) {
                throw new BusinessException("address_required", "A delivery address (addressId) is required", HttpStatus.BAD_REQUEST);
            }
            return Optional.empty();
        }
        CustomerAddress address = addressRepository.findByTenantIdAndCustomerIdAndId(tenantId, customerId, addressId)
                .orElseThrow(() -> new NotFoundException("Address was not found"));
        return Optional.of(new DeliveryAddressSnapshot(address.getId(), address.getRecipientName(), address.getPhone(), address.getLine1(),
                address.getLine2(), address.getCity(), address.getState(), address.getPostalCode(), address.getLatitude(), address.getLongitude(),
                address.getDeliveryInstructions()));
    }

    /** True while checkout still accepts requests without addressId. */
    public boolean isLegacyWithoutAddressEnabled() { return legacyWithoutAddressEnabled; }
}
