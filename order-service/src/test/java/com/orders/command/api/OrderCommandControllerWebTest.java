package com.orders.command.api;

import com.orders.command.application.commands.SubmitOrderCommand;
import com.orders.command.application.handlers.OrderCommandHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderCommandController.class)
class OrderCommandControllerWebTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCommandHandler handler;

    @Test
    void submitDelegatesToTheCommandHandler() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/orders/{id}/submit", orderId))
                .andExpect(status().isOk());

        verify(handler).submit(new SubmitOrderCommand(orderId));
    }

    @Test
    void asyncSubmitEndpointNoLongerExists() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/submit-async", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
