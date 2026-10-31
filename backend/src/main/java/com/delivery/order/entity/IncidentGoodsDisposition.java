package com.delivery.order.entity;

/**
 * What happens to the goods of a failed delivery (LAC-44). Recorded only: stock is not moved (the reservation was
 * committed at pickup; a merchant restocks returned goods with the normal stock endpoint).
 */
public enum IncidentGoodsDisposition {
    /** The goods go (or stayed) back at the store. */
    RETURN_TO_STORE,
    /** The goods are lost or unusable (damage, accident). */
    DISCARDED
}
