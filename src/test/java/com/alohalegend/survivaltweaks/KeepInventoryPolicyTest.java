package com.alohalegend.survivaltweaks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class KeepInventoryPolicyTest {
    @Test
    void productionDefaultsKeepOnlyPvp() {
        KeepInventoryPolicy policy = new KeepInventoryPolicy(true, false, false);

        assertTrue(policy.shouldKeep(DeathType.PVP));
        assertFalse(policy.shouldKeep(DeathType.MOBS));
        assertFalse(policy.shouldKeep(DeathType.WORLD));
    }

    @Test
    void allRulesCanBeEnabled() {
        KeepInventoryPolicy policy = new KeepInventoryPolicy(true, true, true);

        assertTrue(policy.shouldKeep(DeathType.PVP));
        assertTrue(policy.shouldKeep(DeathType.MOBS));
        assertTrue(policy.shouldKeep(DeathType.WORLD));
    }

    @Test
    void allRulesCanBeDisabled() {
        KeepInventoryPolicy policy = new KeepInventoryPolicy(false, false, false);

        assertFalse(policy.shouldKeep(DeathType.PVP));
        assertFalse(policy.shouldKeep(DeathType.MOBS));
        assertFalse(policy.shouldKeep(DeathType.WORLD));
    }
}

