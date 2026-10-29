package com.delivery.common.jobs;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on scheduled jobs (LAC-46) and makes each one run on a single replica at a time: every {@code @Scheduled}
 * job method also carries {@code @SchedulerLock(name = ...)}, and ShedLock takes a row in the {@code shedlock}
 * table (V052) before running it. Lock times come from the database clock, so replicas with skewed clocks agree.
 *
 * <p>{@code app.jobs.enabled=false} (the test default) switches the whole scheduler off; the job beans still exist,
 * so tests call their {@code run()} methods directly. Each job also has its own {@code app.jobs.<job>.enabled}.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobSchedulingConfig {

    /** JDBC lock provider over the V052 shedlock table, using database time. */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
