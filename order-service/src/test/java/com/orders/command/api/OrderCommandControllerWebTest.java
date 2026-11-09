package com.orders.command.api;

import com.orders.api.error.GlobalExceptionHandler;
import com.orders.command.application.commands.SubmitOrderCommand;
import com.orders.command.application.handlers.OrderCommandHandler;
import com.orders.command.domain.OrderNotFoundException;
import com.orders.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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

    @Test
    void submitDelegatesToTheCommandHandlerWhenAuthenticated() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/orders/{id}/submit", orderId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders"))))
                .andExpect(status().isOk());

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
    void returns404ProblemDetailWhenOrderNotFound() throws Exception {
        UUID orderId = UUID.randomUUID();
        doThrow(new OrderNotFoundException(orderId)).when(handler).submit(new SubmitOrderCommand(orderId));

        mockMvc.perform(post("/api/orders/{id}/submit", orderId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders"))))
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
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Business Invariant Violation"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Order must be DRAFT to submit"));
    }
}
