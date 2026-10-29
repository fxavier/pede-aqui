package com.delivery.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.security.TenantContext;
import com.delivery.dashboard.service.DashboardService;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.dispatch.repository.DispatchJobRepository;
import com.delivery.finance.repository.CashReconciliationRepository;
import com.delivery.finance.repository.CommissionRepository;
import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Vendor dashboard "rejected" figure after the seller-rejection unification. */
class DashboardServiceTest {

    @Test
    void vendorRejectedCountSumsSellerRejectedCancellationsAndLegacyRejectedRows() {
        UUID tenantId = UUID.randomUUID();
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.countByTenantIdAndStatusAndCancellationReason(tenantId, OrderStatus.CANCELLED, CancellationReason.SELLER_REJECTED)).thenReturn(3L);
        when(orderRepository.countByTenantIdAndStatus(tenantId, OrderStatus.REJECTED_BY_VENDOR)).thenReturn(2L);
        DashboardService service = new DashboardService(mock(VendorRepository.class), mock(CourierRepository.class), orderRepository,
                mock(DeliveryRepository.class), mock(DispatchJobRepository.class), mock(PaymentRepository.class), mock(CommissionRepository.class),
                mock(CashReconciliationRepository.class), mock(RefundRepository.class), mock(AppUserProfileRepository.class), tenantContext, mock(com.delivery.dispatch.api.CourierEarnings.class));

        assertThat(service.vendor().rejectedOrdersCount()).isEqualTo(5L);
    }
}
