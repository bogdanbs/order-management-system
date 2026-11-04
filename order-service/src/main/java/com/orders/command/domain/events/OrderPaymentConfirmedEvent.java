package com.orders.command.domain.events;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderPaymentConfirmedEvent extends DomainEvent {
    private UUID paymentId;
    private BigDecimal amount;

    @Override
    public String getEventType() {
        return "OrderPaymentConfirmed";
    }

    @Override
    public int getEventVersion() {
        return 1;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
