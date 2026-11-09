package com.orders.query.rebuild;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.command.domain.events.DomainEvent;
import com.orders.command.domain.events.OrderCancelledEvent;
import com.orders.command.domain.events.OrderCreatedEventV2;
import com.orders.command.domain.events.OrderItemAddedEvent;
import com.orders.command.domain.events.OrderItemRemovedEvent;
import com.orders.command.domain.events.OrderPaymentConfirmedEvent;
import com.orders.command.domain.events.OrderSubmittedEvent;
import com.orders.infrastructure.eventstore.EventRecord;
import com.orders.infrastructure.eventstore.EventStoreRepository;
import com.orders.infrastructure.upcasting.UpcasterChain;
import com.orders.query.projection.ProjectionProcessingLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectionRebuilderService {
    private static final Logger log = LoggerFactory.getLogger(ProjectionRebuilderService.class);
    private static final int BATCH_SIZE = 500;
    private static final long REBUILD_COORDINATION_LOCK_ID = 1_330_791_508L;
    private static final String STAGING_SCHEMA = "query_side_rebuild";
    private static final RowMapper<EventRecord> EVENT_ROW_MAPPER = ProjectionRebuilderService::mapEventRecord;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;
    private final EventStoreRepository eventStoreRepository;
    private final UpcasterChain upcasterChain;
    private final ObjectMapper objectMapper;
    private final ProjectionProcessingLock processingLock;

    public ProjectionRebuilderService(JdbcTemplate jdbcTemplate,
                                      DataSource dataSource,
                                      PlatformTransactionManager transactionManager,
                                      EventStoreRepository eventStoreRepository,
                                      UpcasterChain upcasterChain,
                                      ObjectMapper objectMapper,
                                      ProjectionProcessingLock processingLock) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.eventStoreRepository = eventStoreRepository;
        this.upcasterChain = upcasterChain;
        this.objectMapper = objectMapper;
        this.processingLock = processingLock;
    }

    public void rebuild() {
        log.info("Starting staged projection rebuild...");
        try (Connection rebuildLockConnection = dataSource.getConnection()) {
            if (!tryAcquireRebuildLock(rebuildLockConnection)) {
                throw new IllegalStateException("A projection rebuild is already in progress");
            }
            try {
                createStagingTables();
                long totalProcessed = replayExistingEvents();
                totalProcessed += catchUpAndSwap();
                log.info("Projection rebuild complete. Processed {} events.", totalProcessed);
            } finally {
                try {
                    jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + STAGING_SCHEMA + " CASCADE");
                } finally {
                    releaseRebuildLock(rebuildLockConnection);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to coordinate projection rebuild", e);
        }
    }

    private long replayExistingEvents() {
        long processed = 0;
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        Slice<EventRecord> slice;
        do {
            Pageable currentPage = pageable;
            slice = transactionTemplate.execute(status -> {
                Slice<EventRecord> batch =
                        eventStoreRepository.findAllByOrderByAggregateIdAscSequenceNumberAscIdAsc(currentPage);
                for (EventRecord record : batch.getContent()) {
                    applyToStaging(deserialize(record));
                }
                return batch;
            });
            if (slice == null) {
                throw new IllegalStateException("Projection rebuild batch transaction returned no result");
            }
            processed += slice.getNumberOfElements();
            pageable = slice.nextPageable();
        } while (slice.hasNext());
        return processed;
    }

    private long catchUpAndSwap() {
        return transactionTemplate.execute(status -> {
            processingLock.acquireExclusive();
            long caughtUp = 0;
            List<EventRecord> missing;
            do {
                missing = jdbcTemplate.query("""
                        SELECT e.id, e.aggregate_id, e.aggregate_type, e.sequence_number,
                               e.event_type, e.event_version, e.payload, e.metadata, e.occurred_at
                        FROM event_store.event_store e
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM query_side_rebuild.processed_events p
                            WHERE p.event_id = (e.payload::jsonb ->> 'eventId')::uuid
                        )
                        ORDER BY e.aggregate_id, e.sequence_number, e.id
                        LIMIT ?
                        """, EVENT_ROW_MAPPER, BATCH_SIZE);
                for (EventRecord record : missing) {
                    applyToStaging(deserialize(record));
                }
                caughtUp += missing.size();
            } while (missing.size() == BATCH_SIZE);

            swapStagingTables();
            return caughtUp;
        });
    }

    private DomainEvent deserialize(EventRecord record) {
        return upcasterChain.deserialize(
                record.getEventType(), record.getEventVersion(), record.getPayload(), objectMapper);
    }

    private void createStagingTables() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + STAGING_SCHEMA + " CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA " + STAGING_SCHEMA);
        jdbcTemplate.execute("""
                CREATE TABLE query_side_rebuild.order_summary (
                    order_id UUID PRIMARY KEY,
                    first_name VARCHAR(255),
                    last_name VARCHAR(255),
                    status VARCHAR(255),
                    total_amount NUMERIC(38, 2) NOT NULL DEFAULT 0,
                    item_count INT NOT NULL DEFAULT 0,
                    shipping_address VARCHAR(255),
                    created_at TIMESTAMP(6) WITH TIME ZONE,
                    updated_at TIMESTAMP(6) WITH TIME ZONE,
                    owner_subject VARCHAR(255)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE query_side_rebuild.order_item_view (
                    id UUID PRIMARY KEY,
                    order_id UUID NOT NULL,
                    product_id UUID,
                    product_name VARCHAR(255),
                    quantity INT NOT NULL,
                    unit_price NUMERIC(38, 2)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE query_side_rebuild.processed_events (
                    event_id UUID PRIMARY KEY,
                    processed_at TIMESTAMP(6) WITH TIME ZONE
                )
                """);
    }

    private void applyToStaging(DomainEvent event) {
        Boolean alreadyProcessed = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM query_side_rebuild.processed_events WHERE event_id = ?
                )
                """, Boolean.class, event.getEventId());
        if (Boolean.TRUE.equals(alreadyProcessed)) {
            return;
        }
        UUID orderId = event.getAggregateId();
        switch (event) {
            case OrderCreatedEventV2 created -> jdbcTemplate.update("""
                            INSERT INTO query_side_rebuild.order_summary
                                (order_id, first_name, last_name, status, total_amount, item_count,
                                 shipping_address, created_at, updated_at, owner_subject)
                            VALUES (?, ?, ?, 'DRAFT', 0, 0, ?, ?, ?, ?)
                            ON CONFLICT (order_id) DO NOTHING
                            """, orderId, created.getFirstName(), created.getLastName(), created.getShippingAddress(),
                    Timestamp.from(created.getOccurredAt()), Timestamp.from(created.getOccurredAt()),
                    created.getMetadata().get("ownerSubject"));
            case OrderItemAddedEvent added -> applyItemAdded(orderId, added);
            case OrderItemRemovedEvent removed -> applyItemRemoved(orderId, removed);
            case OrderSubmittedEvent orderSubmittedEvent -> updateStatus(orderId, "SUBMITTED", event.getOccurredAt());
            case OrderPaymentConfirmedEvent orderPaymentConfirmedEvent ->
                    updateStatus(orderId, "PAID", event.getOccurredAt());
            case OrderCancelledEvent orderCancelledEvent -> updateStatus(orderId, "CANCELLED", event.getOccurredAt());
            default -> {
            }
        }
        jdbcTemplate.update("""
                INSERT INTO query_side_rebuild.processed_events (event_id, processed_at)
                VALUES (?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, event.getEventId(), Timestamp.from(Instant.now()));
    }

    private void applyItemAdded(UUID orderId, OrderItemAddedEvent event) {
        ProjectedItem existing = jdbcTemplate.query("""
                SELECT id, quantity, unit_price
                FROM query_side_rebuild.order_item_view
                WHERE order_id = ? AND product_id = ?
                """, resultSet -> resultSet.next()
                ? new ProjectedItem(resultSet.getObject("id", UUID.class),
                resultSet.getInt("quantity"), resultSet.getBigDecimal("unit_price"))
                : null, orderId, event.getProductId());
        BigDecimal addedAmount = event.getUnitPrice().multiply(BigDecimal.valueOf(event.getQuantity()));
        if (existing == null) {
            jdbcTemplate.update("""
                    INSERT INTO query_side_rebuild.order_item_view
                        (id, order_id, product_id, product_name, quantity, unit_price)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), orderId, event.getProductId(), event.getProductName(),
                    event.getQuantity(), event.getUnitPrice());
            jdbcTemplate.update("""
                    UPDATE query_side_rebuild.order_summary
                    SET total_amount = total_amount + ?, item_count = item_count + 1,
                        updated_at = ?
                    WHERE order_id = ?
                    """, addedAmount, Timestamp.from(event.getOccurredAt()), orderId);
        } else {
            BigDecimal amountAtExistingPrice =
                    existing.unitPrice().multiply(BigDecimal.valueOf(event.getQuantity()));
            jdbcTemplate.update("""
                    UPDATE query_side_rebuild.order_item_view
                    SET quantity = quantity + ?
                    WHERE id = ?
                    """, event.getQuantity(), existing.id());
            jdbcTemplate.update("""
                    UPDATE query_side_rebuild.order_summary
                    SET total_amount = total_amount + ?, updated_at = ?
                    WHERE order_id = ?
                    """, amountAtExistingPrice, Timestamp.from(event.getOccurredAt()), orderId);
        }
    }

    private void applyItemRemoved(UUID orderId, OrderItemRemovedEvent event) {
        ProjectedItem item = jdbcTemplate.query("""
                SELECT id, quantity, unit_price
                FROM query_side_rebuild.order_item_view
                WHERE order_id = ? AND product_id = ?
                """, resultSet -> resultSet.next()
                ? new ProjectedItem(resultSet.getObject("id", UUID.class),
                resultSet.getInt("quantity"), resultSet.getBigDecimal("unit_price"))
                : null, orderId, event.getProductId());
        if (item == null || event.getQuantity() <= 0 || event.getQuantity() > item.quantity()) {
            throw new IllegalStateException("Invalid item removal during projection rebuild for order " + orderId);
        }
        jdbcTemplate.update("""
                UPDATE query_side_rebuild.order_summary
                SET total_amount = total_amount - ?, item_count = item_count - ?, updated_at = ?
                WHERE order_id = ?
                """, item.unitPrice().multiply(BigDecimal.valueOf(event.getQuantity())),
                event.getQuantity() == item.quantity() ? 1 : 0,
                Timestamp.from(event.getOccurredAt()), orderId);
        if (event.getQuantity() == item.quantity()) {
            jdbcTemplate.update("DELETE FROM query_side_rebuild.order_item_view WHERE id = ?", item.id());
        } else {
            jdbcTemplate.update("""
                    UPDATE query_side_rebuild.order_item_view
                    SET quantity = quantity - ?
                    WHERE id = ?
                    """, event.getQuantity(), item.id());
        }
    }

    private void updateStatus(UUID orderId, String status, Instant occurredAt) {
        jdbcTemplate.update("""
                UPDATE query_side_rebuild.order_summary
                SET status = ?, updated_at = ?
                WHERE order_id = ?
                """, status, Timestamp.from(occurredAt), orderId);
    }

    private void swapStagingTables() {
        jdbcTemplate.execute("DROP TABLE query_side.order_item_view");
        jdbcTemplate.execute("DROP TABLE query_side.order_summary");
        jdbcTemplate.execute("DROP TABLE query_side.processed_events");
        jdbcTemplate.execute("ALTER TABLE query_side_rebuild.order_summary SET SCHEMA query_side");
        jdbcTemplate.execute("ALTER TABLE query_side_rebuild.order_item_view SET SCHEMA query_side");
        jdbcTemplate.execute("ALTER TABLE query_side_rebuild.processed_events SET SCHEMA query_side");
        jdbcTemplate.execute("CREATE INDEX idx_order_summary_customer ON query_side.order_summary "
                + "(LOWER(first_name), LOWER(last_name), created_at DESC)");
        jdbcTemplate.execute("CREATE INDEX idx_order_summary_owner_created ON query_side.order_summary "
                + "(owner_subject, created_at DESC)");
        jdbcTemplate.execute("CREATE INDEX idx_order_item_view_order_id ON query_side.order_item_view (order_id)");
        jdbcTemplate.execute("CREATE INDEX idx_order_item_view_order_product "
                + "ON query_side.order_item_view (order_id, product_id)");
        jdbcTemplate.execute("DROP SCHEMA " + STAGING_SCHEMA + " CASCADE");
    }

    private boolean tryAcquireRebuildLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, REBUILD_COORDINATION_LOCK_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private void releaseRebuildLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, REBUILD_COORDINATION_LOCK_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || !resultSet.getBoolean(1)) {
                    throw new SQLException("Projection rebuild lock was not held by its connection");
                }
            }
        }
    }

    private static EventRecord mapEventRecord(ResultSet resultSet, int rowNum) throws SQLException {
        EventRecord record = new EventRecord();
        record.setId(resultSet.getObject("id", UUID.class));
        record.setAggregateId(resultSet.getObject("aggregate_id", UUID.class));
        record.setAggregateType(resultSet.getString("aggregate_type"));
        record.setSequenceNumber(resultSet.getLong("sequence_number"));
        record.setEventType(resultSet.getString("event_type"));
        record.setEventVersion(resultSet.getInt("event_version"));
        record.setPayload(resultSet.getString("payload"));
        record.setMetadata(resultSet.getString("metadata"));
        record.setOccurredAt(resultSet.getTimestamp("occurred_at").toInstant());
        return record;
    }

    private record ProjectedItem(UUID id, int quantity, BigDecimal unitPrice) {
    }
}
