package com.adp.gateway.runtime.api;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-process, privacy-safe wall-clock timing evidence; callers persist exported E2 evidence as a sidecar. */
public final class RuntimeStageTimingRecorder {
    private static final Map<String, Map<String, MutableTiming>> TIMINGS = new ConcurrentHashMap<>();

    private RuntimeStageTimingRecorder() {}

    public static void start(String executionId, String stage) {
        TIMINGS.computeIfAbsent(executionId, ignored -> new ConcurrentHashMap<>())
            .computeIfAbsent(stage, ignored -> new MutableTiming()).start = OffsetDateTime.now();
    }

    public static void end(String executionId, String stage) {
        var timing = TIMINGS.computeIfAbsent(executionId, ignored -> new ConcurrentHashMap<>())
            .computeIfAbsent(stage, ignored -> new MutableTiming());
        if (timing.start == null) timing.start = OffsetDateTime.now();
        timing.end = OffsetDateTime.now();
    }

    public static void decision(String executionId, String stage, String decision, List<String> reasonCodes) {
        var timing = TIMINGS.computeIfAbsent(executionId, ignored -> new ConcurrentHashMap<>())
            .computeIfAbsent(stage, ignored -> new MutableTiming());
        timing.decision = decision;
        timing.reasonCodes = List.copyOf(reasonCodes);
    }

    public static List<StageTiming> snapshot(String executionId) {
        var values = TIMINGS.get(executionId);
        if (values == null) return List.of();
        var result = new ArrayList<StageTiming>();
        values.forEach((stage, timing) -> {
            if (timing.start != null && timing.end != null) {
                result.add(new StageTiming(stage, timing.start, timing.end,
                    Duration.between(timing.start, timing.end).toMillis(), timing.decision, timing.reasonCodes));
            }
        });
        result.sort(java.util.Comparator.comparing(StageTiming::startedAt));
        return List.copyOf(result);
    }

    private static final class MutableTiming {
        private OffsetDateTime start;
        private OffsetDateTime end;
        private String decision;
        private List<String> reasonCodes = List.of();
    }

    public record StageTiming(
        String stage,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        long durationMillis,
        String decision,
        List<String> reasonCodes
    ) {
        public StageTiming {
            reasonCodes = List.copyOf(reasonCodes);
        }
    }
}
