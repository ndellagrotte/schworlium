package com.denizen.schworlium.mixin;

import com.denizen.schworlium.Constants;
import com.denizen.schworlium.worldgen.FinalDensityOverride;
import com.denizen.schworlium.worldgen.TectonicCompat;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * Disables vanilla noise caves (cheese/spaghetti/noodle/entrances) in the overworld by handing the
 * overworld's NoiseBasedChunkGenerator a replacement final_density
 * (schworlium:overworld_no_noise_caves_final_density) when the level's ChunkMap is built.
 *
 * Since 26.3 the generator samples final_density straight from its own noise settings
 * (NoiseBasedChunkGenerator.doFill / iterateNoiseColumn) rather than from the RandomState, so the
 * swap is delivered through NoiseBasedChunkGeneratorMixin (see FinalDensityOverride) instead of by
 * rebuilding the settings passed to RandomState.create as the 26.2 branch did.
 *
 * This used to be a third-party wrap_noise_router worldgen modifier, but that library
 * (through at least 1.7.12) parsed the modifier's "dimension" field without ever consulting
 * it: its ChunkMapMixin applied every wrap_noise_router modifier to every ServerLevel, which
 * leaked the overworld-only final_density replacement into the nether and end. Doing it
 * ourselves lets us gate on the level actually being the overworld.
 *
 * Gated on the generator using the minecraft:overworld noise settings (not just the dimension
 * key) so amplified/large-biomes presets and custom overworld generators — whose terrain math
 * the replacement formula does not match — keep their vanilla routers.
 *
 * Tectonic also reuses the minecraft:overworld settings key, so this fires under it too; there we
 * substitute a Tectonic-shaped final_density (see TectonicCompat) instead of the vanilla one so
 * Tectonic's terrain, underground rivers and lava tunnels survive while its cave noise is removed.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Unique
    private static final ResourceKey<DensityFunction> SCHWORLIUM$NO_NOISE_CAVES_FINAL_DENSITY =
            ResourceKey.create(Registries.DENSITY_FUNCTION,
                    Identifier.fromNamespaceAndPath(Constants.MOD_ID, "overworld_no_noise_caves_final_density"));

    @Inject(method = "<init>", at = @At("TAIL"))
    private void schworlium$disableOverworldNoiseCaves(CallbackInfo ci,
                                                       @Local(argsOnly = true) ServerLevel level,
                                                       @Local(argsOnly = true) ChunkGenerator generator) {
        if (!level.dimension().equals(Level.OVERWORLD)
                || !(generator instanceof NoiseBasedChunkGenerator noiseGenerator)
                || !noiseGenerator.generatorSettings().is(NoiseGeneratorSettings.OVERWORLD)) {
            return;
        }
        RegistryAccess registryAccess = level.registryAccess();
        DensityFunction replacement;
        if (TectonicCompat.isTectonicOverworld(registryAccess)) {
            replacement = TectonicCompat.buildStrippedFinalDensity(registryAccess);
            if (replacement == null) {
                Constants.LOG.warn("Leaving Tectonic's overworld noise router untouched; its noise caves will remain");
                return;
            }
            Constants.LOG.info("Disabled noise caves in Tectonic's overworld noise router "
                    + "(kept terrain, underground rivers and lava tunnels)");
        } else {
            replacement = registryAccess
                    .lookupOrThrow(Registries.DENSITY_FUNCTION)
                    .getValueOrThrow(SCHWORLIUM$NO_NOISE_CAVES_FINAL_DENSITY);
            Constants.LOG.info("Disabled vanilla noise caves in the overworld noise router");
        }
        ((FinalDensityOverride) generator).schworlium$setFinalDensity(replacement);
    }
}
