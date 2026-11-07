package com.orders.infrastructure.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutboxCdcCleanupTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(OutboxRepository.class, () -> mock(OutboxRepository.class))
            .withUserConfiguration(OutboxCdcCleanup.class);

    @Test
    void isActiveInCdcMode() {
        runner.withPropertyValues("app.outbox.mode=cdc")
                .run(context -> assertThat(context).hasSingleBean(OutboxCdcCleanup.class));
    }

    @Test
    void isInactiveInPollingModeWhereThePublisherTracksStatus() {
        runner.withPropertyValues("app.outbox.mode=polling")
                .run(context -> assertThat(context).doesNotHaveBean(OutboxCdcCleanup.class));
    }

    @Test
    void isInactiveWhenNoModeIsConfigured() {
        runner.run(context -> assertThat(context).doesNotHaveBean(OutboxCdcCleanup.class));
    }
}
