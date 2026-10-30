package com.delivery.common.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.common.PostgresApiE2ETestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * LAC-46: the production lock provider (JobSchedulingConfig) works against the V052 shedlock table — a second
 * "replica" cannot take a held job lock until it is released. Tests run with app.jobs.enabled=false, so the scheduler
 * and its lock provider bean are absent here and the provider is built from the same config method.
 */
class ShedLockPostgresTest extends PostgresApiE2ETestBase {

    @Autowired private DataSource dataSource;
    @Autowired private ApplicationContext context;

    @Test
    void aHeldJobLockCannotBeTakenByAnotherReplicaUntilReleased() {
        assertThat(context.getBeanNamesForType(LockProvider.class)).as("scheduler is off in tests").isEmpty();
        LockProvider replicaA = new JobSchedulingConfig().lockProvider(dataSource);
        LockProvider replicaB = new JobSchedulingConfig().lockProvider(dataSource);
        LockConfiguration config = new LockConfiguration(Instant.now(), "test-job-" + UUID.randomUUID(),
                Duration.ofMinutes(1), Duration.ZERO);

        Optional<SimpleLock> held = replicaA.lock(config);
        assertThat(held).isPresent();
        assertThat(replicaB.lock(config)).isEmpty();

        held.get().unlock();
        Optional<SimpleLock> again = replicaB.lock(config);
        assertThat(again).isPresent();
        again.get().unlock();
    }
}
