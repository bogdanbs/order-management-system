package com.orders.infrastructure.outbox;

import com.orders.TestcontainersConfiguration;
import com.orders.command.domain.Order;
import com.orders.command.domain.events.DomainEvent;
import com.orders.infrastructure.messaging.KafkaTopics;
import com.orders.query.model.OrderSummary;
import com.orders.query.repository.OrderSummaryRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "app.outbox.polling-interval-ms=3600000")
@Import(TestcontainersConfiguration.class)
class OutboxPublisherIT {
    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OrderSummaryRepository summaryRepository;

    @Autowired
    private TransactionTemplate tx;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    void publishesPendingRecordsInOrderKeyedByAggregateAndMarksThemPublished() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(orderId, "Grace", "Hopper", "1 Navy Yard");
        order.addItem(UUID.randomUUID(), "Compiler", 1, new BigDecimal("99.00"));
        List<DomainEvent> events = order.drainPendingEvents();
        tx.executeWithoutResult(status -> outboxService.write(events, orderId, "Order"));

        outboxPublisher.publishPending();

        List<OutboxRecord> records = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(orderId))
                .toList();
        assertThat(records).hasSize(2).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo("PUBLISHED");
            assertThat(r.getProcessedAt()).isNotNull();
        });
        assertThat(consumeValuesForKey(orderId.toString(), 2)).satisfiesExactly(
                created -> assertThat(created).contains("\"eventType\":\"OrderCreated\""),
                itemAdded -> assertThat(itemAdded).contains("\"eventType\":\"OrderItemAdded\""));
    }

    @Test
    void publishedEventsReachTheReadModelProjection() {
        UUID orderId = UUID.randomUUID();
        List<DomainEvent> events = Order.create(orderId, "Grace", "Hopper", "1 Navy Yard").drainPendingEvents();
        tx.executeWithoutResult(status -> outboxService.write(events, orderId, "Order"));

        outboxPublisher.publishPending();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(summaryRepository.findById(orderId))
                        .get()
                        .extracting(OrderSummary::getLastName)
                        .isEqualTo("Hopper"));
    }

    private List<String> consumeValuesForKey(String key, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> values = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(KafkaTopics.ORDER_EVENTS));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        values.add(record.value());
                    }
                }
                return values.size() >= expected;
            });
        }
        return values;
    }
}
