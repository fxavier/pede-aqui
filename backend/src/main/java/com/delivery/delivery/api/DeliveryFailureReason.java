package com.delivery.delivery.api;

/**
 * Coded reason a courier (or OPS/ADMIN) gives when a delivery ends in FAILED_DELIVERY (LAC-44, W7-B). Required on that
 * transition; it selects the default incident outcome (who bears the cost) in the order module's incident policy.
 */
public enum DeliveryFailureReason {
    /** Nobody at the address to receive the order. */
    CUSTOMER_ABSENT,
    /** The address is wrong or cannot be found. */
    WRONG_ADDRESS,
    /** The customer refused to receive the order. */
    CUSTOMER_REFUSED,
    /** Store fault: wrong/missing items, or store closed at pickup (the only reason accepted before pickup). */
    STORE_FAULT,
    /** Goods damaged while being carried. */
    DAMAGED_IN_TRANSIT,
    /** Accident on the way. */
    ACCIDENT,
    /** Anything else; OPS decides. */
    OTHER
}
