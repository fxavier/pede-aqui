package com.delivery.order.service;

import com.delivery.delivery.api.DeliveryFailureReason;
import com.delivery.order.entity.DeliveryIncident.Amounts;
import com.delivery.order.entity.DeliveryIncident.Decision;
import com.delivery.order.entity.IncidentCostBearer;
import com.delivery.order.entity.IncidentGoodsDisposition;
import com.delivery.order.entity.IncidentOutcome;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Default outcome of a failed delivery by reason (LAC-44, user decision W7-B — OPS has the final word and may override
 * it, audited) and the money arithmetic of a resolution. Built-in table:
 * <pre>
 * reason              cost bearer  outcome                     goods
 * CUSTOMER_ABSENT     CUSTOMER     REFUND_MINUS_DELIVERY_FEE   RETURN_TO_STORE
 * WRONG_ADDRESS       CUSTOMER     REFUND_MINUS_DELIVERY_FEE   RETURN_TO_STORE
 * CUSTOMER_REFUSED    CUSTOMER     REFUND_MINUS_DELIVERY_FEE   RETURN_TO_STORE
 * STORE_FAULT         MERCHANT     REFUND_FULL                 RETURN_TO_STORE
 * DAMAGED_IN_TRANSIT  PLATFORM     REFUND_FULL                 DISCARDED
 * ACCIDENT            PLATFORM     REFUND_FULL                 DISCARDED
 * OTHER               PLATFORM     REFUND_FULL                 RETURN_TO_STORE
 * </pre>
 * Each cell can be replaced per environment: {@code app.delivery-incidents.policy.<REASON>.cost-bearer|outcome|goods-disposition}
 * (unset cells keep the built-in value).
 */
@Component
@ConfigurationProperties(prefix = "app.delivery-incidents")
public class DeliveryIncidentPolicy {
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** The built-in table above. */
    public static final Map<DeliveryFailureReason, Decision> DEFAULTS;

    static {
        Map<DeliveryFailureReason, Decision> table = new EnumMap<>(DeliveryFailureReason.class);
        Decision customer = new Decision(IncidentCostBearer.CUSTOMER, IncidentOutcome.REFUND_MINUS_DELIVERY_FEE,
                IncidentGoodsDisposition.RETURN_TO_STORE);
        table.put(DeliveryFailureReason.CUSTOMER_ABSENT, customer);
        table.put(DeliveryFailureReason.WRONG_ADDRESS, customer);
        table.put(DeliveryFailureReason.CUSTOMER_REFUSED, customer);
        table.put(DeliveryFailureReason.STORE_FAULT, new Decision(IncidentCostBearer.MERCHANT, IncidentOutcome.REFUND_FULL,
                IncidentGoodsDisposition.RETURN_TO_STORE));
        Decision platformLost = new Decision(IncidentCostBearer.PLATFORM, IncidentOutcome.REFUND_FULL, IncidentGoodsDisposition.DISCARDED);
        table.put(DeliveryFailureReason.DAMAGED_IN_TRANSIT, platformLost);
        table.put(DeliveryFailureReason.ACCIDENT, platformLost);
        table.put(DeliveryFailureReason.OTHER, new Decision(IncidentCostBearer.PLATFORM, IncidentOutcome.REFUND_FULL,
                IncidentGoodsDisposition.RETURN_TO_STORE));
        DEFAULTS = Collections.unmodifiableMap(table);
    }

    /** Per-environment overrides of single cells, bound from {@code app.delivery-incidents.policy}. */
    private final Map<DeliveryFailureReason, Rule> policy = new EnumMap<>(DeliveryFailureReason.class);

    /** One configurable row; null fields keep the built-in value. */
    public static class Rule {
        private IncidentCostBearer costBearer;
        private IncidentOutcome outcome;
        private IncidentGoodsDisposition goodsDisposition;

        public IncidentCostBearer getCostBearer() { return costBearer; }
        public void setCostBearer(IncidentCostBearer costBearer) { this.costBearer = costBearer; }
        public IncidentOutcome getOutcome() { return outcome; }
        public void setOutcome(IncidentOutcome outcome) { this.outcome = outcome; }
        public IncidentGoodsDisposition getGoodsDisposition() { return goodsDisposition; }
        public void setGoodsDisposition(IncidentGoodsDisposition goodsDisposition) { this.goodsDisposition = goodsDisposition; }
    }

    public Map<DeliveryFailureReason, Rule> getPolicy() { return policy; }

    /** The default decision for a reason: the configured cells over the built-in row. */
    public Decision defaultFor(DeliveryFailureReason reason) {
        Decision builtIn = DEFAULTS.get(reason);
        Rule rule = policy.get(reason);
        if (rule == null) {
            return builtIn;
        }
        return new Decision(
                rule.getCostBearer() != null ? rule.getCostBearer() : builtIn.costBearer(),
                rule.getOutcome() != null ? rule.getOutcome() : builtIn.outcome(),
                rule.getGoodsDisposition() != null ? rule.getGoodsDisposition() : builtIn.goodsDisposition());
    }

    /**
     * Money figures of a resolution (pure; MZN, scale 2, HALF_UP):
     * <ul>
     *   <li>retained fee = the delivery fee capped at what is still refundable, only for REFUND_MINUS_DELIVERY_FEE
     *       (platform revenue in the ledger);</li>
     *   <li>refund = refundable − retained fee (never negative; 0 → no refund row);</li>
     *   <li>merchant charge (MERCHANT bears it) = the platform fees given back to the customer
     *       ({@code fees − retained}, capped at the refund), debited from the merchant payable;</li>
     *   <li>platform compensation (goods DISCARDED and the merchant is not at fault) = goods value
     *       ({@code subtotal + taxes}), a platform expense credited to the merchant payable.</li>
     * </ul>
     *
     * @param refundable  what can still be refunded on the captured payment (0 when nothing was captured)
     * @param deliveryFee the order's delivery fee (quote snapshot; the order's fees for orders without one)
     * @param fees        delivery + service fee charged on the order
     * @param goodsValue  subtotal + taxes of the order
     */
    public static Amounts amounts(Decision decision, BigDecimal refundable, BigDecimal deliveryFee, BigDecimal fees, BigDecimal goodsValue) {
        BigDecimal available = money(refundable).max(zero());
        BigDecimal retained = decision.outcome() == IncidentOutcome.REFUND_MINUS_DELIVERY_FEE
                ? money(deliveryFee).max(zero()).min(available) : zero();
        BigDecimal refund = available.subtract(retained);
        BigDecimal merchantCharge = decision.costBearer() == IncidentCostBearer.MERCHANT
                ? money(fees).subtract(retained).max(zero()).min(refund) : zero();
        BigDecimal compensation = decision.goodsDisposition() == IncidentGoodsDisposition.DISCARDED
                && decision.costBearer() != IncidentCostBearer.MERCHANT ? money(goodsValue).max(zero()) : zero();
        return new Amounts(refund, retained, merchantCharge, compensation);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, ROUNDING);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    }
}
