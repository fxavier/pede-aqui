/**
 * Public API of the vendor module (ADR-0002): merchant scope guard, store availability (LAC-27) and its exception,
 * merchant onboarding, the vendor-name lookup and the rating-aggregate write port ({@code VendorRatingAggregates}, LAC-28).
 * Other modules use these instead of the vendor repositories/entities.
 */
@NamedInterface("api")
package com.delivery.vendor.api;

import org.springframework.modulith.NamedInterface;
