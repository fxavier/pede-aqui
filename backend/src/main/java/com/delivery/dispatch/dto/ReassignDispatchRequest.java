package com.delivery.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request used by operations users to reassign a dispatch job in a target zone. */
public record ReassignDispatchRequest(@NotNull UUID operatingZoneId) {}
