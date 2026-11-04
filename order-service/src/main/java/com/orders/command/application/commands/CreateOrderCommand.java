package com.orders.command.application.commands;

import java.util.UUID;

public record CreateOrderCommand(UUID orderId, String firstName, String lastName, String shippingAddress) {
}
