package com.orders.command.api;

import com.orders.command.application.commands.AddOrderItemCommand;
import com.orders.command.application.commands.CancelOrderCommand;
import com.orders.command.application.commands.ConfirmPaymentCommand;
import com.orders.command.application.commands.CreateOrderCommand;
import com.orders.command.application.commands.RemoveOrderItemCommand;
import com.orders.command.application.commands.SubmitOrderCommand;
import com.orders.command.application.handlers.OrderCommandHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderCommandController {
    private final OrderCommandHandler handler;

    public OrderCommandController(OrderCommandHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody @Validated CreateOrderRequest req) {
        UUID orderId = UUID.randomUUID();
        handler.createOrder(new CreateOrderCommand(orderId, req.firstName(), req.lastName(), req.shippingAddress()));
        return ResponseEntity.ok(Map.of("orderId", orderId.toString()));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<Void> addItem(@PathVariable UUID id, @RequestBody @Validated AddItemRequest req) {
        handler.addItem(new AddOrderItemCommand(id, req.productId(), req.productName(), req.quantity(), req.unitPrice()));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/items/{productId}")
    public ResponseEntity<Void> removeItem(@PathVariable UUID id, @PathVariable UUID productId) {
        handler.removeItem(new RemoveOrderItemCommand(id, productId, 1));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<Void> submit(@PathVariable UUID id) {
        handler.submit(new SubmitOrderCommand(id));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/confirm-payment")
    public ResponseEntity<Void> confirmPayment(@PathVariable UUID id,
                                               @RequestBody @Validated ConfirmPaymentRequest req) {
        handler.confirmPayment(new ConfirmPaymentCommand(id, req.paymentId(), req.amount()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, @RequestBody @Validated CancelRequest req) {
        handler.cancel(new CancelOrderCommand(id, req.reason()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/submit-async")
    public ResponseEntity<String> submitAsync() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body("Asynchronous submission is unavailable; use POST /api/orders/{id}/submit.");
    }
}
