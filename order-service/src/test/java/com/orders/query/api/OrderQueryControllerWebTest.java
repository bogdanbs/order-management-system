package com.orders.query.api;

import com.orders.api.error.GlobalExceptionHandler;
import com.orders.infrastructure.config.SecurityConfig;
import com.orders.infrastructure.eventstore.EventRecord;
import com.orders.infrastructure.eventstore.EventStoreRepository;
import com.orders.query.model.OrderSummary;
import com.orders.query.repository.OrderItemViewRepository;
import com.orders.query.repository.OrderSummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderQueryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class OrderQueryControllerWebTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderSummaryRepository summaryRepo;

    @MockitoBean
    private OrderItemViewRepository itemRepo;

    @MockitoBean
    private EventStoreRepository eventStoreRepo;

    @Test
    void listReturnsPagedOrders() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderSummary order = new OrderSummary();
        order.setOrderId(orderId);
        order.setFirstName("Ada");
        order.setLastName("Lovelace");

        when(summaryRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));

        mockMvc.perform(get("/api/orders?page=0&size=10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].firstName").value("Ada"));
    }

    @Test
    void requestedPageSizeIsCappedAtOneHundred() throws Exception {
        mockMvc.perform(get("/api/orders?size=500")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders"))))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Pageable> pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(summaryRepo).findAll(pageable.capture());
        assertEquals(100, pageable.getValue().getPageSize());
    }

    @Test
    void returnsCustomerOrdersAsJsonWhenAuthenticated() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderSummary order = new OrderSummary();
        order.setOrderId(orderId);
        order.setFirstName("Ada");
        order.setLastName("Lovelace");
        order.setStatus("CREATED");
        order.setTotalAmount(new BigDecimal("25.50"));

        when(summaryRepo.findByFirstNameIgnoreCaseAndLastNameIgnoreCase(
                eq("ada"), eq("lovelace"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        mockMvc.perform(get("/api/orders/customer")
                        .param("firstName", "ada")
                        .param("lastName", "lovelace")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].firstName").value("Ada"))
                .andExpect(jsonPath("$.content[0].lastName").value("Lovelace"))
                .andExpect(jsonPath("$.content[0].totalAmount").value(25.50));
    }

    @Test
    void customerEndpointFailsWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/orders/customer")
                        .param("firstName", "ada")
                        .param("lastName", "lovelace"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresBothCustomerNameParameters() throws Exception {
        mockMvc.perform(get("/api/orders/customer")
                        .param("firstName", "Ada")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404ProblemDetailWhenOrderNotFoundById() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(summaryRepo.findById(orderId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void eventsReturnsEventResponseDtos() throws Exception {
        UUID orderId = UUID.randomUUID();
        EventRecord record = new EventRecord();
        record.setId(UUID.randomUUID());
        record.setAggregateId(orderId);
        record.setAggregateType("Order");
        record.setSequenceNumber(1L);
        record.setEventType("OrderCreated");
        record.setEventVersion(2);
        record.setPayload("{}");
        record.setOccurredAt(Instant.now());

        when(eventStoreRepo.existsByAggregateId(orderId)).thenReturn(true);
        when(eventStoreRepo.findByAggregateIdOrderBySequenceNumberAsc(eq(orderId), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(record)));

        mockMvc.perform(get("/api/orders/{id}/events?page=0&size=10", orderId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].aggregateId").value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].eventType").value("OrderCreated"))
                .andExpect(jsonPath("$.content[0].eventVersion").value(2));
    }

    @Test
    void itemsReturns404WhenOrderDoesNotExist() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(summaryRepo.existsById(orderId)).thenReturn(false);

        mockMvc.perform(get("/api/orders/{id}/items", orderId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
