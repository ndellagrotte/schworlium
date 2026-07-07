package com.denizen.schworlium.modernerbeta_mixin;

import com.denizen.schworlium.config.SchworliumConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Moderner-Beta compat: suppress Moderner-Beta's density-function ("noise") caves. Its {@code ChunkProviderNoise}
 * constructor adds the {@code NOISE_CAVES} post-processor when {@code CaveGeneration.useNoiseCaves()} is true (e.g. the
 * MODERN_BETA preset). schworlium's own {@code ChunkMapMixin} does not touch these because Moderner-Beta's generator
 * has no registry-keyed noise settings, so we force the flag off here.
 */
@Mixin(targets = "mod.bluestaggo.modernerbeta.api.level.chunk.ChunkProviderNoise", remap = false)
public abstract class ChunkProviderNoiseMixin {

    @ModifyExpressionValue(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lmod/bluestaggo/modernerbeta/settings/component/CaveGeneration;useNoiseCaves()Z"
        )
    )
    private boolean schworlium$forceNoNoiseCaves(boolean original) {
        return SchworliumConfig.modernerBetaCompat ? false : original;
    }
}
