package com.delivery.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Registers (or refreshes) the caller's push token; {@code platform} = ANDROID, IOS or WEB. */
public record RegisterDeviceRequest(@NotBlank @Pattern(regexp = "ANDROID|IOS|WEB") String platform,
                                    @NotBlank @Size(max = 4096) String token) {}
