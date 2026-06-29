package com.giso.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsTest {

    @BeforeEach
    void reset() {
        Metrics.reset();
    }

    @Test
    void countsAndRendersPrometheusFormat() {
        Metrics.inc("giso_events_total{status=\"ok\"}");
        Metrics.inc("giso_events_total{status=\"ok\"}");
        Metrics.inc("giso_events_total{status=\"error\"}");

        String out = Metrics.render();
        assertTrue(out.contains("giso_events_total{status=\"ok\"} 2"));
        assertTrue(out.contains("giso_events_total{status=\"error\"} 1"));
        assertTrue(out.contains("# TYPE giso_events_total counter"));
        assertTrue(out.contains("giso_gateway_uptime_seconds"));
    }
}
