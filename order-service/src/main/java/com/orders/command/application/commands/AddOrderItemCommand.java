package com.orders.command.application.commands;

import java.math.BigDecimal;
import java.util.UUID;

public record AddOrderItemCommand(UUID orderId, UUID productId, String productName, int quantity, BigDecimal unitPrice) {
}
