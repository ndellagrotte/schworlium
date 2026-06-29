package com.denizen.schworlium;

import com.denizen.schworlium.config.SchworliumConfig;
import com.denizen.schworlium.platform.Services;

public final class SchworliumCommon {

    private SchworliumCommon() {}

    // Loader-agnostic init: load config + log. Carver registration into
    // BuiltInRegistries.CARVER is loader-specific because NeoForge freezes
    // its registries before mod constructors run and requires RegisterEvent,
    // whereas Fabric allows direct Registry.register during onInitialize.
    public static void init() {
        SchworliumConfig.load();
        Constants.LOG.info("Schworlium initialized on {} ({})",
                Services.PLATFORM.getPlatformName(),
                Services.PLATFORM.getEnvironmentName());
    }
}
