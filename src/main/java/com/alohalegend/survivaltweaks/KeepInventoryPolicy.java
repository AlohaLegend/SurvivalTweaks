package com.alohalegend.survivaltweaks;

final class KeepInventoryPolicy {
    private final boolean keepPvp;
    private final boolean keepMobs;
    private final boolean keepWorld;

    KeepInventoryPolicy(boolean keepPvp, boolean keepMobs, boolean keepWorld) {
        this.keepPvp = keepPvp;
        this.keepMobs = keepMobs;
        this.keepWorld = keepWorld;
    }

    boolean shouldKeep(DeathType type) {
        return switch (type) {
            case PVP -> keepPvp;
            case MOBS -> keepMobs;
            case WORLD -> keepWorld;
        };
    }

    boolean keepPvp() {
        return keepPvp;
    }

    boolean keepMobs() {
        return keepMobs;
    }

    boolean keepWorld() {
        return keepWorld;
    }
}

