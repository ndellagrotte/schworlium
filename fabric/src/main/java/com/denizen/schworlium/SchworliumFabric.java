package com.denizen.schworlium;

import com.denizen.schworlium.util.WorldSeedHolder;
import com.denizen.schworlium.worldgen.SchworliumCarvers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public class SchworliumFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        SchworliumCommon.init();
        SchworliumCarvers.bootstrap();

        BiomeModifications.create(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "carvers"))
                .add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInOverworld(), ctx -> {
                    var gs = ctx.getGenerationSettings();
                    gs.removeCarver(Carvers.CAVE);
                    gs.removeCarver(Carvers.CAVE_EXTRA_UNDERGROUND);
                    gs.removeCarver(Carvers.CANYON);
                    gs.addCarver(SchworliumCarvers.WORLEY_CAVE);
                });

        // Capture the seed when the overworld is loaded, before any chunk generates. SERVER_STARTED
        // fires after spawn chunks are generated, so the carver would already be seeded with its fallback.
        ServerLevelEvents.LOAD.register((server, level) -> {
            if (level.dimension() == Level.OVERWORLD) {
                WorldSeedHolder.set(level.getSeed());
            }
        });
        ServerLevelEvents.UNLOAD.register((server, level) -> {
            if (level.dimension() == Level.OVERWORLD) {
                WorldSeedHolder.clear();
            }
        });
    }
}
