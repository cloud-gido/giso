package com.giso.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SemVerTest {
    @Test
    void compareVersions() {
        assertTrue(SemVer.compare("2.0.0", "1.9.9") > 0);
        assertTrue(SemVer.compare("1.4.0", "1.4.0") == 0);
        assertTrue(SemVer.compare("1.3.9", "1.4.0") < 0);
    }
}
