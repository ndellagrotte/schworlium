package com.denizen.schworlium.mixin;

import com.denizen.schworlium.worldgen.FinalDensityOverride;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/*
 * Carries the final_density replacement chosen in ChunkMapMixin. Both places the generator reads
 * final_density from its noise settings (terrain fill and the base-height column sampler used for
 * structure placement) are redirected to the override so the two stay consistent.
 *
 * RandomState caches compiled samplers per DensityFunction instance, so returning the same stored
 * object every time compiles it exactly once.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin implements FinalDensityOverride {

    @Unique
    private volatile @Nullable DensityFunction schworlium$finalDensity;

    @Override
    public void schworlium$setFinalDensity(DensityFunction finalDensity) {
        this.schworlium$finalDensity = finalDensity;
    }

    @ModifyExpressionValue(
            method = {"doFill", "iterateNoiseColumn"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;finalDensity()Lnet/minecraft/world/level/levelgen/densityfunction/DensityFunction;"
            )
    )
    private DensityFunction schworlium$overrideFinalDensity(DensityFunction original) {
        DensityFunction override = this.schworlium$finalDensity;
        return override != null ? override : original;
    }
}
