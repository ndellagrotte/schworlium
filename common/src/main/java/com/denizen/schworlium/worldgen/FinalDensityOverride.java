package com.denizen.schworlium.worldgen;

import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Duck interface added to {@code NoiseBasedChunkGenerator} by schworlium's mixin. When set, the generator
 * samples this function instead of its noise settings' {@code final_density} when filling terrain.
 */
public interface FinalDensityOverride {
    void schworlium$setFinalDensity(DensityFunction finalDensity);
}
