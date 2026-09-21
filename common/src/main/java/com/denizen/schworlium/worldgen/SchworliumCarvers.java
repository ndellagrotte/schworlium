package com.denizen.schworlium.worldgen;

import com.denizen.schworlium.Constants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.carver.WorldCarver;

public final class SchworliumCarvers {

    public static final Identifier WORLEY_CAVE_ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "worley_cave");

    /** The datapack carver entry (data/schworlium/worldgen/carver/worley_cave.json). */
    public static final ResourceKey<WorldCarver> WORLEY_CAVE =
            ResourceKey.create(Registries.CARVER, WORLEY_CAVE_ID);

    private static boolean bootstrapped = false;

    private SchworliumCarvers() {}

    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        Registry.register(BuiltInRegistries.CARVER_TYPE, WORLEY_CAVE_ID, WorldCarverWorley.CODEC);
        bootstrapped = true;
        Constants.LOG.info("Registered Worley carver type");
    }
}
