package com.denizen.schworlium;

import com.denizen.schworlium.util.WorldSeedHolder;
import com.denizen.schworlium.worldgen.SchworliumCarvers;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(Constants.MOD_ID)
public class SchworliumNeoForge {

    public SchworliumNeoForge(IEventBus eventBus) {
        SchworliumCommon.init();

        eventBus.addListener(RegisterEvent.class, SchworliumNeoForge::onRegister);

        NeoForge.EVENT_BUS.addListener(SchworliumNeoForge::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(SchworliumNeoForge::onLevelUnload);
    }

    private static void onRegister(RegisterEvent event) {
        if (Registries.CARVER.equals(event.getRegistryKey())) {
            SchworliumCarvers.bootstrap();
        }
    }

    private static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            WorldSeedHolder.set(level.getSeed());
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            WorldSeedHolder.clear();
        }
    }
}
