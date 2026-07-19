package com.alohalegend.survivaltweaks;

final class SpawnerProximityPolicy {
    private SpawnerProximityPolicy() {
    }

    static boolean isWithinCube(int originX, int originY, int originZ, int targetX, int targetY, int targetZ, int radius) {
        return Math.abs(originX - targetX) <= radius
            && Math.abs(originY - targetY) <= radius
            && Math.abs(originZ - targetZ) <= radius;
    }
}

