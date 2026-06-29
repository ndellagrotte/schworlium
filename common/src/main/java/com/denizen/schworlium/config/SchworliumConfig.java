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

    public static int lavaDepth = 10;
    public static int easeInDepth = 15;
    public static String lavaBlock = "minecraft:lava";
    public static int maxCaveHeight = 128;
    public static int minCaveHeight = 1;
    public static double noiseCutoffValue = -0.18;
    public static double surfaceCutoffValue = -0.081;
    public static double verticalCompressionMultiplier = 2.0;
    public static double horizonalCompressionMultiplier = 1.0;
    public static double warpAmplifier = 8.0;

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

        try (var reader = Files.newBufferedReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                Constants.LOG.warn("schworlium.json root is not an object; using defaults");
                return;
            }
            JsonObject obj = root.getAsJsonObject();
            lavaDepth = readInt(obj, "lavaDepth", lavaDepth);
            easeInDepth = readInt(obj, "easeInDepth", easeInDepth);
            lavaBlock = readString(obj, "lavaBlock", lavaBlock);
            maxCaveHeight = readInt(obj, "maxCaveHeight", maxCaveHeight);
            minCaveHeight = readInt(obj, "minCaveHeight", minCaveHeight);
            noiseCutoffValue = readDouble(obj, "noiseCutoffValue", noiseCutoffValue);
            surfaceCutoffValue = readDouble(obj, "surfaceCutoffValue", surfaceCutoffValue);
            verticalCompressionMultiplier = readDouble(obj, "verticalCompressionMultiplier", verticalCompressionMultiplier);
            horizonalCompressionMultiplier = readDouble(obj, "horizonalCompressionMultiplier", horizonalCompressionMultiplier);
            warpAmplifier = readDouble(obj, "warpAmplifier", warpAmplifier);
        } catch (Exception e) {
            Constants.LOG.warn("Failed to parse schworlium.json; using defaults", e);
        }
    }

    public static BlockState resolveLavaBlock() {
        Identifier id = Identifier.tryParse(lavaBlock);
        if (id == null) {
            Constants.LOG.warn("Invalid lavaBlock id: {}; falling back to minecraft:lava", lavaBlock);
            return Blocks.LAVA.defaultBlockState();
        }
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (block == null || block == Blocks.AIR) {
            Constants.LOG.warn("Unknown lavaBlock id: {}; falling back to minecraft:lava", lavaBlock);
            return Blocks.LAVA.defaultBlockState();
        }
        return block.defaultBlockState();
    }

    private static void writeDefaults(Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("lavaDepth", lavaDepth);
            obj.addProperty("easeInDepth", easeInDepth);
            obj.addProperty("lavaBlock", lavaBlock);
            obj.addProperty("maxCaveHeight", maxCaveHeight);
            obj.addProperty("minCaveHeight", minCaveHeight);
            obj.addProperty("noiseCutoffValue", noiseCutoffValue);
            obj.addProperty("surfaceCutoffValue", surfaceCutoffValue);
            obj.addProperty("verticalCompressionMultiplier", verticalCompressionMultiplier);
            obj.addProperty("horizonalCompressionMultiplier", horizonalCompressionMultiplier);
            obj.addProperty("warpAmplifier", warpAmplifier);
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
}
