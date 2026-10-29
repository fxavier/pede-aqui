package com.delivery.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.DeliveryApplication;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * ADR-0002 structural verification with a ratcheting baseline (rollout step 1). Spring Modulith detects every
 * violation of the module rules; the ones that already existed when the check was introduced (cycles C1–C9 and the
 * cross-module repository/entity accesses) are listed in {@code modulith/violations-baseline.txt}. The build fails
 * when a violation appears that is not in the baseline, and also when a baseline entry no longer occurs — fixing a
 * violation means deleting its line, so the list can only shrink. When it is empty this test becomes a plain
 * {@code ApplicationModules.verify()}.
 *
 * <p>Cycles are tracked as module-level edges inside a strongly connected component of the module graph, computed
 * here, rather than as Modulith's cycle messages: ArchUnit stops after 100 cycle paths (the current graph has more),
 * so path lists are truncated and unstable, whereas the SCC edge set is complete and deterministic. A new cycle
 * always adds at least one such edge.
 *
 * <p>Regenerate after an intentional fix: {@code mvn test -Dtest=ModularityTest -Dmodulith.baseline.write=true}
 * (review the diff — new lines must never be added this way to hide a new violation).
 */
class ModularityTest {

    private static final String BASELINE_RESOURCE = "/modulith/violations-baseline.txt";
    private static final Path BASELINE_SOURCE = Path.of("src/test/resources/modulith/violations-baseline.txt");
    private static final Pattern NON_EXPOSED = Pattern.compile(
            "^Module '([\\w.]+)' depends on non-exposed type (\\S+) within module '([\\w.]+)'!");

    private static ApplicationModules modules;
    private static Set<String> current;

    @BeforeAll
    static void detect() {
        modules = ApplicationModules.of(DeliveryApplication.class);
        current = currentViolations(modules);
    }

    @Test
    void noViolationsBeyondTheBaseline() throws IOException {
        if (Boolean.getBoolean("modulith.baseline.write")) {
            writeBaseline(current);
        }
        Set<String> added = new TreeSet<>(current);
        added.removeAll(baseline());
        assertThat(added)
                .as("New module-structure violations (ADR-0002). Use the module's public API or an event instead of "
                        + "another module's repository/entity/service internals, and do not add cycles. New entries:")
                .isEmpty();
    }

    @Test
    void baselineOnlyListsViolationsThatStillExist() throws IOException {
        Set<String> stale = new TreeSet<>(baseline());
        stale.removeAll(current);
        assertThat(stale)
                .as("These baseline entries are fixed: delete them from src/test/resources" + BASELINE_RESOURCE
                        + " so they cannot come back")
                .isEmpty();
    }

    @Test
    void commonIsTheOnlyOpenModule() {
        assertThat(modules.stream().filter(ApplicationModule::isOpen).map(ApplicationModule::getName))
                .containsExactly("common");
    }

    /** Regenerates docs/architecture/modules (PlantUML C4 diagrams); opt-in so normal builds stay read-only. */
    @Test
    @EnabledIfSystemProperty(named = "modulith.docs", matches = "true")
    void writeModuleDocumentation() {
        new Documenter(modules, Documenter.Options.defaults().withOutputFolder("../docs/architecture/modules"))
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
        // Module canvases are left out: Documenter 1.4 fails parsing the (absent) Javadoc metadata JSON here.
    }

    // --- violation keys ---

    static Set<String> currentViolations(ApplicationModules modules) {
        Set<String> keys = new TreeSet<>();
        for (String message : modules.detectViolations().getMessages()) {
            if (message.startsWith("Cycle detected")) {
                continue; // covered by the SCC edges below (Modulith/ArchUnit cap the cycle list at 100 paths)
            }
            keys.add(key(message));
        }
        keys.addAll(cycleEdges(modules));
        return keys;
    }

    /**
     * "non-exposed: order -> com.delivery.payment.entity.Payment" — the identity Modulith itself reports (module ->
     * internal type of another module), stable across line and file moves inside the module.
     */
    private static String key(String message) {
        String firstLine = message.lines().findFirst().orElse(message).strip();
        Matcher m = NON_EXPOSED.matcher(firstLine);
        if (!m.find()) {
            return "other: " + firstLine;
        }
        return "non-exposed: " + m.group(1) + " -> " + m.group(2);
    }

    /** Module edges a -> b where a and b sit in the same strongly connected component (i.e. on some cycle). */
    private static Set<String> cycleEdges(ApplicationModules modules) {
        Map<String, Set<String>> graph = new TreeMap<>();
        for (ApplicationModule module : modules) {
            if (module.isOpen()) {
                continue;
            }
            Set<String> targets = new TreeSet<>();
            module.getDirectDependencies(modules).uniqueModules()
                    .filter(target -> !target.isOpen() && !target.getName().equals(module.getName()))
                    .forEach(target -> targets.add(target.getName()));
            graph.put(module.getName(), targets);
        }
        Map<String, Integer> component = stronglyConnectedComponents(graph);
        Set<String> edges = new TreeSet<>();
        graph.forEach((from, targets) -> targets.forEach(to -> {
            if (component.get(from).equals(component.get(to))) {
                edges.add("cycle-edge: " + from + " -> " + to);
            }
        }));
        return edges;
    }

    /** Tarjan's algorithm; returns node -> component index. */
    private static Map<String, Integer> stronglyConnectedComponents(Map<String, Set<String>> graph) {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Map<String, Integer> component = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new TreeSet<>();
        int[] counter = {0, 0};
        for (String node : graph.keySet()) {
            if (!index.containsKey(node)) {
                strongConnect(node, graph, index, low, component, stack, onStack, counter);
            }
        }
        return component;
    }

    private static void strongConnect(String node, Map<String, Set<String>> graph, Map<String, Integer> index,
                                      Map<String, Integer> low, Map<String, Integer> component, Deque<String> stack,
                                      Set<String> onStack, int[] counter) {
        index.put(node, counter[0]);
        low.put(node, counter[0]);
        counter[0]++;
        stack.push(node);
        onStack.add(node);
        for (String next : graph.getOrDefault(node, Set.of())) {
            if (!graph.containsKey(next)) {
                continue;
            }
            if (!index.containsKey(next)) {
                strongConnect(next, graph, index, low, component, stack, onStack, counter);
                low.put(node, Math.min(low.get(node), low.get(next)));
            } else if (onStack.contains(next)) {
                low.put(node, Math.min(low.get(node), index.get(next)));
            }
        }
        if (low.get(node).equals(index.get(node))) {
            String member;
            do {
                member = stack.pop();
                onStack.remove(member);
                component.put(member, counter[1]);
            } while (!member.equals(node));
            counter[1]++;
        }
    }

    // --- baseline file ---

    private static Set<String> baseline() throws IOException {
        Set<String> entries = new TreeSet<>();
        try (InputStream in = ModularityTest.class.getResourceAsStream(BASELINE_RESOURCE)) {
            if (in == null) {
                return entries;
            }
            new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(entries::add);
        }
        return entries;
    }

    private static void writeBaseline(Set<String> entries) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# ADR-0002 known module-structure violations (ratchet: may only shrink). Generated by ModularityTest;");
        lines.add("# see its Javadoc. cycle-edge = module edge inside a dependency cycle (C1-C9 and the transitive ones);");
        lines.add("# non-exposed = <module> uses an internal type of another module. Breaking them is W3-A.");
        lines.addAll(entries);
        Files.createDirectories(BASELINE_SOURCE.getParent());
        Files.write(BASELINE_SOURCE, lines, StandardCharsets.UTF_8);
    }
}
