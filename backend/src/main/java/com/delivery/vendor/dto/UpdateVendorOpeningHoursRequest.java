package com.delivery.vendor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Request used to replace all vendor opening-hour rules. */
public record UpdateVendorOpeningHoursRequest(@NotEmpty List<@Valid VendorOpeningHourRequest> hours) {}
