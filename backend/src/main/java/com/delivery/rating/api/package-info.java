/**
 * Public API of the rating module (ADR-0002): {@code CustomerRatingPrivacy} (W7-C/W7-D, the rating side of the
 * account erasure, called by the privacy module). Rating events live in the module base package.
 */
@NamedInterface("api")
package com.delivery.rating.api;

import org.springframework.modulith.NamedInterface;
