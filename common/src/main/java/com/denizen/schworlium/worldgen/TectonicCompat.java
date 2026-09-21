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
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import org.jetbrains.annotations.Nullable;

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
    private static final Identifier TECTONIC_SLOPED_CHEESE =
            Identifier.fromNamespaceAndPath(TECTONIC_MOD_ID, "sloped_cheese");

    /*
     * Tectonic 3.0.29's overworld final_density (its override of data/minecraft/worldgen/density_function/
     * overworld/final_density.json) with the cave-noise terms stripped: min(sloped_cheese, caves) -> sloped_cheese,
     * and the outer min(<squeeze chain>, cave/noodle) -> <squeeze chain>. The rivers + lava-tunnel term and the
     * beardifier are kept verbatim. Only vanilla density-function types are used; tectonic:* appear solely as id
     * references.
     */
    private static final String STRIPPED_FINAL_DENSITY_JSON = """
            {
              "type": "minecraft:add",
              "left": {
                "type": "minecraft:add",
                "left": {
                  "type": "minecraft:squeeze",
                  "input": {
                    "type": "minecraft:interpolated",
                    "cell_size_xz": 4,
                    "cell_size_y": 8,
                    "input": {
                      "type": "minecraft:mul",
                      "left": 0.64,
                      "right": {
                        "type": "minecraft:blend_density",
                        "input": {
                          "type": "minecraft:lerp",
                          "alpha": "tectonic:__constants/slope_lower",
                          "first": 0.1,
                          "second": {
                            "type": "minecraft:lerp",
                            "alpha": "tectonic:__constants/slope_upper",
                            "first": -0.1,
                            "second": "tectonic:sloped_cheese"
                          }
                        }
                      }
                    }
                  }
                },
                "right": {
                  "type": "minecraft:add",
                  "left": {
                    "type": "minecraft:min",
                    "left": 0.0002,
                    "right": "tectonic:underground_river/total"
                  },
                  "right": "tectonic:lava_tunnel/total"
                }
              },
              "right": {
                "type": "minecraft:beardifier"
              }
            }
            """;

    private TectonicCompat() {}

    /** True when Tectonic's overworld worldgen is loaded (its density functions are in the registry). */
    public static boolean isTectonicOverworld(RegistryAccess registryAccess) {
        return registryAccess.lookupOrThrow(Registries.DENSITY_FUNCTION).containsKey(TECTONIC_SLOPED_CHEESE);
    }

    /**
     * Decodes the Tectonic-shaped, cave-stripped final_density against the live registry. Only call when
     * {@link #isTectonicOverworld(RegistryAccess)} is true, so the tectonic:* references resolve.
     *
     * @return the decoded function, or {@code null} if Tectonic's density-function layout no longer matches
     *         (in which case the caller should leave Tectonic's own router alone rather than clobber it).
     */
    public static @Nullable DensityFunction buildStrippedFinalDensity(RegistryAccess registryAccess) {
        JsonElement json = JsonParser.parseString(STRIPPED_FINAL_DENSITY_JSON);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
        DataResult<DensityFunction> parsed = DensityFunction.CODEC.parse(ops, json);
        parsed.error().ifPresent(err ->
                Constants.LOG.error("Failed to decode Tectonic-compatible final_density: {}", err.message()));
        return parsed.result().orElse(null);
    }
}
