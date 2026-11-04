package com.orders.command.domain.events;

public class OrderCreatedEventV1 extends DomainEvent {
    private String customerName;
    private String shippingAddress;

    @Override
    public String getEventType() {
        return "OrderCreated";
    }

    @Override
    public int getEventVersion() {
        return 1;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }
}
