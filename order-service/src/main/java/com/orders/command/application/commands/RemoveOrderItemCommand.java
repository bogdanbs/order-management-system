package com.orders.command.application.commands;

import java.util.UUID;

public record RemoveOrderItemCommand(UUID orderId, UUID productId, int quantity) {
}
