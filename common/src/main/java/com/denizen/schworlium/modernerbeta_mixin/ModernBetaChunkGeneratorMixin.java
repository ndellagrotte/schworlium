package com.denizen.schworlium.modernerbeta_mixin;

import com.denizen.schworlium.config.SchworliumConfig;
import com.denizen.schworlium.worldgen.SchworliumCarvers;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Moderner-Beta compat: replace the carver list Moderner-Beta pulls from each biome's generation settings with
 * schworlium's worley carver only.
 *
 * <p>Moderner-Beta's {@code ModernBetaChunkGenerator.applyCarvers} is a full override (no {@code super}) that still
 * runs the standard vanilla carve loop: for each nearby chunk it fetches {@code genSettings.getCarvers()} and calls
 * {@code configuredCarver.carve(...)} with a proper carving context/mask/aquifer/random. By substituting that fetched
 * list we (a) get worley caves through Moderner-Beta's own carve pipeline, and (b) drop Moderner-Beta's own
 * beta_cave/beta_cave_deep/beta_canyon carvers plus any vanilla carvers, since they are no longer in the list. This is
 * loader-symmetric and does not depend on biome tags or schworlium's biome modifiers (Moderner-Beta's {@code
 * moderner_beta:beta_*} biomes are not tagged {@code #minecraft:is_overworld}, so those never reach them).
 *
 * <p>Note: this substitutes carvers for every biome routed through {@code ModernBetaChunkGenerator}. If Moderner-Beta
 * ever drives a non-overworld dimension through this generator, worley would run there too using the overworld seed —
 * acceptable for this compat.
 */
@Mixin(targets = "mod.bluestaggo.modernerbeta.level.chunk.ModernBetaChunkGenerator", remap = false)
public abstract class ModernBetaChunkGeneratorMixin {

    @ModifyExpressionValue(
        method = "applyCarvers",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/BiomeGenerationSettings;getCarvers()Ljava/lang/Iterable;"
        )
    )
    private Iterable<Holder<ConfiguredWorldCarver<?>>> schworlium$forceWorleyOnly(
            Iterable<Holder<ConfiguredWorldCarver<?>>> original,
            @Local(argsOnly = true) WorldGenRegion chunkRegion) {
        if (!SchworliumConfig.modernerBetaCompat) return original;
        return chunkRegion.registryAccess()
                .lookupOrThrow(Registries.CONFIGURED_CARVER)
                .get(SchworliumCarvers.CONFIGURED_WORLEY_CAVE)
                .<Iterable<Holder<ConfiguredWorldCarver<?>>>>map(List::of)
                .orElse(original);
    }
}
