package com.delivery.dashboard.service;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.dashboard.dto.AdminDashboardResponse;
import com.delivery.dashboard.dto.CourierDashboardResponse;
import com.delivery.dashboard.dto.FinanceDashboardResponse;
import com.delivery.dashboard.dto.VendorDashboardResponse;
import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.dispatch.entity.Courier;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final AppUserProfileRepository userProfileRepository;
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
            AppUserProfileRepository userProfileRepository,
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
        this.userProfileRepository = userProfileRepository;
        this.tenantContext = tenantContext;
    }

    /** Returns top-level operations metrics for admins and operations users. */
    @Transactional(readOnly = true)
    public AdminDashboardResponse admin() {
        UUID tenantId = tenantId();
        
        var allOrders = orderRepository.findByTenantId(tenantId);
        long totalOrders = allOrders.size();
        
        BigDecimal totalRevenue = orderRepository.findByTenantIdAndStatus(tenantId, OrderStatus.DELIVERED).stream()
                .map(order -> order.getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Map<String, Long> ordersByStatus = allOrders.stream()
                .collect(Collectors.groupingBy(
                    order -> order.getStatus().name(),
                    Collectors.counting()
                ));
        
        // Ensure all OrderStatus enum values are present with default 0L
        for (OrderStatus status : OrderStatus.values()) {
            ordersByStatus.putIfAbsent(status.name(), 0L);
        }
        
        long activeVendors = vendorRepository.countByTenantIdAndAvailable(tenantId, true);
        long activeCouriers = courierRepository.countByTenantIdAndAvailable(tenantId, true);
        long cancellations = orderRepository.findByTenantIdAndStatus(tenantId, OrderStatus.CANCELLED).size();
        long failedDeliveries = deliveryRepository.findByTenantIdAndStatus(tenantId, DeliveryStatus.FAILED_DELIVERY).size();
        
        return new AdminDashboardResponse(
                totalOrders,
                totalRevenue,
                ordersByStatus,
                activeVendors,
                activeCouriers,
                cancellations,
                failedDeliveries);
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

    /** Returns courier completion and assignment metrics for the authenticated courier. */
    @Transactional(readOnly = true)
    public CourierDashboardResponse courier() {
        Courier courier = requireMine();
        UUID tenantId = courier.getTenantId();
        UUID courierId = courier.getId();
        
        long completedDeliveries = deliveryRepository.findByTenantIdAndCourierIdAndStatus(tenantId, courierId, DeliveryStatus.DELIVERED).size();
        long failedDeliveries = deliveryRepository.findByTenantIdAndCourierIdAndStatus(tenantId, courierId, DeliveryStatus.FAILED_DELIVERY).size();
        long activeAssignments = dispatchJobRepository.countByTenantIdAndCourierIdAndStatus(tenantId, courierId, DispatchJobStatus.ASSIGNED);
        
        BigDecimal earningsTotal = calculateCourierEarnings(tenantId, courierId);
        
        return new CourierDashboardResponse(completedDeliveries, failedDeliveries, activeAssignments, earningsTotal);
    }

    /** Calculates total earnings for a courier based on delivered order totals. */
    @Transactional(readOnly = true)
    public BigDecimal calculateCourierEarnings(UUID tenantId, UUID courierId) {
        return deliveryRepository.findByTenantIdAndCourierIdAndStatus(tenantId, courierId, DeliveryStatus.DELIVERED).stream()
                .map(Delivery::getOrderId)
                .map(orderId -> orderRepository.findByTenantIdAndId(tenantId, orderId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(order -> order.getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
    
    /** Returns the tenant-scoped courier record of the authenticated user. */
    private Courier requireMine() {
        UUID tenantId = tenantId();
        String userId = tenantContext.currentKeycloakUserId().orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        UUID profileId = userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, userId)
                .orElseThrow(() -> new NotFoundException("User profile was not found"))
                .getId();
        return courierRepository.findByTenantIdAndUserProfileId(tenantId, profileId)
                .orElseThrow(() -> new NotFoundException("Courier profile was not found"));
    }
}
