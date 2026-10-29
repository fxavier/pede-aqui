package com.delivery.vendor.api;

import com.delivery.vendor.entity.MerchantRole;
import com.delivery.vendor.entity.MerchantUser;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.MerchantUserRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Vendor side of merchant onboarding (ADR-0002 C9): creates the vendor pending verification and its OWNER
 * merchant_users association for the tenant module's registration/provisioning saga. Runs in the caller's
 * transaction (no transaction of its own).
 */
@Service
public class MerchantOnboarding {
    private final VendorRepository vendorRepository;
    private final MerchantUserRepository merchantUserRepository;

    public MerchantOnboarding(VendorRepository vendorRepository, MerchantUserRepository merchantUserRepository) {
        this.vendorRepository = vendorRepository;
        this.merchantUserRepository = merchantUserRepository;
    }

    /** The vendor created by onboarding and its verification status (always PENDING for a new vendor). */
    public record OnboardedVendor(UUID vendorId, String verificationStatus) {}

    /**
     * Creates the vendor (pending verification, unavailable) and links {@code ownerProfileId} to it as OWNER.
     * {@code contactName} becomes the vendor's contact name.
     */
    public OnboardedVendor createPendingVendorWithOwner(UUID tenantId, String companyName, String contactName,
                                                        String taxNumber, String companyPhone, String address,
                                                        UUID ownerProfileId) {
        UUID vendorId = UUID.randomUUID();
        Vendor vendor = new Vendor(
                vendorId,
                tenantId,
                companyName,
                null,
                null,
                null,
                contactName,
                taxNumber,
                companyPhone,
                address,
                null,
                null
        );
        vendorRepository.save(vendor);
        merchantUserRepository.save(new MerchantUser(UUID.randomUUID(), tenantId, vendorId, ownerProfileId, MerchantRole.OWNER));
        return new OnboardedVendor(vendorId, vendor.getVerificationStatus().name());
    }
}
