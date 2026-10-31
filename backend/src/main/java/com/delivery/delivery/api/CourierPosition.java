package com.delivery.delivery.api;

import java.time.Instant;

/**
 * Last position received from a courier (W5-B), recorded as arrival evidence on a delivery incident (LAC-44). Never
 * logged. {@code accuracyMeters} may be null; {@code recordedAt} is the device instant of the fix.
 */
public record CourierPosition(double latitude, double longitude, Double accuracyMeters, Instant recordedAt) {
}
