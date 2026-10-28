package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.customer.entity.CustomerAddress;
import com.delivery.customer.repository.CustomerAddressRepository;
import com.delivery.order.entity.DeliveryAddressSnapshot;
import com.delivery.order.service.DeliveryAddressResolver;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** INC-12: checkout address resolution — ownership, snapshot content and the legacy no-address window. */
class DeliveryAddressResolverTest {
    private final CustomerAddressRepository repository = mock(CustomerAddressRepository.class);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @Test
    void missingAddressIsAcceptedOnlyWhileTheLegacyWindowIsOpen() {
        assertThat(new DeliveryAddressResolver(repository, true).resolve(tenantId, customerId, null)).isEmpty();

        BusinessException closed = catchThrowableOfType(() -> new DeliveryAddressResolver(repository, false).resolve(tenantId, customerId, null), BusinessException.class);
        assertThat(closed.getCode()).isEqualTo("address_required");
        assertThat(closed.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(repository);
    }

    @Test
    void addressOfAnotherCustomerOrTenantIsNotFound() {
        UUID addressId = UUID.randomUUID();
        when(repository.findByTenantIdAndCustomerIdAndId(tenantId, customerId, addressId)).thenReturn(Optional.empty());

        assertThat(catchThrowableOfType(() -> new DeliveryAddressResolver(repository, true).resolve(tenantId, customerId, addressId), NotFoundException.class))
                .isNotNull();
    }

    @Test
    void snapshotCopiesTextCoordinatesAndInstructions() {
        CustomerAddress address = new CustomerAddress(UUID.randomUUID(), tenantId, customerId);
        address.update("Casa", "Ana", "+258840000000", "Av. Julius Nyerere 1200", "Polana", "Maputo", null, null, -25.9692, 32.5732, "Portão verde");
        when(repository.findByTenantIdAndCustomerIdAndId(tenantId, customerId, address.getId())).thenReturn(Optional.of(address));

        DeliveryAddressSnapshot snapshot = new DeliveryAddressResolver(repository, false).resolve(tenantId, customerId, address.getId()).orElseThrow();

        assertThat(snapshot.getSourceAddressId()).isEqualTo(address.getId());
        assertThat(snapshot.getLine1()).isEqualTo("Av. Julius Nyerere 1200");
        assertThat(snapshot.getLine2()).isEqualTo("Polana");
        assertThat(snapshot.getCity()).isEqualTo("Maputo");
        assertThat(snapshot.getRecipientName()).isEqualTo("Ana");
        assertThat(snapshot.getPhone()).isEqualTo("+258840000000");
        assertThat(snapshot.getLatitude()).isEqualTo(-25.9692);
        assertThat(snapshot.getLongitude()).isEqualTo(32.5732);
        assertThat(snapshot.getInstructions()).isEqualTo("Portão verde");
    }
}
