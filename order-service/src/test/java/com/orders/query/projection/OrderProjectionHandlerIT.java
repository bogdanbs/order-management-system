package com.orders.query.projection;

import com.orders.TestcontainersConfiguration;
import com.orders.infrastructure.messaging.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "app.outbox.polling-interval-ms=3600000")
@Import(TestcontainersConfiguration.class)
class OrderProjectionHandlerIT {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    void sendsEventsThatKeepFailingToTheConfiguredDeadLetterTopic() throws Exception {
        String key = UUID.randomUUID().toString();
        kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, key, "not-json").get();

        ConsumerRecord<String, String> deadLetter = awaitRecord(KafkaTopics.ORDER_DLQ, key);

        assertThat(deadLetter.value()).isEqualTo("not-json");
        Header originalTopic = deadLetter.headers().lastHeader("kafka_dlt-original-topic");
        assertThat(new String(originalTopic.value(), StandardCharsets.UTF_8)).isEqualTo(KafkaTopics.ORDER_EVENTS);
    }

    private ConsumerRecord<String, String> awaitRecord(String topic, String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "projection-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        AtomicReference<ConsumerRecord<String, String>> found = new AtomicReference<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        found.set(record);
                    }
                }
                return found.get() != null;
            });
        }
        return found.get();
    }
}
