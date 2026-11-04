package com.orders.command.api;

import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String shippingAddress
) {
}
