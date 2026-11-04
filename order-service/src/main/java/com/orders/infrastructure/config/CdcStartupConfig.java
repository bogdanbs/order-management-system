package com.orders.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "app.outbox.mode", havingValue = "cdc")
public class CdcStartupConfig {
    private static final Logger log = LoggerFactory.getLogger(CdcStartupConfig.class);

    @Value("${app.cdc.kafka-connect-url:http://kafka-connect:8083}")
    private String kafkaConnectUrl;

    @Value("${spring.datasource.password}")
    private String databasePassword;

    @EventListener(ApplicationReadyEvent.class)
    public void registerDebeziumConnector() {
        try {
            byte[] bytes = getClass().getResourceAsStream("/debezium-pg-connector.json").readAllBytes();
            String connectorJson = new String(bytes, StandardCharsets.UTF_8)
                    .replace("__ORDERS_DB_PASSWORD__", databasePassword);
            HttpClient client = HttpClient.newHttpClient();

            for (int i = 0; i < 30; i++) {
                try {
                    HttpRequest deleteReq = HttpRequest.newBuilder()
                            .uri(URI.create(kafkaConnectUrl + "/connectors/orders-outbox-connector"))
                            .DELETE()
                            .header("Content-Type", "application/json")
                            .build();
                    client.send(deleteReq, HttpResponse.BodyHandlers.discarding());

                    HttpRequest createReq = HttpRequest.newBuilder()
                            .uri(URI.create(kafkaConnectUrl + "/connectors"))
                            .POST(HttpRequest.BodyPublishers.ofString(connectorJson))
                            .header("Content-Type", "application/json")
                            .build();
                    HttpResponse<String> resp = client.send(createReq, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 201 || resp.statusCode() == 200 || resp.statusCode() == 409) {
                        log.info("Debezium connector registered, status={}", resp.statusCode());
                        return;
                    }
                    log.warn("Unexpected status from Kafka Connect: {}", resp.statusCode());
                } catch (Exception e) {
                    log.warn("Kafka Connect not ready yet (attempt {}), retrying...", i + 1);
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            log.error("Failed to register Debezium connector after 30 attempts");
        } catch (IOException e) {
            log.error("Failed to load Debezium connector config: {}", e.getMessage());
        }
    }
}
