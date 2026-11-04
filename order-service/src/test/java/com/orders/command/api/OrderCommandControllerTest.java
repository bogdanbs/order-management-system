package com.orders.command.api;

import com.orders.command.application.handlers.OrderCommandHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OrderCommandControllerTest {
    @Test
    void asyncSubmitClearlyReportsThatItIsUnavailable() {
        OrderCommandController controller = new OrderCommandController(mock(OrderCommandHandler.class));

        var response = controller.submitAsync();

        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        assertTrue(response.getBody().contains("/api/orders/{id}/submit"));
    }
}
