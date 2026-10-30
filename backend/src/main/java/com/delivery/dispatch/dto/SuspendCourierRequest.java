package com.delivery.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** OPS/ADMIN suspension of a courier with a mandatory reason (stored on the courier, not in logs or audit text). */
public record SuspendCourierRequest(@NotBlank @Size(max = 500) String reason) {}
