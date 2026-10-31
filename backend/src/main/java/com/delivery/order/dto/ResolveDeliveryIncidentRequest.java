package com.delivery.order.dto;

import com.delivery.order.entity.IncidentCostBearer;
import com.delivery.order.entity.IncidentGoodsDisposition;
import com.delivery.order.entity.IncidentOutcome;
import jakarta.validation.constraints.Size;

/**
 * OPS/ADMIN resolution of a delivery incident (LAC-44). Every field is optional: an omitted field keeps the policy
 * default. Any field that differs from the default is an override and needs {@code overrideReason} (400
 * {@code override_reason_required}); the reason is kept on the incident (not in the audit log).
 */
public record ResolveDeliveryIncidentRequest(IncidentCostBearer costBearer, IncidentOutcome outcome,
                                             IncidentGoodsDisposition goodsDisposition,
                                             @Size(max = 500) String overrideReason) {
}
