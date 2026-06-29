package com.denizen.schworlium.util;

public final class WorldSeedHolder {

    public static volatile long SEED = 0L;
    public static volatile boolean HAS_SEED = false;

    private WorldSeedHolder() {}

    public static void set(long seed) {
        SEED = seed;
        HAS_SEED = true;
    }

    public static void clear() {
        HAS_SEED = false;
        SEED = 0L;
    }
}
