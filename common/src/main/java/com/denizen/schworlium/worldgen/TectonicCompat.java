package com.denizen.schworlium.worldgen;

import com.denizen.schworlium.Constants;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.DensityFunction;

/*
 * Compatibility bridge for the Tectonic worldgen mod.
 *
 * Tectonic overrides the minecraft:overworld noise settings in place (same registry id) and rewrites
 * the whole noise router, including final_density, which folds in its own terrain, caves, underground
 * rivers and lava tunnels. Schworlium's ChunkMapMixin gates on the minecraft:overworld settings *key*,
 * so it still fires under Tectonic — but its vanilla-shaped replacement final_density (built on
 * minecraft:overworld/sloped_cheese) does not match Tectonic's terrain math and would clobber it.
 *
 * When Tectonic's worldgen is actually loaded we instead swap in a Tectonic-shaped final_density that
 * keeps Tectonic's terrain, underground rivers and lava tunnels, and removes only the cave noise
 * (cheese/spaghetti + noodle), leaving Schworlium's Worley carver as the sole cave source underground.
 *
 * The replacement is decoded on demand from a bundled JSON string against the live registry rather than
 * shipped as a datapack density_function. That way its tectonic:* references are only ever resolved when
 * Tectonic's datapack is active; a missing or disabled Tectonic simply fails the probe and we fall back
 * to the vanilla replacement, with no risk of an unbound-registry crash.
 */
public final class TectonicCompat {

    public static final String TECTONIC_MOD_ID = "tectonic";

    /** Present in the DENSITY_FUNCTION registry iff Tectonic's overworld worldgen datapack is active. */
    private static final Identifier TECTONIC_BASE_TERRAIN =
            Identifier.fromNamespaceAndPath(TECTONIC_MOD_ID, "base_terrain");

    /*
     * Tectonic's overworld final_density (data/minecraft/worldgen/density_function/overworld/noise_router/
     * final_density.json) with the cave-noise terms stripped: min(base_terrain, caves) -> base_terrain, and
     * the outer min(<slope chain>, cave/noodle) -> <slope chain>. The rivers + lava-tunnel term is kept
     * verbatim. Only vanilla density-function types are used; tectonic:* appear solely as id references.
     */
    private static final String STRIPPED_FINAL_DENSITY_JSON = """
            {
              "type": "minecraft:add",
              "argument1": {
                "type": "minecraft:squeeze",
                "argument": {
                  "type": "minecraft:mul",
                  "argument1": 0.64,
                  "argument2": {
                    "type": "minecraft:interpolated",
                    "argument": {
                      "type": "minecraft:blend_density",
                      "argument": {
                        "type": "minecraft:add",
                        "argument1": 0.1,
                        "argument2": {
                          "type": "minecraft:mul",
                          "argument1": "tectonic:__constants/slope_lower",
                          "argument2": {
                            "type": "minecraft:add",
                            "argument1": -0.1,
                            "argument2": {
                              "type": "minecraft:add",
                              "argument1": -1,
                              "argument2": {
                                "type": "minecraft:mul",
                                "argument1": "tectonic:__constants/slope_upper",
                                "argument2": {
                                  "type": "minecraft:add",
                                  "argument1": 1,
                                  "argument2": "tectonic:base_terrain"
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              },
              "argument2": {
                "type": "minecraft:add",
                "argument1": {
                  "type": "minecraft:min",
                  "argument1": 0.0002,
                  "argument2": "tectonic:underground_river/total"
                },
                "argument2": "tectonic:lava_tunnel/total"
              }
            }
            """;

    private TectonicCompat() {}

    /** True when Tectonic's overworld worldgen is loaded (its density functions are in the registry). */
    public static boolean isTectonicOverworld(RegistryAccess registryAccess) {
        return registryAccess.lookupOrThrow(Registries.DENSITY_FUNCTION).containsKey(TECTONIC_BASE_TERRAIN);
    }

    /**
     * Decodes the Tectonic-shaped, cave-stripped final_density against the live registry. Only call when
     * {@link #isTectonicOverworld(RegistryAccess)} is true, so the tectonic:* re/ferences resolve.
     */
    public static DensityFunction buildStrippedFinalDensity(RegistryAccess registryAccess) {
        JsonElement json = JsonParser.parseString(STRIPPED_FINAL_DENSITY_JSON);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
        DataResult<DensityFunction> parsed = DensityFunction.DIRECT_CODEC.parse(ops, json);
        parsed.error().ifPresent(err ->
                Constants.LOG.error("Failed to decode Tectonic-compatible final_density: {}", err.message()));
        return parsed.result().orElseThrow(() ->
                new IllegalStateException("Failed to decode Tectonic-compatible final_density"));
    }
}
