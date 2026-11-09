package com.orders.command.application.commands;

import java.util.UUID;

public record CreateOrderCommand(UUID orderId, String firstName, String lastName, String shippingAddress,
                                 String ownerSubject) {
    public CreateOrderCommand(UUID orderId, String firstName, String lastName, String shippingAddress) {
        this(orderId, firstName, lastName, shippingAddress, null);
    }
}
