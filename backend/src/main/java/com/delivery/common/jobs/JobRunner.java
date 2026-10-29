package com.delivery.common.jobs;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * The one way scheduled jobs run (LAC-46): wraps a job body with structured logging (MDC {@code job} and
 * {@code jobRunId}), a duration timer, a run counter and a last-success gauge, and swallows the failure after
 * recording it so the scheduler thread survives and the next tick retries.
 *
 * <p>Metrics (Prometheus names): {@code job_runs_total{job,result=success|failure}},
 * {@code job_duration_seconds{job,result}}, {@code job_last_success_timestamp_seconds{job}} — alert when
 * {@code time() - job_last_success_timestamp_seconds} exceeds a few intervals of the job.
 */
@Component
public class JobRunner {
    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> lastSuccess = new ConcurrentHashMap<>();

    public JobRunner(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Runs one job tick. The body returns a short, PII-free summary for the log line (e.g. "released=2 committed=0").
     * Returns true when the body completed, false when it threw (the error is logged and counted, not rethrown).
     */
    public boolean run(String job, Supplier<String> body) {
        String runId = UUID.randomUUID().toString();
        MDC.put("job", job);
        MDC.put("jobRunId", runId);
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String summary = body.get();
            long nanos = sample.stop(timer(job, "success"));
            counter(job, "success").increment();
            lastSuccessGauge(job).set(Instant.now().getEpochSecond());
            log.info("Job {} finished in {} ms: {}", job, nanos / 1_000_000, summary);
            return true;
        } catch (RuntimeException ex) {
            sample.stop(timer(job, "failure"));
            counter(job, "failure").increment();
            log.error("Job {} failed", job, ex);
            return false;
        } finally {
            MDC.remove("job");
            MDC.remove("jobRunId");
        }
    }

    private Timer timer(String job, String result) {
        return Timer.builder("job.duration").description("Scheduled job run duration")
                .tag("job", job).tag("result", result).register(meterRegistry);
    }

    private Counter counter(String job, String result) {
        return Counter.builder("job.runs").description("Scheduled job runs")
                .tag("job", job).tag("result", result).register(meterRegistry);
    }

    private AtomicLong lastSuccessGauge(String job) {
        return lastSuccess.computeIfAbsent(job, name -> meterRegistry.gauge("job.last.success.timestamp.seconds",
                Tags.of("job", name), new AtomicLong()));
    }
}
