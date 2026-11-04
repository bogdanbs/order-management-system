package com.orders.command.domain.events;

public class OrderCreatedEventV2 extends DomainEvent {
    private String firstName;
    private String lastName;
    private String shippingAddress;

    @Override
    public String getEventType() {
        return "OrderCreated";
    }

    @Override
    public int getEventVersion() {
        return 2;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }
}
