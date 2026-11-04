package com.orders.command.application.commands;

import java.math.BigDecimal;
import java.util.UUID;

public record ConfirmPaymentCommand(UUID orderId, UUID paymentId, BigDecimal amount) {
}
