package com.orders.query.api;

import com.orders.infrastructure.eventstore.EventStoreRepository;
import com.orders.query.model.OrderSummary;
import com.orders.query.repository.OrderItemViewRepository;
import com.orders.query.repository.OrderSummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderQueryController.class)
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
    void returnsCustomerOrdersAsJson() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderSummary order = new OrderSummary();
        order.setOrderId(orderId);
        order.setFirstName("Ada");
        order.setLastName("Lovelace");
        order.setStatus("CREATED");
        order.setTotalAmount(new BigDecimal("25.50"));

        when(summaryRepo.findByFirstNameIgnoreCaseAndLastNameIgnoreCaseOrderByCreatedAtDesc("ada", "lovelace"))
                .thenReturn(List.of(order));

        mockMvc.perform(get("/api/orders/customer")
                        .param("firstName", "ada")
                        .param("lastName", "lovelace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$[0].firstName").value("Ada"))
                .andExpect(jsonPath("$[0].lastName").value("Lovelace"))
                .andExpect(jsonPath("$[0].totalAmount").value(25.50));
    }

    @Test
    void requiresBothCustomerNameParameters() throws Exception {
        mockMvc.perform(get("/api/orders/customer").param("firstName", "Ada"))
                .andExpect(status().isBadRequest());
    }
}
