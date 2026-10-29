package com.delivery.dispatch.entity;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Courier vehicle (LAC-42). Requests may send the enum name or a common pt/en spelling ("Mota", "Motorcycle",
 * "Bicicleta", "Carro", "A pé"); the same prefix rules mapped the pre-V060 free text in the migration
 * (unknown legacy values became {@link #OTHER} there, while the API rejects them).
 */
public enum CourierVehicleType {
    MOTORBIKE,
    BICYCLE,
    CAR,
    ON_FOOT,
    OTHER;

    private static final Pattern MOTORBIKE_ALIAS = Pattern.compile("^(mot|scooter).*");
    private static final Pattern BICYCLE_ALIAS = Pattern.compile("^(bic|bike|cicl).*");
    private static final Pattern CAR_ALIAS = Pattern.compile("^(car|auto|viat).*");
    private static final Pattern ON_FOOT_ALIAS = Pattern.compile("^(a\\s*p[eé]|on[\\s_-]*foot|walk|pedestre|p[eé]$).*");

    /**
     * Parses an enum name or a known alias. Blank → {@code null} (no vehicle); unknown text →
     * {@link IllegalArgumentException}.
     */
    public static CourierVehicleType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        for (CourierVehicleType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (MOTORBIKE_ALIAS.matcher(lower).matches()) return MOTORBIKE;
        if (BICYCLE_ALIAS.matcher(lower).matches()) return BICYCLE;
        if (CAR_ALIAS.matcher(lower).matches()) return CAR;
        if (ON_FOOT_ALIAS.matcher(lower).matches()) return ON_FOOT;
        throw new IllegalArgumentException("Unknown vehicle type");
    }
}
