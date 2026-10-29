package com.delivery.payment.api;

import com.delivery.payment.repository.PaymentStatusHistoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to an order's payment status history (V058) for other modules (the order history endpoint), so they
 * do not reach into payment's repositories or entities. Statuses are returned as names.
 */
@Component
public class PaymentStatusHistoryReader {
    /** One payment status change; {@code fromStatus} is null for the creation row. */
    public record PaymentStatusChange(UUID paymentId, String fromStatus, String toStatus, String actorType, String actorId,
                                      String reason, String correlationId, Instant createdAt) {}

    private final PaymentStatusHistoryRepository repository;

    public PaymentStatusHistoryReader(PaymentStatusHistoryRepository repository) {
        this.repository = repository;
    }

    /** The order's payment status changes, oldest first (tenant-scoped). */
    @Transactional(readOnly = true)
    public List<PaymentStatusChange> forOrder(UUID tenantId, UUID orderId) {
        return repository.findByTenantIdAndOrderIdOrderByCreatedAtAscIdAsc(tenantId, orderId).stream()
                .map(row -> new PaymentStatusChange(row.getPaymentId(),
                        row.getFromStatus() == null ? null : row.getFromStatus().name(), row.getToStatus().name(),
                        row.getActorType(), row.getActorId(), row.getReason(), row.getCorrelationId(), row.getCreatedAt()))
                .toList();
    }
}
