package com.denizen.schworlium.config;

import com.denizen.schworlium.Constants;
import com.denizen.schworlium.platform.Services;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SchworliumConfig {

    public static int easeInDepth = 15;
    public static String lavaBlock = "minecraft:lava";
    public static double noiseCutoffValue = -0.18;
    public static double surfaceCutoffValue = -0.081;
    public static double verticalCompressionMultiplier = 2.0;
    public static double horizonalCompressionMultiplier = 1.0;
    public static double warpAmplifier = 8.0;
    public static boolean modernerBetaCompat = true;
    public static boolean regenerateConfig = true;

    private static boolean loaded = false;

    private SchworliumConfig() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;

        Path file = Services.PLATFORM.getConfigDirectory().resolve("schworlium.json");
        if (!Files.exists(file)) {
            writeDefaults(file);
            Constants.LOG.info("Wrote default config to {}", file);
            return;
        }

        JsonObject obj = null;
        try (var reader = Files.newBufferedReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonObject()) {
                obj = root.getAsJsonObject();
            } else {
                Constants.LOG.warn("schworlium.json root is not an object; regenerating");
            }
        } catch (Exception e) {
            Constants.LOG.warn("Failed to parse schworlium.json; regenerating", e);
        }

        // When regenerate_config is enabled (the default), delete the existing file and write a fresh one from
        // defaults on every mod init — this is how new config options show up after a mod update. Set it to false
        // to preserve manual edits. A missing or corrupt object also regenerates.
        if (obj == null || readBoolean(obj, "regenerate_config", regenerateConfig)) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                Constants.LOG.warn("Failed to delete schworlium.json for regeneration", e);
            }
            writeDefaults(file);
            Constants.LOG.info("Regenerated schworlium.json (set \"regenerate_config\": false to keep manual edits)");
            return;
        }

        // regenerate_config == false: honor the on-disk values.
        regenerateConfig = readBoolean(obj, "regenerate_config", regenerateConfig);
        easeInDepth = readInt(obj, "easeInDepth", easeInDepth);
        lavaBlock = readString(obj, "lavaBlock", lavaBlock);
        noiseCutoffValue = readDouble(obj, "noiseCutoffValue", noiseCutoffValue);
        surfaceCutoffValue = readDouble(obj, "surfaceCutoffValue", surfaceCutoffValue);
        verticalCompressionMultiplier = readDouble(obj, "verticalCompressionMultiplier", verticalCompressionMultiplier);
        horizonalCompressionMultiplier = readDouble(obj, "horizonalCompressionMultiplier", horizonalCompressionMultiplier);
        warpAmplifier = readDouble(obj, "warpAmplifier", warpAmplifier);
        modernerBetaCompat = readBoolean(obj, "modernerBetaCompat", modernerBetaCompat);
    }

    public static BlockState resolveLavaBlock() {
        Identifier id = Identifier.tryParse(lavaBlock);
        if (id == null) {
            Constants.LOG.warn("Invalid lavaBlock id: {}; falling back to minecraft:lava", lavaBlock);
            return Blocks.LAVA.defaultBlockState();
        }
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (block == Blocks.AIR) {
            Constants.LOG.warn("Unknown lavaBlock id: {}; falling back to minecraft:lava", lavaBlock);
            return Blocks.LAVA.defaultBlockState();
        }
        return block.defaultBlockState();
    }

    private static void writeDefaults(Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("regenerate_config", regenerateConfig);
            obj.addProperty("easeInDepth", easeInDepth);
            obj.addProperty("lavaBlock", lavaBlock);
            obj.addProperty("noiseCutoffValue", noiseCutoffValue);
            obj.addProperty("surfaceCutoffValue", surfaceCutoffValue);
            obj.addProperty("verticalCompressionMultiplier", verticalCompressionMultiplier);
            obj.addProperty("horizonalCompressionMultiplier", horizonalCompressionMultiplier);
            obj.addProperty("warpAmplifier", warpAmplifier);
            obj.addProperty("modernerBetaCompat", modernerBetaCompat);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file, gson.toJson(obj));
        } catch (IOException e) {
            Constants.LOG.warn("Failed to write default schworlium.json", e);
        }
    }

    private static int readInt(JsonObject o, String key, int defaultValue) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : defaultValue;
    }

    private static double readDouble(JsonObject o, String key, double defaultValue) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : defaultValue;
    }

    private static String readString(JsonObject o, String key, String defaultValue) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : defaultValue;
    }

    private static boolean readBoolean(JsonObject o, String key, boolean defaultValue) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : defaultValue;
    }
}
