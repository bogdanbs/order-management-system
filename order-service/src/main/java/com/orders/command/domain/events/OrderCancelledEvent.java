package com.orders.command.domain.events;

public class OrderCancelledEvent extends DomainEvent {
    private String reason;

    @Override
    public String getEventType() {
        return "OrderCancelled";
    }

    @Override
    public int getEventVersion() {
        return 1;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
