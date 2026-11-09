package com.orders.query.rebuild;

import com.orders.TestcontainersConfiguration;
import com.orders.command.application.commands.AddOrderItemCommand;
import com.orders.command.application.commands.CreateOrderCommand;
import com.orders.command.application.commands.RemoveOrderItemCommand;
import com.orders.command.application.handlers.OrderCommandHandler;
import com.orders.query.model.OrderItemView;
import com.orders.query.model.OrderSummary;
import com.orders.query.repository.OrderItemViewRepository;
import com.orders.query.repository.OrderSummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.outbox.polling-interval-ms=3600000")
@Import(TestcontainersConfiguration.class)
class ProjectionRebuilderServiceIT {
    @Autowired
    private ProjectionRebuilderService rebuilder;

    @Autowired
    private OrderCommandHandler commandHandler;

    @Autowired
    private OrderSummaryRepository summaryRepository;

    @Autowired
    private OrderItemViewRepository itemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rebuildsIntoShadowTablesAndAtomicallyReplacesLiveProjection() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        commandHandler.createOrder(new CreateOrderCommand(
                orderId, "Ada", "Lovelace", "London", "keycloak-user-1"));
        commandHandler.addItem(new AddOrderItemCommand(
                orderId, productId, "Notebook", 3, new BigDecimal("8.50")));
        commandHandler.removeItem(new RemoveOrderItemCommand(orderId, productId, 1));

        rebuilder.rebuild();

        OrderSummary summary = summaryRepository.findById(orderId).orElseThrow();
        OrderItemView item = itemRepository.findByOrderIdAndProductId(orderId, productId).orElseThrow();
        assertThat(summary.getOwnerSubject()).isEqualTo("keycloak-user-1");
        assertThat(summary.getTotalAmount()).isEqualByComparingTo("17.00");
        assertThat(summary.getItemCount()).isEqualTo(1);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM event_store.event_store e
                JOIN query_side.processed_events p
                  ON p.event_id = (e.payload::jsonb ->> 'eventId')::uuid
                WHERE e.aggregate_id = ?
                """, Integer.class, orderId)).isEqualTo(3);

        rebuilder.rebuild();

        assertThat(summaryRepository.findById(orderId).orElseThrow().getTotalAmount())
                .isEqualByComparingTo("17.00");
        assertThat(itemRepository.findByOrderIdAndProductId(orderId, productId).orElseThrow().getQuantity())
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM event_store.event_store e
                JOIN query_side.processed_events p
                  ON p.event_id = (e.payload::jsonb ->> 'eventId')::uuid
                WHERE e.aggregate_id = ?
                """, Integer.class, orderId)).isEqualTo(3);
    }

    @Test
    void rebuildIncludesRepeatAddsOfTheSameProductInTheTotal() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        commandHandler.createOrder(new CreateOrderCommand(
                orderId, "Ada", "Lovelace", "London", "keycloak-user-1"));
        commandHandler.addItem(new AddOrderItemCommand(
                orderId, productId, "Notebook", 2, new BigDecimal("10.00")));
        commandHandler.addItem(new AddOrderItemCommand(
                orderId, productId, "Notebook", 1, new BigDecimal("10.00")));

        rebuilder.rebuild();

        OrderSummary summary = summaryRepository.findById(orderId).orElseThrow();
        OrderItemView item = itemRepository.findByOrderIdAndProductId(orderId, productId).orElseThrow();
        assertThat(summary.getTotalAmount()).isEqualByComparingTo("30.00");
        assertThat(summary.getItemCount()).isEqualTo(1);
        assertThat(item.getQuantity()).isEqualTo(3);
    }
}
