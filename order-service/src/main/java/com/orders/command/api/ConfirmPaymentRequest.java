package com.orders.command.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ConfirmPaymentRequest(
        @NotNull UUID paymentId,
        @NotNull @DecimalMin("0.01") BigDecimal amount
) {
}
