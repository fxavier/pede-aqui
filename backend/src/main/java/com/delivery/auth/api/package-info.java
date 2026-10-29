/**
 * Public API of the auth module (ADR-0002): identity (Keycloak user) and user-profile operations used by tenant
 * onboarding/provisioning. Other modules use these instead of the auth repositories/entities/services.
 */
@NamedInterface("api")
package com.delivery.auth.api;

import org.springframework.modulith.NamedInterface;
