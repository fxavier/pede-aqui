package com.delivery.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Maps a request (method + path) to the route group that limits it (LAC-11). When several groups match, the most
 * specific path pattern wins (PathPattern specificity, e.g. /deliveries/{@literal *}/complete beats /deliveries/**), so the
 * configuration needs no ordering. Requests matching no group are not limited.
 */
public class RateLimitPolicy {

    /** A configured group with its pre-built Bucket4j configuration. */
    public record Rule(String group, long capacity, Duration period, BucketConfiguration bucket) {
    }

    /** The rule that applies plus the group pattern that selected it. */
    public record Match(Rule rule, String groupPattern) {
    }

    private record Entry(Rule rule, PathPattern pattern, Set<String> methods) {
    }

    private final List<Entry> entries;

    public RateLimitPolicy(Map<String, RateLimitProperties.Group> groups) {
        PathPatternParser parser = new PathPatternParser();
        List<Entry> built = new ArrayList<>();
        groups.forEach((name, group) -> {
            if (group.getCapacity() <= 0 || group.getPeriod() == null || group.getPeriod().isZero()
                    || group.getPeriod().isNegative()) {
                throw new IllegalStateException("app.rate-limit.groups." + name
                        + " needs capacity > 0 and a positive period");
            }
            BucketConfiguration bucket = BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(group.getCapacity())
                            .refillGreedy(group.getCapacity(), group.getPeriod())
                            .build())
                    .build();
            Rule rule = new Rule(name, group.getCapacity(), group.getPeriod(), bucket);
            Set<String> methods = group.getMethods().stream()
                    .map(m -> m.trim().toUpperCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            for (String path : group.getPaths()) {
                built.add(new Entry(rule, parser.parse(path), methods));
            }
        });
        this.entries = List.copyOf(built);
    }

    /** The group limiting {@code method path} (path relative to the context path), if any. */
    public Optional<Match> resolve(String method, String path) {
        PathContainer container = PathContainer.parsePath(path);
        String upperMethod = method.toUpperCase(Locale.ROOT);
        return entries.stream()
                .filter(e -> e.methods().isEmpty() || e.methods().contains(upperMethod))
                .filter(e -> e.pattern().matches(container))
                .min(Comparator.comparing(Entry::pattern, PathPattern.SPECIFICITY_COMPARATOR)
                        .thenComparing(e -> e.rule().group()))
                .map(e -> new Match(e.rule(), e.pattern().getPatternString()));
    }
}
