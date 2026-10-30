package com.delivery.order.api;

import com.delivery.vendor.api.MerchantScopeGuard;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Customer PII masking rule for order and sales listings: a pure SUPPORT caller sees masked customer names. Owned by
 * the order module (ADR-0002 C5) so the order listings no longer depend on the sales module; the sales lens reuses it
 * through {@code SalesAccessGuard}. Stateless helper over {@link MerchantScopeGuard} (built by its users, not a bean).
 */
public class CustomerPiiMasking {
    private final MerchantScopeGuard merchantScopeGuard;

    public CustomerPiiMasking(MerchantScopeGuard merchantScopeGuard) {
        this.merchantScopeGuard = merchantScopeGuard;
    }

    /** True when customer PII must be masked (SUPPORT without any wider role). */
    public boolean shouldMaskCustomerPii() {
        return hasAuthority("ROLE_SUPPORT")
                && !merchantScopeGuard.hasTenantWideRole()
                && !hasAuthority("ROLE_VENDOR_ADMIN");
    }

    /** Masks a person's name: keeps the first letter of each word ("Ana Lima" -> "A*** L***"). */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        return Arrays.stream(name.trim().split("\\s+"))
                .map(word -> word.charAt(0) + "***")
                .collect(Collectors.joining(" "));
    }

    private static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
    }
}
