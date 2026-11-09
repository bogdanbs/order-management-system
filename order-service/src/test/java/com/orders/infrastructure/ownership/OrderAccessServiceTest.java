package com.orders.infrastructure.ownership;

import com.orders.command.domain.OrderNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderAccessServiceTest {
    private final OrderOwnershipRepository repository = mock(OrderOwnershipRepository.class);
    private final OrderAccessService service = new OrderAccessService(repository);

    @Test
    void ownerCanAccessOwnOrder() {
        UUID orderId = UUID.randomUUID();
        when(repository.findByOrderIdAndOwnerSubject(orderId, "owner-1"))
                .thenReturn(Optional.of(new OrderOwnershipRecord(orderId, "owner-1")));
        var authentication = new UsernamePasswordAuthenticationToken(
                "owner-1", "n/a", List.of(new SimpleGrantedAuthority("SCOPE_orders.read")));

        service.requireAccess(orderId, authentication);

        verify(repository).findByOrderIdAndOwnerSubject(orderId, "owner-1");
    }

    @Test
    void legacyUnownedOrderIsHiddenFromRegularUsers() {
        UUID orderId = UUID.randomUUID();
        when(repository.findByOrderIdAndOwnerSubject(orderId, "owner-1")).thenReturn(Optional.empty());
        var authentication = new UsernamePasswordAuthenticationToken(
                "owner-1", "n/a", List.of(new SimpleGrantedAuthority("SCOPE_orders.read")));

        assertThrows(OrderNotFoundException.class, () -> service.requireAccess(orderId, authentication));
    }

    @Test
    void adminCanAccessLegacyUnownedOrder() {
        UUID orderId = UUID.randomUUID();
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ORDERS_ADMIN")));

        service.requireAccess(orderId, authentication);

        verify(repository, never()).findByOrderIdAndOwnerSubject(orderId, "admin");
    }
}
