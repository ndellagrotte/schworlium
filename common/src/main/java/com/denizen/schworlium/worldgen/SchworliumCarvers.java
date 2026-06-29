package com.denizen.schworlium.worldgen;

import com.denizen.schworlium.Constants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;

public final class SchworliumCarvers {

    public static final Identifier WORLEY_CAVE_ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "worley_cave");


    public static final WorldCarverWorley WORLEY_CAVE = new WorldCarverWorley();

    public static final ResourceKey<ConfiguredWorldCarver<?>> CONFIGURED_WORLEY_CAVE =
            ResourceKey.create(Registries.CONFIGURED_CARVER, WORLEY_CAVE_ID);

    private static boolean bootstrapped = false;

    private SchworliumCarvers() {}

    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        Registry.register(BuiltInRegistries.CARVER, WORLEY_CAVE_ID, WORLEY_CAVE);
        bootstrapped = true;
        Constants.LOG.info("Registered Worley carver type");
    }
}
