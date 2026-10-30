package com.delivery.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.common.entity.AuditLog;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.repository.AuditLogRepository;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.controller.DeliveryController;
import com.delivery.delivery.dto.CompleteDeliveryRequest;
import com.delivery.delivery.dto.UpdateDeliveryStatusRequest;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.service.DeliveryService;
import com.delivery.order.controller.CheckoutController;
import com.delivery.order.dto.CheckoutRequest;
import com.delivery.order.dto.CheckoutResult;
import com.delivery.order.service.OrderService;
import com.delivery.upload.dto.CreateUploadUrlRequest;
import com.delivery.upload.service.UploadLimits;
import com.delivery.upload.service.UploadService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * LAC-50 business metrics: names, the fixed tag keys of every series (Prometheus requires them to be stable) and
 * low-cardinality values (result / stable error code / small enums — no ids), plus the after-commit audit counter.
 */
class BusinessMetricsTest {
    private SimpleMeterRegistry registry;
    private BusinessMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new BusinessMetrics(registry);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void checkoutCountsCreatedReplayedAndRejectedWithStableCode() {
        OrderService orderService = mock(OrderService.class);
        CheckoutController controller = new CheckoutController(orderService);
        controller.setBusinessMetrics(metrics);
        UUID cartId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        when(orderService.checkout(cartId, "k1", addressId, null)).thenReturn(new CheckoutResult(null, false));
        when(orderService.checkout(cartId, "k2", addressId, null)).thenReturn(new CheckoutResult(null, true));
        when(orderService.checkout(cartId, "k3", addressId, null))
                .thenThrow(new BusinessException("insufficient_stock", "No stock", HttpStatus.CONFLICT));
        when(orderService.checkout(cartId, "k4", addressId, null)).thenThrow(new IllegalStateException("boom"));

        controller.checkout(new CheckoutRequest(cartId, "k1", addressId));
        controller.checkout(new CheckoutRequest(cartId, "k2", addressId));
        assertThatThrownBy(() -> controller.checkout(new CheckoutRequest(cartId, "k3", addressId))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.checkout(new CheckoutRequest(cartId, "k4", addressId))).isInstanceOf(IllegalStateException.class);

        assertThat(count("orders.checkout", "result", "created", "reason", "none")).isEqualTo(1);
        assertThat(count("orders.checkout", "result", "replayed", "reason", "none")).isEqualTo(1);
        assertThat(count("orders.checkout", "result", "rejected", "reason", "insufficient_stock")).isEqualTo(1);
        assertThat(count("orders.checkout", "result", "error", "reason", "internal_error")).isEqualTo(1);
        assertThat(tagKeys("orders.checkout")).containsExactly(Set.of("result", "reason"));
    }

    @Test
    void deliveryCompletionAndStatusUpdatesCountOutcomesWithoutIds() {
        DeliveryService deliveryService = mock(DeliveryService.class);
        DeliveryController controller = new DeliveryController(deliveryService);
        controller.setBusinessMetrics(metrics);
        UUID ok = UUID.randomUUID();
        UUID wrongPin = UUID.randomUUID();
        when(deliveryService.complete(ok, "123456")).thenReturn(null);
        when(deliveryService.complete(wrongPin, "000000"))
                .thenThrow(new BusinessException("invalid_delivery_code", "bad", HttpStatus.BAD_REQUEST));
        when(deliveryService.updateStatus(any(), any(), any(), any())).thenReturn(null);

        controller.complete(ok, new CompleteDeliveryRequest("123456"));
        assertThatThrownBy(() -> controller.complete(wrongPin, new CompleteDeliveryRequest("000000"))).isInstanceOf(BusinessException.class);
        controller.updateStatus(ok, new UpdateDeliveryStatusRequest(DeliveryStatus.FAILED_DELIVERY, null, null));

        assertThat(count("deliveries.completion", "result", "success", "reason", "none")).isEqualTo(1);
        assertThat(count("deliveries.completion", "result", "rejected", "reason", "invalid_delivery_code")).isEqualTo(1);
        assertThat(count("deliveries.status.updates", "status", "FAILED_DELIVERY", "result", "success", "reason", "none")).isEqualTo(1);
        assertThat(allTagValues()).noneMatch(value -> value.contains(ok.toString()) || value.contains(wrongPin.toString()));
    }

    @Test
    void uploadRejectionsAreCountedByStageKindAndReason() {
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(UUID.randomUUID()));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of("user-1"));
        UploadService uploadService = new UploadService(mock(S3Presigner.class), tenantContext,
                new UploadLimits(5_242_880, 10_485_760), "bucket", 900);
        uploadService.setBusinessMetrics(metrics);

        assertThatThrownBy(() -> uploadService.createImageUploadUrl(
                new CreateUploadUrlRequest("image/png", "logo", "logo", 50_000_000L))).isInstanceOf(BusinessException.class);

        assertThat(count("uploads.rejected", "stage", "presign", "kind", "image", "reason", "upload_too_large")).isEqualTo(1);
    }

    @Test
    void auditEventsAreCountedOnlyAfterCommit() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(UUID.randomUUID()));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of("finance-user"));
        AuditLogService auditLogService = new AuditLogService(repository, tenantContext);
        auditLogService.setBusinessMetrics(metrics);

        TransactionSynchronizationManager.initSynchronization();
        auditLogService.log("REFUND_APPROVED", "refund", UUID.randomUUID().toString(), "order", "SUCCESS");
        assertThat(count("audit.events", "action", "REFUND_APPROVED", "result", "SUCCESS")).isZero();

        TransactionSynchronizationUtils.triggerAfterCommit();
        assertThat(count("audit.events", "action", "REFUND_APPROVED", "result", "SUCCESS")).isEqualTo(1);
    }

    @Test
    void auditEventOutsideTransactionIsCountedImmediatelyAndUnsafeValuesAreBucketed() {
        metrics.incrementAfterCommit("audit.events", "action", BusinessMetrics.safeAction("PAYMENT_CONFIRMED"),
                "result", BusinessMetrics.safeAction("attempts=3"));
        assertThat(count("audit.events", "action", "PAYMENT_CONFIRMED", "result", "OTHER")).isEqualTo(1);
    }

    @Test
    void failureClassificationAndReasonsStayBounded() {
        assertThat(BusinessMetrics.failureResult(new AccessDeniedException("no"))).isEqualTo("rejected");
        assertThat(BusinessMetrics.reason(new AccessDeniedException("no"))).isEqualTo("forbidden");
        assertThat(BusinessMetrics.failureResult(new BusinessException("storage_unavailable", "x", HttpStatus.SERVICE_UNAVAILABLE)))
                .isEqualTo("error");
        assertThat(BusinessMetrics.safeCode("Weird Code " + UUID.randomUUID())).isEqualTo("other");
        assertThat(BusinessMetrics.safeCode(null)).isEqualTo("other");
    }

    private double count(String name, String... tags) {
        Counter counter = registry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private List<Set<String>> tagKeys(String name) {
        return registry.find(name).meters().stream()
                .map(meter -> meter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet()))
                .distinct().toList();
    }

    private List<String> allTagValues() {
        return registry.getMeters().stream().map(Meter::getId)
                .flatMap(id -> id.getTags().stream()).map(Tag::getValue).toList();
    }
}
