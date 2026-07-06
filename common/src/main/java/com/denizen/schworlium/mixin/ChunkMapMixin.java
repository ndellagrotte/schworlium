package com.denizen.schworlium.mixin;

import com.denizen.schworlium.Constants;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/*
 * Disables vanilla noise caves (cheese/spaghetti/noodle/entrances) in the overworld by
 * swapping the noise router's final_density for schworlium:overworld_no_noise_caves_final_density
 * when the overworld's RandomState is built.
 *
 * This used to be a third-party wrap_noise_router worldgen modifier, but that library
 * (through at least 1.7.12) parsed the modifier's "dimension" field without ever consulting
 * it: its ChunkMapMixin applied every wrap_noise_router modifier to every ServerLevel, which
 * leaked the overworld-only final_density replacement into the nether and end. Wrapping the
 * same operation ourselves lets us gate on the level actually being the overworld.
 *
 * Gated on the generator using the minecraft:overworld noise settings (not just the dimension
 * key) so amplified/large-biomes presets and custom overworld generators — whose terrain math
 * the replacement formula does not match — keep their vanilla routers.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Unique
    private static final ResourceKey<DensityFunction> SCHWORLIUM$NO_NOISE_CAVES_FINAL_DENSITY =
            ResourceKey.create(Registries.DENSITY_FUNCTION,
                    Identifier.fromNamespaceAndPath(Constants.MOD_ID, "overworld_no_noise_caves_final_density"));

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/core/HolderGetter;J)Lnet/minecraft/world/level/levelgen/RandomState;"
            )
    )
    private RandomState schworlium$disableOverworldNoiseCaves(NoiseGeneratorSettings settings,
                                                              HolderGetter<NormalNoise.NoiseParameters> noises,
                                                              long seed,
                                                              Operation<RandomState> original,
                                                              @Local(argsOnly = true) ServerLevel level,
                                                              @Local(argsOnly = true) ChunkGenerator generator) {
        if (level.dimension().equals(Level.OVERWORLD)
                && generator instanceof NoiseBasedChunkGenerator noiseGenerator
                && noiseGenerator.generatorSettings().is(NoiseGeneratorSettings.OVERWORLD)) {
            DensityFunction replacement = level.registryAccess()
                    .lookupOrThrow(Registries.DENSITY_FUNCTION)
                    .getValueOrThrow(SCHWORLIUM$NO_NOISE_CAVES_FINAL_DENSITY);
            settings = schworlium$withFinalDensity(settings, replacement);
            Constants.LOG.info("Disabled vanilla noise caves in the overworld noise router");
        }
        return original.call(settings, noises, seed);
    }

    @Unique
    private static NoiseGeneratorSettings schworlium$withFinalDensity(NoiseGeneratorSettings settings,
                                                                      DensityFunction finalDensity) {
        NoiseRouter router = settings.noiseRouter();
        NoiseRouter newRouter = new NoiseRouter(
                router.barrierNoise(),
                router.fluidLevelFloodednessNoise(),
                router.fluidLevelSpreadNoise(),
                router.lavaNoise(),
                router.temperature(),
                router.vegetation(),
                router.continents(),
                router.erosion(),
                router.depth(),
                router.ridges(),
                router.preliminarySurfaceLevel(),
                finalDensity,
                router.veinToggle(),
                router.veinRidged(),
                router.veinGap());
        return new NoiseGeneratorSettings(
                settings.noiseSettings(),
                settings.defaultBlock(),
                settings.defaultFluid(),
                newRouter,
                settings.surfaceRule(),
                settings.spawnTarget(),
                settings.seaLevel(),
                settings.disableMobGeneration(),
                settings.aquifersEnabled(),
                settings.oreVeinsEnabled(),
                settings.useLegacyRandomSource());
    }
}
