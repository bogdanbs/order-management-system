package com.orders.query.api;

import com.orders.infrastructure.eventstore.EventStoreRepository;
import com.orders.query.model.OrderSummary;
import com.orders.query.repository.OrderItemViewRepository;
import com.orders.query.repository.OrderSummaryRepository;
import org.junit.jupiter.api.Test;

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
        when(summaryRepo.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseOrderByCreatedAtDesc("Ada", "Lovelace"))
                .thenReturn(List.of(order));

        List<OrderSummary> result = controller.findByCustomer("Ada", "Lovelace");

        assertEquals(List.of(order), result);
        verify(summaryRepo).findByFirstNameIgnoreCaseAndLastNameIgnoreCaseOrderByCreatedAtDesc("Ada", "Lovelace");
    }
}
