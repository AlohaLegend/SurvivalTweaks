package com.alohalegend.survivaltweaks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class HeadDropPolicyTest {
    private static final UUID KILLER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID VICTIM = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final long NOW = 1_000_000_000L;

    @Test
    void rejectsSamePlayer() {
        HeadDropSubject killer = new HeadDropSubject(KILLER, 100, "1.2.3.4");
        HeadDropSubject victim = new HeadDropSubject(KILLER, 100, "5.6.7.8");

        assertFalse(HeadDropPolicy.isEligible(settings(), killer, victim, emptyState(), NOW));
    }

    @Test
    void rejectsLowPlaytime() {
        HeadDropSubject killer = new HeadDropSubject(KILLER, 23, "1.2.3.4");
        HeadDropSubject victim = new HeadDropSubject(VICTIM, 100, "5.6.7.8");

        assertFalse(HeadDropPolicy.isEligible(settings(), killer, victim, emptyState(), NOW));
    }

    @Test
    void rejectsSameAddressWhenEnabled() {
        HeadDropSubject killer = new HeadDropSubject(KILLER, 100, "1.2.3.4");
        HeadDropSubject victim = new HeadDropSubject(VICTIM, 100, "1.2.3.4");

        assertFalse(HeadDropPolicy.isEligible(settings(), killer, victim, emptyState(), NOW));
    }

    @Test
    void rejectsKillerCooldown() {
        MutableState state = new MutableState();
        state.killers.put(KILLER, NOW - 1_000L);

        assertFalse(HeadDropPolicy.isEligible(settings(), goodKiller(), goodVictim(), state, NOW));
    }

    @Test
    void rejectsVictimCooldown() {
        MutableState state = new MutableState();
        state.victims.put(VICTIM, NOW - 1_000L);

        assertFalse(HeadDropPolicy.isEligible(settings(), goodKiller(), goodVictim(), state, NOW));
    }

    @Test
    void rejectsPairCooldown() {
        MutableState state = new MutableState();
        state.pairs.put(KILLER + "." + VICTIM, NOW - 1_000L);

        assertFalse(HeadDropPolicy.isEligible(settings(), goodKiller(), goodVictim(), state, NOW));
    }

    @Test
    void allowsEligibleRollAfterCooldowns() {
        MutableState state = new MutableState();
        HeadDropSettings settings = settings();
        state.killers.put(KILLER, NOW - settings.killerCooldownMillis() - 1L);
        state.victims.put(VICTIM, NOW - settings.victimCooldownMillis() - 1L);
        state.pairs.put(KILLER + "." + VICTIM, NOW - settings.pairCooldownMillis() - 1L);

        assertTrue(HeadDropPolicy.isEligible(settings, goodKiller(), goodVictim(), state, NOW));
    }

    @Test
    void clampsChance() {
        assertEquals(0.0D, HeadDropPolicy.clampChance(-1.0D));
        assertEquals(0.25D, HeadDropPolicy.clampChance(0.25D));
        assertEquals(1.0D, HeadDropPolicy.clampChance(2.0D));
    }

    private static HeadDropSubject goodKiller() {
        return new HeadDropSubject(KILLER, 100, "1.2.3.4");
    }

    private static HeadDropSubject goodVictim() {
        return new HeadDropSubject(VICTIM, 100, "5.6.7.8");
    }

    private static HeadDropSettings settings() {
        return new HeadDropSettings(true, 0.00005D, 24, 24, 6_000L, 7_000L, 30_000L, true, true, true, true);
    }

    private static MutableState emptyState() {
        return new MutableState();
    }

    private static final class MutableState implements HeadDropPolicy.HeadDropState {
        private final Map<UUID, Long> killers = new HashMap<>();
        private final Map<UUID, Long> victims = new HashMap<>();
        private final Map<String, Long> pairs = new HashMap<>();

        @Override
        public long lastKillerRoll(UUID killer) {
            return killers.getOrDefault(killer, 0L);
        }

        @Override
        public long lastVictimRoll(UUID victim) {
            return victims.getOrDefault(victim, 0L);
        }

        @Override
        public long lastPairRoll(UUID killer, UUID victim) {
            return pairs.getOrDefault(killer + "." + victim, 0L);
        }
    }
}

