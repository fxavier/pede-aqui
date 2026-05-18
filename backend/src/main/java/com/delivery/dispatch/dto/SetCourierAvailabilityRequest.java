package com.delivery.dispatch.dto;

/** Request used by couriers to change online availability. */
public record SetCourierAvailabilityRequest(boolean available) {}
