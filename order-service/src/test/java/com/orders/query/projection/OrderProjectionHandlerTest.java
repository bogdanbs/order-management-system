package com.orders.query.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.command.domain.events.OrderItemRemovedEvent;
import com.orders.infrastructure.upcasting.UpcasterChain;
import com.orders.query.model.OrderItemView;
import com.orders.query.model.OrderSummary;
import com.orders.query.repository.OrderItemViewRepository;
import com.orders.query.repository.OrderSummaryRepository;
import com.orders.query.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderProjectionHandlerTest {
    @Test
    void removingOneUnitUpdatesQuantityAndOrderTotal() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderSummary summary = summary(orderId, 1, new BigDecimal("20.00"));
        OrderItemView item = item(orderId, productId, 2);
        OrderItemViewRepository itemRepo = mock(OrderItemViewRepository.class);
        OrderSummaryRepository summaryRepo = mock(OrderSummaryRepository.class);
        OrderProjectionHandler handler = handler(summaryRepo, itemRepo);

        when(summaryRepo.findById(orderId)).thenReturn(Optional.of(summary));
        when(itemRepo.findByOrderId(orderId)).thenReturn(List.of(item));

        handler.handleEvent(removal(orderId, productId, 1));

        assertEquals(1, item.getQuantity());
        assertEquals(new BigDecimal("10.00"), summary.getTotalAmount());
        assertEquals(1, summary.getItemCount());
        verify(itemRepo).save(item);
        verify(itemRepo, never()).delete(item);
        verify(summaryRepo).save(summary);
    }

    @Test
    void removingFinalUnitDeletesTheLineAndDecrementsLineCount() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderSummary summary = summary(orderId, 1, new BigDecimal("10.00"));
        OrderItemView item = item(orderId, productId, 1);
        OrderItemViewRepository itemRepo = mock(OrderItemViewRepository.class);
        OrderSummaryRepository summaryRepo = mock(OrderSummaryRepository.class);
        OrderProjectionHandler handler = handler(summaryRepo, itemRepo);

        when(summaryRepo.findById(orderId)).thenReturn(Optional.of(summary));
        when(itemRepo.findByOrderId(orderId)).thenReturn(List.of(item));

        handler.handleEvent(removal(orderId, productId, 1));

        assertEquals(0, summary.getTotalAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, summary.getItemCount());
        verify(itemRepo).delete(item);
        verify(summaryRepo).save(summary);
    }

    private static OrderProjectionHandler handler(OrderSummaryRepository summaryRepo,
                                                  OrderItemViewRepository itemRepo) {
        return new OrderProjectionHandler(summaryRepo, itemRepo,
                mock(ProcessedEventRepository.class), mock(UpcasterChain.class), new ObjectMapper());
    }

    private static OrderSummary summary(UUID orderId, int itemCount, BigDecimal totalAmount) {
        OrderSummary summary = new OrderSummary();
        summary.setOrderId(orderId);
        summary.setTotalAmount(totalAmount);
        summary.setItemCount(itemCount);
        return summary;
    }

    private static OrderItemView item(UUID orderId, UUID productId, int quantity) {
        OrderItemView item = new OrderItemView();
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal("10.00"));
        return item;
    }

    private static OrderItemRemovedEvent removal(UUID orderId, UUID productId, int quantity) {
        OrderItemRemovedEvent event = new OrderItemRemovedEvent();
        event.setAggregateId(orderId);
        event.setProductId(productId);
        event.setQuantity(quantity);
        return event;
    }
}
