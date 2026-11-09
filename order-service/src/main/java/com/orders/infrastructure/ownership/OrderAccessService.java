package com.orders.infrastructure.ownership;

import com.orders.command.domain.OrderNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderAccessService {
    private final OrderOwnershipRepository ownershipRepository;

    public OrderAccessService(OrderOwnershipRepository ownershipRepository) {
        this.ownershipRepository = ownershipRepository;
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_ORDERS_ADMIN")
                        || authority.getAuthority().equals("SCOPE_admin"));
    }

    public void requireAccess(UUID orderId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }
        ownershipRepository.findByOrderIdAndOwnerSubject(orderId, authentication.getName())
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
