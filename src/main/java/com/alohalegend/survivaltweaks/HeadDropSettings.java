package com.alohalegend.survivaltweaks;

import org.bukkit.configuration.file.FileConfiguration;

record HeadDropSettings(
    boolean enabled,
    double chance,
    long minimumKillerPlaytimeHours,
    long minimumVictimPlaytimeHours,
    long killerCooldownMillis,
    long victimCooldownMillis,
    long pairCooldownMillis,
    boolean blockSameIp,
    boolean addToDeathDrops,
    boolean messageKillerOnDrop,
    boolean logDrops
) {
    static HeadDropSettings fromConfig(FileConfiguration config) {
        return new HeadDropSettings(
            config.getBoolean("player-head-drops.enabled", true),
            HeadDropPolicy.clampChance(config.getDouble("player-head-drops.chance", 0.00005D)),
            Math.max(0L, config.getLong("player-head-drops.minimum-killer-playtime-hours", 24L)),
            Math.max(0L, config.getLong("player-head-drops.minimum-victim-playtime-hours", 24L)),
            hoursToMillis(config.getLong("player-head-drops.killer-roll-cooldown-hours", 6L)),
            daysToMillis(config.getLong("player-head-drops.victim-roll-cooldown-days", 7L)),
            daysToMillis(config.getLong("player-head-drops.pair-roll-cooldown-days", 30L)),
            config.getBoolean("player-head-drops.block-same-ip", true),
            config.getBoolean("player-head-drops.add-to-death-drops", true),
            config.getBoolean("player-head-drops.message-killer-on-drop", true),
            config.getBoolean("player-head-drops.log-drops", true)
        );
    }

    static long hoursToMillis(long hours) {
        return Math.max(0L, hours) * 60L * 60L * 1000L;
    }

    static long daysToMillis(long days) {
        return Math.max(0L, days) * 24L * 60L * 60L * 1000L;
    }
}

