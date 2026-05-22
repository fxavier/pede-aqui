package com.delivery.finance.mapper;

import com.delivery.finance.dto.CashReconciliationResponse;
import com.delivery.finance.dto.CommissionResponse;
import com.delivery.finance.dto.RefundFinanceResponse;
import com.delivery.finance.dto.TransactionResponse;
import com.delivery.finance.entity.CashReconciliation;
import com.delivery.finance.entity.Commission;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.Refund;
import org.springframework.stereotype.Component;

/** Converts finance and payment entities into finance API DTOs. */
@Component
public class FinanceMapper {
    public TransactionResponse toTransactionResponse(Payment payment) {
        return new TransactionResponse(payment.getId(), payment.getOrderId(), payment.getAmount(), payment.getStatus());
    }

    public CommissionResponse toCommissionResponse(Commission commission) {
        return new CommissionResponse(commission.getId(), commission.getOrderId(), commission.getVendorId(), commission.getCommissionAmount(), commission.getStatus(), commission.getCreatedAt());
    }

    public CashReconciliationResponse toCashReconciliationResponse(CashReconciliation cash) {
        return new CashReconciliationResponse(cash.getId(), cash.getOrderId(), cash.getDeliveryId(), cash.getCourierId(), cash.getAmount(), cash.getStatus(), cash.getRecordedAt());
    }

    public RefundFinanceResponse toRefundFinanceResponse(Refund refund) {
        return new RefundFinanceResponse(refund.getId(), refund.getPaymentId(), refund.getOrderId(), refund.getAmount(), refund.getReason(), refund.getStatus());
    }
}
