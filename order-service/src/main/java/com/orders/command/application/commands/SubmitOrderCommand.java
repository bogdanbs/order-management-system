package com.orders.command.application.commands;

import java.util.UUID;

public record SubmitOrderCommand(UUID orderId) {
}
