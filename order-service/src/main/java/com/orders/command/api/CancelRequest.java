package com.orders.command.api;

import jakarta.validation.constraints.NotBlank;

public record CancelRequest(
        @NotBlank String reason
) {
}
