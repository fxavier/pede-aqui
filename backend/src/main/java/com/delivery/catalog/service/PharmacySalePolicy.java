package com.delivery.catalog.service;

import com.delivery.catalog.entity.Category;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.repository.CategoryRepository;
import com.delivery.common.exception.BusinessException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Pharmacy sale gate (LAC-64). Until there is a legal/regulatory opinion the pharmacy vertical is blocked by
 * configuration ({@code app.verticals.pharmacy.enabled=false}): a product flagged
 * {@code requires_prescription_metadata} (V003) or whose category belongs to the {@code pharmacy} vertical cannot be
 * added to a cart or checked out (422 {@code pharmacy_not_available}). Even with the vertical enabled, prescription
 * products stay unsellable (422 {@code prescription_required}) because no prescription upload/pharmacist validation
 * flow exists yet.
 */
@Component
public class PharmacySalePolicy {
    public static final String PHARMACY_VERTICAL = "pharmacy";
    public static final String PHARMACY_NOT_AVAILABLE_CODE = "pharmacy_not_available";
    public static final String PRESCRIPTION_REQUIRED_CODE = "prescription_required";

    private final CategoryRepository categoryRepository;
    private final boolean pharmacyEnabled;

    public PharmacySalePolicy(CategoryRepository categoryRepository,
                              @Value("${app.verticals.pharmacy.enabled:false}") boolean pharmacyEnabled) {
        this.categoryRepository = categoryRepository;
        this.pharmacyEnabled = pharmacyEnabled;
    }

    /** Throws 422 when the product may not be sold under the current pharmacy configuration. */
    public void requireSellable(Product product) {
        if (!pharmacyEnabled && (product.isRequiresPrescriptionMetadata() || inPharmacyVertical(product))) {
            throw new BusinessException(PHARMACY_NOT_AVAILABLE_CODE, "Pharmacy products are not available for ordering", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (product.isRequiresPrescriptionMetadata()) {
            throw new BusinessException(PRESCRIPTION_REQUIRED_CODE, "Prescription products cannot be ordered online", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    // Categories are operator-wide (managed by OPS), so the lookup is by id, not by the caller's tenant.
    private boolean inPharmacyVertical(Product product) {
        if (product.getCategoryId() == null) {
            return false;
        }
        return categoryRepository.findById(product.getCategoryId())
                .map(Category::getVertical)
                .map(vertical -> PHARMACY_VERTICAL.equals(vertical.toLowerCase(Locale.ROOT)))
                .orElse(false);
    }
}
