package com.orders.query.api;

import com.orders.infrastructure.eventstore.EventStoreRepository;
import com.orders.query.model.OrderSummary;
import com.orders.query.repository.OrderItemViewRepository;
import com.orders.query.repository.OrderSummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderQueryControllerTest {
    private final OrderSummaryRepository summaryRepo = mock(OrderSummaryRepository.class);
    private final OrderQueryController controller = new OrderQueryController(
            summaryRepo, mock(OrderItemViewRepository.class), mock(EventStoreRepository.class));

    @Test
    void findsOrdersForCustomerByName() {
        OrderSummary order = new OrderSummary();
        PageRequest pageable = PageRequest.of(0, 20);
        when(summaryRepo.findByFirstNameIgnoreCaseAndLastNameIgnoreCase("Ada", "Lovelace", pageable))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        Page<OrderSummary> result = controller.findByCustomer("Ada", "Lovelace", pageable);

        assertEquals(List.of(order), result.getContent());
        verify(summaryRepo).findByFirstNameIgnoreCaseAndLastNameIgnoreCase("Ada", "Lovelace", pageable);
    }
}
