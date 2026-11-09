package com.orders.command.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.api.error.GlobalExceptionHandler;
import com.orders.command.application.commands.CreateOrderCommand;
import com.orders.command.application.commands.SubmitOrderCommand;
import com.orders.command.application.handlers.OrderCommandHandler;
import com.orders.command.domain.OrderNotFoundException;
import com.orders.infrastructure.config.SecurityConfig;
import com.orders.infrastructure.ownership.OrderAccessService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderCommandController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class OrderCommandControllerWebTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCommandHandler handler;

    @MockitoBean
    private OrderAccessService orderAccessService;

    @Test
    void createAssignsOrderToAuthenticatedJwtSubject() throws Exception {
        when(handler.createOrder(any())).thenAnswer(invocation ->
                invocation.getArgument(0, CreateOrderCommand.class).orderId());

        var response = mockMvc.perform(post("/api/orders")
                        .contentType("application/json")
                        .content("""
                                {"firstName":"Ada","lastName":"Lovelace","shippingAddress":"London"}
                                """)
                        .with(jwt().jwt(token -> token.subject("user-123"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_orders.write"))))
                .andExpect(status().isOk())
                .andReturn();

        ArgumentCaptor<CreateOrderCommand> commandCaptor = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(handler).createOrder(commandCaptor.capture());
        CreateOrderCommand command = commandCaptor.getValue();
        assertEquals("user-123", command.ownerSubject());
        assertEquals("Ada", command.firstName());
        assertEquals(command.orderId().toString(),
                new ObjectMapper().readTree(response.getResponse().getContentAsString()).get("orderId").asText());
    }

    @Test
    void submitDelegatesToTheCommandHandlerWhenAuthenticated() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/orders/{id}/submit", orderId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders.write"))))
                .andExpect(status().isOk());

        verify(orderAccessService).requireAccess(eq(orderId), any());
        verify(handler).submit(new SubmitOrderCommand(orderId));
    }

    @Test
    void submitFailsWhenUnauthenticated() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/orders/{id}/submit", orderId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitFailsWhenLackingRequiredScope() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/orders/{id}/submit", orderId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_other"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void readScopeCannotSubmitAnOrder() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/orders/{id}/submit", orderId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void writeScopeCannotReadOrders() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders.write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void returns404ProblemDetailWhenOrderNotFound() throws Exception {
        UUID orderId = UUID.randomUUID();
        doThrow(new OrderNotFoundException(orderId)).when(handler).submit(new SubmitOrderCommand(orderId));

        mockMvc.perform(post("/api/orders/{id}/submit", orderId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders.write"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void returns400ProblemDetailWhenBusinessRuleViolated() throws Exception {
        UUID orderId = UUID.randomUUID();
        doThrow(new IllegalStateException("Order must be DRAFT to submit"))
                .when(handler).submit(new SubmitOrderCommand(orderId));

        mockMvc.perform(post("/api/orders/{id}/submit", orderId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders.write"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Business Invariant Violation"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Order must be DRAFT to submit"));
    }
}
