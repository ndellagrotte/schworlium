package com.denizen.schworlium;

import com.denizen.schworlium.util.WorldSeedHolder;
import com.denizen.schworlium.worldgen.SchworliumCarvers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.GenerationStep;

public class SchworliumFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        SchworliumCommon.init();
        SchworliumCarvers.bootstrap();

        BiomeModifications.create(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "carvers"))
                .add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInOverworld(), ctx -> {
                    var gs = ctx.getGenerationSettings();
                    gs.removeCarver(Carvers.CAVE);
                    gs.removeCarver(Carvers.CAVE_EXTRA_UNDERGROUND);
                    gs.removeCarver(Carvers.CANYON);
                    gs.addCarver(GenerationStep.Carving.AIR, SchworliumCarvers.CONFIGURED_WORLEY_CAVE);
                });

        // ServerWorldEvents.LOAD fires inside MinecraftServer.createLevels, before spawn chunks are
        // prepared. SERVER_STARTED fires after prepareLevels, by which point the carver has already
        // initialized with its fallback seed. Mirrors NeoForge's LevelEvent.Load / Unload.
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.dimension() == Level.OVERWORLD) {
                WorldSeedHolder.set(world.getSeed());
            }
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            if (world.dimension() == Level.OVERWORLD) {
                WorldSeedHolder.clear();
            }
        });
    }
}
