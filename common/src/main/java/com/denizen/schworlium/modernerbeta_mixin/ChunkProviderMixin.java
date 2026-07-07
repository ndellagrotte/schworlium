package com.denizen.schworlium.modernerbeta_mixin;

import com.denizen.schworlium.config.SchworliumConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Moderner-Beta compat: force {@code CaveGeneration.useCarvers()} to true so the carve pass runs even under presets
 * that disable carvers (e.g. the cave-DISABLED preset). Moderner-Beta's {@code ChunkProvider} constructor computes
 * {@code skipCarvers = !useCarvers()}; keeping carvers on guarantees {@code applyCarvers} runs and thus schworlium's
 * worley substitution ({@link ModernBetaChunkGeneratorMixin}) executes. This is the inverse of Worlium, which forces
 * carvers off because it carves imperatively; schworlium needs the carve pipeline to run its worley WorldCarver.
 */
@Mixin(targets = "mod.bluestaggo.modernerbeta.api.level.chunk.ChunkProvider", remap = false)
public abstract class ChunkProviderMixin {

    @ModifyExpressionValue(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lmod/bluestaggo/modernerbeta/settings/component/CaveGeneration;useCarvers()Z"
        )
    )
    private boolean schworlium$forceUseCarvers(boolean original) {
        return SchworliumConfig.modernerBetaCompat ? true : original;
    }
}
