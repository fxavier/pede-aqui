/**
 * Password policy constraint (mirrors the Keycloak realm policy). Exposed as a named interface (ADR-0002) because
 * the tenant module's registration request uses it too.
 */
@NamedInterface("validation")
package com.delivery.auth.validation;

import org.springframework.modulith.NamedInterface;
