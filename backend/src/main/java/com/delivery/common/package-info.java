/**
 * Shared kernel of the backend (config, security, exceptions, audit, pricing value types, jobs). Declared an OPEN
 * Spring Modulith application module (ADR-0002, decision 1): every other module may use any of its types.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.delivery.common;

import org.springframework.modulith.ApplicationModule;
