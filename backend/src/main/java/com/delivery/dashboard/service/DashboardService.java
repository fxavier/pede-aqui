package com.delivery.dashboard.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.dashboard.dto.AdminDashboardResponse;
import com.delivery.dashboard.dto.CourierDashboardResponse;
import com.delivery.dashboard.dto.FinanceDashboardResponse;
import com.delivery.dashboard.dto.VendorDashboardResponse;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.dispatch.entity.DispatchJobStatus;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.dispatch.repository.DispatchJobRepository;
import com.delivery.finance.repository.CashReconciliationRepository;
import com.delivery.finance.repository.CommissionRepository;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provides role-specific aggregate metrics for dashboard endpoints. */
@Service
public class DashboardService {
    private final VendorRepository vendorRepository;
    private final CourierRepository courierRepository;
    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final DispatchJobRepository dispatchJobRepository;
    private final PaymentRepository paymentRepository;
    private final CommissionRepository commissionRepository;
    private final CashReconciliationRepository cashReconciliationRepository;
    private final RefundRepository refundRepository;
    private final TenantContext tenantContext;

    public DashboardService(
            VendorRepository vendorRepository,
            CourierRepository courierRepository,
            OrderRepository orderRepository,
            DeliveryRepository deliveryRepository,
            DispatchJobRepository dispatchJobRepository,
            PaymentRepository paymentRepository,
            CommissionRepository commissionRepository,
            CashReconciliationRepository cashReconciliationRepository,
            RefundRepository refundRepository,
            TenantContext tenantContext) {
        this.vendorRepository = vendorRepository;
        this.courierRepository = courierRepository;
        this.orderRepository = orderRepository;
        this.deliveryRepository = deliveryRepository;
        this.dispatchJobRepository = dispatchJobRepository;
        this.paymentRepository = paymentRepository;
        this.commissionRepository = commissionRepository;
        this.cashReconciliationRepository = cashReconciliationRepository;
        this.refundRepository = refundRepository;
        this.tenantContext = tenantContext;
    }

    /** Returns top-level operations metrics for admins and operations users. */
    @Transactional(readOnly = true)
    public AdminDashboardResponse admin() {
        UUID tenantId = tenantId();
        return new AdminDashboardResponse(
                vendorRepository.countByTenantIdAndAvailable(tenantId, true),
                courierRepository.countByTenantIdAndAvailable(tenantId, true),
                orderRepository.findByTenantIdAndStatus(tenantId, OrderStatus.CANCELLED).size(),
                deliveryRepository.findByTenantIdAndStatus(tenantId, DeliveryStatus.FAILED_DELIVERY).size());
    }

    /** Returns vendor sales and order-state metrics for the current tenant. */
    @Transactional(readOnly = true)
    public VendorDashboardResponse vendor() {
        UUID tenantId = tenantId();
        long rejected = orderRepository.findByTenantIdAndStatus(tenantId, OrderStatus.REJECTED_BY_VENDOR).size();
        long accepted = orderRepository.findByTenantIdAndStatus(tenantId, OrderStatus.ACCEPTED_BY_VENDOR).size();
        BigDecimal salesTotal = orderRepository.findByTenantIdAndStatus(tenantId, OrderStatus.DELIVERED).stream()
                .map(order -> order.getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new VendorDashboardResponse(accepted, rejected, salesTotal);
    }

    /** Returns courier completion and assignment metrics for the current tenant. */
    @Transactional(readOnly = true)
    public CourierDashboardResponse courier() {
        UUID tenantId = tenantId();
        return new CourierDashboardResponse(
                deliveryRepository.findByTenantIdAndStatus(tenantId, DeliveryStatus.DELIVERED).size(),
                deliveryRepository.findByTenantIdAndStatus(tenantId, DeliveryStatus.FAILED_DELIVERY).size(),
                dispatchJobRepository.countByTenantIdAndStatus(tenantId, DispatchJobStatus.ASSIGNED));
    }

    /** Returns finance aggregates aligned to payment, refund, and reconciliation data. */
    @Transactional(readOnly = true)
    public FinanceDashboardResponse finance() {
        UUID tenantId = tenantId();
        BigDecimal transactionsTotal = paymentRepository.findByTenantIdAndStatus(tenantId, PaymentStatus.CONFIRMED).stream()
                .map(payment -> payment.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal commissionsTotal = commissionRepository.findByTenantId(tenantId).stream()
                .map(commission -> commission.getCommissionAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundsTotal = refundRepository.findByTenantIdAndStatus(tenantId, RefundStatus.REFUNDED).stream()
                .map(refund -> refund.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unreconciledCashTotal = cashReconciliationRepository.findByTenantIdAndStatus(tenantId, "PENDING").stream()
                .map(item -> item.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanceDashboardResponse(transactionsTotal, commissionsTotal, refundsTotal, unreconciledCashTotal);
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
