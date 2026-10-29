package com.delivery.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Batch of GPS fixes (1..50, buffered offline points included) posted by the courier app (LAC-40). */
public record CourierLocationBatchRequest(@NotNull @Size(min = 1, max = 50) List<@Valid @NotNull CourierLocationPointRequest> points) {}
