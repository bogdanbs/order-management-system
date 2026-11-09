package com.orders.query.api;

import com.orders.infrastructure.eventstore.EventStoreRepository;
import com.orders.infrastructure.ownership.OrderAccessService;
import com.orders.query.model.OrderSummary;
import com.orders.query.repository.OrderItemViewRepository;
import com.orders.query.repository.OrderSummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderQueryControllerTest {
    private final OrderSummaryRepository summaryRepo = mock(OrderSummaryRepository.class);
    private final OrderQueryController controller = new OrderQueryController(
            summaryRepo, mock(OrderItemViewRepository.class), mock(EventStoreRepository.class),
            mock(OrderAccessService.class));

    @Test
    void findsOrdersForCustomerByName() {
        OrderSummary order = new OrderSummary();
        PageRequest pageable = PageRequest.of(0, 20);
        when(summaryRepo.findByOwnerSubjectAndFirstNameIgnoreCaseAndLastNameIgnoreCase(
                "ada-sub", "Ada", "Lovelace", pageable))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        var authentication = new UsernamePasswordAuthenticationToken(
                "ada-sub", "n/a", List.of(new SimpleGrantedAuthority("SCOPE_orders.read")));
        Page<OrderSummary> result = controller.findByCustomer("Ada", "Lovelace", pageable, authentication);

        assertEquals(List.of(order), result.getContent());
        verify(summaryRepo).findByOwnerSubjectAndFirstNameIgnoreCaseAndLastNameIgnoreCase(
                "ada-sub", "Ada", "Lovelace", pageable);
    }

    @Test
    void directControllerUsesAuthenticatedOwnerForOrderList() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(summaryRepo.findByOwnerSubject("ada-sub", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        var authentication = new UsernamePasswordAuthenticationToken(
                "ada-sub", "n/a", List.of(new SimpleGrantedAuthority("SCOPE_orders.read")));

        controller.list(pageable, authentication);

        verify(summaryRepo).findByOwnerSubject("ada-sub", pageable);
    }
}
