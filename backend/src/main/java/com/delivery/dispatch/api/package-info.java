/**
 * Public API of the dispatch module (ADR-0002): courier earnings for the dashboard and the courier rating-aggregate
 * write port ({@code CourierRatingAggregates}, LAC-28). Other modules use these instead
 * of the dispatch repositories/entities.
 */
@NamedInterface("api")
package com.delivery.dispatch.api;

import org.springframework.modulith.NamedInterface;
