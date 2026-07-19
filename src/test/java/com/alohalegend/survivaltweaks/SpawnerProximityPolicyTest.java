package com.alohalegend.survivaltweaks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SpawnerProximityPolicyTest {
    @Test
    void detectsSpawnersWithinConfiguredCube() {
        assertTrue(SpawnerProximityPolicy.isWithinCube(10, 64, 10, 14, 64, 10, 4));
        assertTrue(SpawnerProximityPolicy.isWithinCube(10, 64, 10, 6, 60, 6, 4));
    }

    @Test
    void ignoresSpawnersOutsideConfiguredCube() {
        assertFalse(SpawnerProximityPolicy.isWithinCube(10, 64, 10, 15, 64, 10, 4));
        assertFalse(SpawnerProximityPolicy.isWithinCube(10, 64, 10, 10, 69, 10, 4));
    }
}

