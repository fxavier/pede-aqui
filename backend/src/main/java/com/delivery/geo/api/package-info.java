/**
 * Public API of the geo module (ADR-0002): the delivery-fee estimate that vendor search needs, as an interface
 * implemented by the single pricing engine in the cart module (so geo does not depend on cart), and the zone
 * service-area check ({@link com.delivery.geo.api.ServiceArea}, LAC-39) that checkout v2 will call.
 */
@NamedInterface("api")
package com.delivery.geo.api;

import org.springframework.modulith.NamedInterface;
