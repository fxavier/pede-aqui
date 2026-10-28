package com.delivery.customer.service;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.customer.dto.CustomerAddressRequest;
import com.delivery.customer.dto.CustomerAddressResponse;
import com.delivery.customer.entity.CustomerAddress;
import com.delivery.customer.repository.CustomerAddressRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the calling customer's saved addresses. Ownership is resolved from the authenticated profile in the
 * resolved tenant (ADR-0001), never from a path or body id: another customer's address answers 404.
 */
@Service
public class CustomerAddressService {
    /** Upper bound per customer, so the list endpoint stays small and unpaginated. */
    static final int MAX_ADDRESSES_PER_CUSTOMER = 20;

    private final CustomerAddressRepository repository;
    private final AppUserProfileRepository userProfileRepository;
    private final TenantContext tenantContext;

    public CustomerAddressService(CustomerAddressRepository repository, AppUserProfileRepository userProfileRepository, TenantContext tenantContext) {
        this.repository = repository;
        this.userProfileRepository = userProfileRepository;
        this.tenantContext = tenantContext;
    }

    /** Lists the caller's addresses, oldest first. */
    @Transactional(readOnly = true)
    public List<CustomerAddressResponse> listMine() {
        Owner owner = currentOwner();
        return repository.findByTenantIdAndCustomerIdOrderByCreatedAtAsc(owner.tenantId(), owner.customerId()).stream().map(CustomerAddressService::toResponse).toList();
    }

    /** Returns one of the caller's addresses (404 when missing or owned by someone else). */
    @Transactional(readOnly = true)
    public CustomerAddressResponse getMine(UUID addressId) {
        Owner owner = currentOwner();
        return toResponse(find(owner, addressId));
    }

    /** Creates an address for the caller, up to {@value #MAX_ADDRESSES_PER_CUSTOMER} per customer. */
    @Transactional
    public CustomerAddressResponse create(CustomerAddressRequest request) {
        validateCoordinates(request);
        Owner owner = currentOwner();
        if (repository.countByTenantIdAndCustomerId(owner.tenantId(), owner.customerId()) >= MAX_ADDRESSES_PER_CUSTOMER) {
            throw new BusinessException("address_limit_reached", "Maximum number of saved addresses reached", HttpStatus.CONFLICT);
        }
        CustomerAddress address = new CustomerAddress(UUID.randomUUID(), owner.tenantId(), owner.customerId());
        apply(address, request);
        return toResponse(repository.save(address));
    }

    /** Replaces one of the caller's addresses; orders already placed keep their own snapshot. */
    @Transactional
    public CustomerAddressResponse update(UUID addressId, CustomerAddressRequest request) {
        validateCoordinates(request);
        CustomerAddress address = find(currentOwner(), addressId);
        apply(address, request);
        return toResponse(address);
    }

    /** Deletes one of the caller's addresses; orders already placed keep their own snapshot. */
    @Transactional
    public void delete(UUID addressId) {
        repository.delete(find(currentOwner(), addressId));
    }

    private CustomerAddress find(Owner owner, UUID addressId) {
        return repository.findByTenantIdAndCustomerIdAndId(owner.tenantId(), owner.customerId(), addressId)
                .orElseThrow(() -> new NotFoundException("Address was not found"));
    }

    private static void apply(CustomerAddress address, CustomerAddressRequest r) {
        address.update(r.label().trim(), r.recipientName().trim(), blankToNull(r.phone()), r.line1().trim(), blankToNull(r.line2()), r.city().trim(),
                blankToNull(r.state()), blankToNull(r.postalCode()), r.latitude(), r.longitude(), blankToNull(r.deliveryInstructions()));
    }

    private static void validateCoordinates(CustomerAddressRequest request) {
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new BusinessException("invalid_coordinates", "Latitude and longitude must be provided together", HttpStatus.BAD_REQUEST);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Owner currentOwner() {
        UUID tenantId = tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
        String keycloakUserId = tenantContext.currentKeycloakUserId().orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        AppUserProfile profile = userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId)
                .orElseThrow(() -> new NotFoundException("User profile was not found"));
        return new Owner(tenantId, profile.getId());
    }

    static CustomerAddressResponse toResponse(CustomerAddress a) {
        return new CustomerAddressResponse(a.getId(), a.getLabel(), a.getRecipientName(), a.getPhone(), a.getLine1(), a.getLine2(), a.getCity(),
                a.getState(), a.getPostalCode(), a.getLatitude(), a.getLongitude(), a.getDeliveryInstructions(), a.getCreatedAt(), a.getUpdatedAt());
    }

    private record Owner(UUID tenantId, UUID customerId) {}
}
