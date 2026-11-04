package com.orders.command.domain.events;

public class OrderSubmittedEvent extends DomainEvent {

    @Override
    public String getEventType() {
        return "OrderSubmitted";
    }

    @Override
    public int getEventVersion() {
        return 1;
    }
}
