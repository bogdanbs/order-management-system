package com.orders.infrastructure.messaging;

public final class KafkaTopics {
    public static final String ORDER_EVENTS = "order.events";
    public static final String ORDER_DLQ = "order.events.dlq";

    private KafkaTopics() {
    }
}
