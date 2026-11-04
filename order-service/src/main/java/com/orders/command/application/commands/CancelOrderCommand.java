package com.orders.command.application.commands;

import java.util.UUID;

public record CancelOrderCommand(UUID orderId, String reason) {
}
