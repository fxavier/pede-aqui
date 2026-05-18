package com.delivery.payment.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.order.entity.Order;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.dto.PaymentResponse;
import com.delivery.payment.dto.RefundRequest;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.Refund;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.mapper.PaymentMapper;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles local/mock payment confirmation with idempotency. */
@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final AuditLogService auditLogService;
    private final PaymentMapper mapper;
    private final TenantContext tenantContext;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            RefundRepository refundRepository,
            AuditLogService auditLogService,
            PaymentMapper mapper,
            TenantContext tenantContext) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.auditLogService = auditLogService;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }

    /** Confirms a pending mock payment once and returns the same result on duplicate calls. */
    @Transactional
    public PaymentResponse confirm(UUID paymentId, String idempotencyKey) {
        UUID tenantId = tenantId();
        Payment payment = paymentRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                .orElseGet(() -> paymentRepository.findByTenantIdAndId(tenantId, paymentId).orElseThrow(() -> new NotFoundException("Payment was not found")));
        if (!payment.getId().equals(paymentId)) {
            throw new BusinessException("idempotency_conflict", "Idempotency key belongs to another payment", HttpStatus.CONFLICT);
        }
        if (payment.getStatus().name().equals("PENDING_CONFIRMATION")) {
            payment.confirm();
            Order order = orderRepository.findByTenantIdAndId(tenantId, payment.getOrderId()).orElseThrow(() -> new NotFoundException("Order was not found"));
            order.markPaymentConfirmed();
        }
        return mapper.toResponse(payment);
    }

    /** Creates a refund request idempotently for finance/admin review. */
    @Transactional
    public RefundResponse requestRefund(UUID paymentId, RefundRequest request) {
        UUID tenantId = tenantId();
        Payment payment = paymentRepository.findByTenantIdAndId(tenantId, paymentId).orElseThrow(() -> new NotFoundException("Payment was not found"));
        Refund refund = refundRepository.findByTenantIdAndIdempotencyKey(tenantId, request.idempotencyKey())
                .orElseGet(() -> refundRepository.save(new Refund(UUID.randomUUID(), tenantId, paymentId, payment.getOrderId(), request.amount(), request.reason(), RefundStatus.REQUESTED, request.idempotencyKey())));
        if (!refund.getPaymentId().equals(paymentId)) {
            throw new BusinessException("idempotency_conflict", "Idempotency key belongs to another refund", HttpStatus.CONFLICT);
        }
        return toRefundResponse(refund);
    }

    /** Approves a pending refund and marks it refunded once. */
    @Transactional
    public RefundResponse approveRefund(UUID refundId) {
        Refund refund = refundRepository.findById(refundId).orElseThrow(() -> new NotFoundException("Refund was not found"));
        if (refund.getStatus() == RefundStatus.REJECTED) {
            throw new BusinessException("refund_rejected", "Rejected refunds cannot be approved", HttpStatus.CONFLICT);
        }
        if (refund.getStatus() != RefundStatus.REFUNDED) {
            refund.approve();
            auditLogService.log("REFUND_APPROVED", "refund", refund.getId().toString(), refund.getOrderId().toString(), "SUCCESS");
        }
        return toRefundResponse(refund);
    }

    /** Rejects a pending refund once and audits the decision. */
    @Transactional
    public RefundResponse rejectRefund(UUID refundId) {
        Refund refund = refundRepository.findById(refundId).orElseThrow(() -> new NotFoundException("Refund was not found"));
        if (refund.getStatus() == RefundStatus.REFUNDED) {
            throw new BusinessException("refund_completed", "Refund already completed", HttpStatus.CONFLICT);
        }
        if (refund.getStatus() != RefundStatus.REJECTED) {
            refund.reject();
            auditLogService.log("REFUND_REJECTED", "refund", refund.getId().toString(), refund.getOrderId().toString(), "SUCCESS");
        }
        return toRefundResponse(refund);
    }

    private RefundResponse toRefundResponse(Refund refund) {
        return new RefundResponse(refund.getId(), refund.getPaymentId(), refund.getOrderId(), refund.getAmount(), refund.getReason(), refund.getStatus());
    }

    private UUID tenantId() { return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN)); }
}
