package com.alohalegend.survivaltweaks;

import java.util.UUID;

final class HeadDropPolicy {
    private HeadDropPolicy() {
    }

    static boolean isEligible(HeadDropSettings settings, HeadDropSubject killer, HeadDropSubject victim,
                              HeadDropState state, long nowMillis) {
        if (killer.uuid().equals(victim.uuid())) {
            return false;
        }
        if (killer.playtimeHours() < settings.minimumKillerPlaytimeHours()) {
            return false;
        }
        if (victim.playtimeHours() < settings.minimumVictimPlaytimeHours()) {
            return false;
        }
        if (settings.blockSameIp() && !killer.address().isEmpty() && killer.address().equals(victim.address())) {
            return false;
        }
        if (withinCooldown(nowMillis, state.lastKillerRoll(killer.uuid()), settings.killerCooldownMillis())) {
            return false;
        }
        if (withinCooldown(nowMillis, state.lastVictimRoll(victim.uuid()), settings.victimCooldownMillis())) {
            return false;
        }
        return !withinCooldown(nowMillis, state.lastPairRoll(killer.uuid(), victim.uuid()), settings.pairCooldownMillis());
    }

    static double clampChance(double chance) {
        return Math.max(0.0D, Math.min(1.0D, chance));
    }

    static boolean withinCooldown(long nowMillis, long previousMillis, long cooldownMillis) {
        return previousMillis > 0L && cooldownMillis > 0L && nowMillis - previousMillis < cooldownMillis;
    }

    interface HeadDropState {
        long lastKillerRoll(UUID killer);

        long lastVictimRoll(UUID victim);

        long lastPairRoll(UUID killer, UUID victim);
    }
}

