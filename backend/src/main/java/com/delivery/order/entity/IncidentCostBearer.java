package com.delivery.order.entity;

/** Who bears the cost of a failed delivery (LAC-44, user decision W7-B; reflected in the settlement ledger). */
public enum IncidentCostBearer {
    /** Customer fault (absent, wrong address, refused): the delivery fee is retained as platform revenue. */
    CUSTOMER,
    /** Store fault: full refund to the customer; the refunded platform fees are charged to the merchant payable. */
    MERCHANT,
    /** Accident / courier fault / damage in transit: full refund; goods not returned are compensated to the merchant. */
    PLATFORM
}
