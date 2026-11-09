package com.orders.query.projection;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProjectionProcessingLock {
    private static final long LOCK_ID = 1330791506L;

    private final JdbcTemplate jdbcTemplate;

    public ProjectionProcessingLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquireExclusive() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, LOCK_ID);
                statement.execute();
            }
            return null;
        });
    }

    public void acquireShared() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock_shared(?)")) {
                statement.setLong(1, LOCK_ID);
                statement.execute();
            }
            return null;
        });
    }

}
