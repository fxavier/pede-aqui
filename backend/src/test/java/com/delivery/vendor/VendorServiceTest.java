package com.delivery.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.vendor.dto.CreateVendorRequest;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.entity.VendorVerificationStatus;
import com.delivery.vendor.mapper.VendorMapper;
import com.delivery.vendor.repository.VendorDocumentRepository;
import com.delivery.vendor.repository.VendorOpeningHourRepository;
import com.delivery.vendor.repository.VendorRepository;
import com.delivery.vendor.service.VendorService;
import com.delivery.vendor.service.VendorVerificationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VendorServiceTest {
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void registersVendorWithPendingVerification() {
        VendorRepository repository = mock(VendorRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(repository.save(any(Vendor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VendorService service = new VendorService(repository, mock(VendorDocumentRepository.class), mock(VendorOpeningHourRepository.class), new VendorMapper(), tenantContext);

        var response = service.create(new CreateVendorRequest("Mercado Central", UUID.randomUUID(), -25.9, 32.6));

        assertThat(response.verificationStatus()).isEqualTo(VendorVerificationStatus.PENDING);
        assertThat(response.available()).isFalse();
    }

    @Test
    void approvesVendorVerification() {
        UUID vendorId = UUID.randomUUID();
        VendorRepository repository = mock(VendorRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(repository.findByTenantIdAndId(tenantId, vendorId))
                .thenReturn(Optional.of(new Vendor(vendorId, tenantId, "Loja X", UUID.randomUUID(), null, null)));

        VendorVerificationService service = new VendorVerificationService(repository, new VendorMapper(), tenantContext);

        var response = service.decide(vendorId, true, "Licenca validada");

        assertThat(response.verificationStatus()).isEqualTo(VendorVerificationStatus.APPROVED);
    }

    @Test
    void rejectsVendorVerificationAndForcesUnavailable() {
        UUID vendorId = UUID.randomUUID();
        Vendor vendor = new Vendor(vendorId, tenantId, "Loja Y", UUID.randomUUID(), null, null);
        vendor.setAvailability(true);
        VendorRepository repository = mock(VendorRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(repository.findByTenantIdAndId(tenantId, vendorId)).thenReturn(Optional.of(vendor));

        VendorVerificationService service = new VendorVerificationService(repository, new VendorMapper(), tenantContext);

        var response = service.decide(vendorId, false, "Documento expirado");

        assertThat(response.verificationStatus()).isEqualTo(VendorVerificationStatus.REJECTED);
        assertThat(response.available()).isFalse();
    }

    @Test
    void rejectsDecisionWithoutReason() {
        UUID vendorId = UUID.randomUUID();
        VendorRepository repository = mock(VendorRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));

        VendorVerificationService service = new VendorVerificationService(repository, new VendorMapper(), tenantContext);

        assertThatThrownBy(() -> service.decide(vendorId, false, "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A verification reason is required");
    }
}
