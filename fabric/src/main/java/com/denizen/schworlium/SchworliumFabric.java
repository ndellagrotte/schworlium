package com.denizen.schworlium;

import com.denizen.schworlium.util.WorldSeedHolder;
import com.denizen.schworlium.worldgen.SchworliumCarvers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.resources.Identifier;

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
                    gs.addCarver(SchworliumCarvers.CONFIGURED_WORLEY_CAVE);
                });

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                WorldSeedHolder.set(server.overworld().getSeed()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WorldSeedHolder.clear());
    }
}
