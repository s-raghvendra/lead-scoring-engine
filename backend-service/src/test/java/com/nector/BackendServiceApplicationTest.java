package com.nector;

import org.junit.jupiter.api.Test;

/**
 * Lightweight sanity-check that doesn't require a full Spring context
 * (avoiding the need for live DB connections in CI).
 */
class BackendServiceApplicationTest {

    @Test
    void contextLoads() {
        // Spring context load is validated during integration test with Testcontainers.
        // This placeholder keeps Gradle's test task from failing with "no tests found".
    }
}
