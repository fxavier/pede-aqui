/**
 * Public API of the order module (ADR-0002): entry points other modules may call synchronously (customer PII
 * masking rule shared with the sales lens; the order-side implementations of the payment/delivery hooks are wired
 * by Spring, not called directly) and {@code OrderSettlementFacts} (W6-B, read-only money facts for the settlement
 * ledger), {@code OrderRatingFacts} (LAC-28, the customer's own order facts for ratings). Order events live in the
 * module base package.
 */
@NamedInterface("api")
package com.delivery.order.api;

import org.springframework.modulith.NamedInterface;
