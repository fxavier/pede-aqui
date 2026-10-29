/**
 * Public API of the tenant module (ADR-0002): read-only tenant counts for the platform dashboard. Other modules use
 * these instead of the tenant repositories/entities.
 */
@NamedInterface("api")
package com.delivery.tenant.api;

import org.springframework.modulith.NamedInterface;
