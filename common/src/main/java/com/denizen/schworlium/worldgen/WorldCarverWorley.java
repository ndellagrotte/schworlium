package com.denizen.schworlium.worldgen;

import com.denizen.schworlium.config.SchworliumConfig;
import com.denizen.schworlium.util.WorldSeedHolder;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarverOutput;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;

/*
 * Adapted from fluke.worleycaves.world.WorldCarverWorley (MIT, SuperFluke) to
 * the Minecraft 26.3 carver API. Caves span world Y -64 to 128.
 *
 * Since 26.3 a carver never touches the chunk: it only marks positions in a
 * CarverOutput bit-mask, and NoiseBasedChunkGenerator.applyCarvingMask later
 * resolves every marked block through the aquifer (air, water or lava), skips
 * #minecraft:uncarvable blocks and repairs the surface block above grass. The
 * pre-26.3 per-block logic (fluid-adjacency skips, lava floor below Y -56,
 * sand -> sandstone) is therefore gone; vanilla carvers in 26.3 do not do it
 * either. This carver is pure noise -> mask.
 *
 * Cave-shape evaluation samples the Worley noise PER BLOCK rather than on a
 * coarse 5x5x97 grid with linear interpolation (as the original WorleyCaves
 * does). The vanilla-era lerp trick was a cost-saving measure that has the
 * visible side effect of facetting the noise field along the grid corners:
 * Worley noise changes sharply at cell boundaries, so a 4-block linear
 * interpolation produces planar gradients that the threshold cut turns into
 * staircase edges along the chunk subdivisions. Per-block sampling matches
 * the smoothness the sister Worlium mod gets by running the same noise
 * function inside Minecraft's density-function pipeline.
 *
 * The warp-amplitude and threshold-easing formulas are ported verbatim from
 * Worlium so cave shapes match block-for-block for a given seed.
 */
public class WorldCarverWorley implements WorldCarver {

    /** Registered as the schworlium:worley_cave carver type; the carver has no data-driven fields. */
    public static final MapCodec<WorldCarverWorley> CODEC = MapCodec.unit(WorldCarverWorley::new);

    private static final int CAVE_TOP = 128;
    private static final int CAVE_BOTTOM = -64;
    // Floor softening — below this Y the cutoff is biased back toward solid so caves taper out
    // before slicing into bedrock. Matches Worlium's MIN_CAVE_HEIGHT + 5 constant.
    private static final int FLOOR_SOFTEN_TOP = CAVE_BOTTOM + 5;
    private static final float FLOOR_SOFTEN_PER_BLOCK = 0.05f;

    private WorleyUtil worleyF1divF3;
    private FastNoiseLite displacementNoisePerlin;
    private volatile boolean initialized = false;

    private float noiseCutoff = -0.18f;
    private float warpAmplifier = 8.0f;
    private float easeInDepth = 15f;
    private float yCompression = 2.0f;
    private float xzCompression = 1.0f;
    private float surfaceCutoff = -0.081f;

    public WorldCarverWorley() {}

    public synchronized void init(long worldSeed) {
        worleyF1divF3 = new WorleyUtil((int) worldSeed);
        worleyF1divF3.SetFrequency(0.016f);

        displacementNoisePerlin = new FastNoiseLite();
        displacementNoisePerlin.SetSeed((int) worldSeed);
        displacementNoisePerlin.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        displacementNoisePerlin.SetFrequency(0.05f);

        noiseCutoff = (float) SchworliumConfig.noiseCutoffValue;
        warpAmplifier = (float) SchworliumConfig.warpAmplifier;
        easeInDepth = (float) SchworliumConfig.easeInDepth;
        yCompression = (float) SchworliumConfig.verticalCompressionMultiplier;
        xzCompression = (float) SchworliumConfig.horizonalCompressionMultiplier;
        surfaceCutoff = (float) SchworliumConfig.surfaceCutoffValue;

        initialized = true;
    }

    private void ensureInitialized() {
        if (initialized) return;
        long seed = WorldSeedHolder.HAS_SEED ? WorldSeedHolder.SEED : 1337L;
        init(seed);
    }

    @Override
    public MapCodec<WorldCarverWorley> codec() {
        return CODEC;
    }

    @Override
    public boolean isStartChunk(RandomSource random) {
        return true;
    }

    @Override
    public boolean carve(WorldGenerationContext context, RandomSource random, ChunkPos chunkPos,
                         ChunkPos sourceChunkPos, CarverOutput output) {
        // The generator invokes every carver once per source chunk in a 17x17 window around the
        // chunk being carved. Worley noise is a pure function of world position, so only the
        // chunk's own invocation does any work.
        if (!chunkPos.equals(sourceChunkPos)) {
            return false;
        }
        ensureInitialized();
        carveWorleyCaves(chunkPos, output);
        return true;
    }

    private void carveWorleyCaves(ChunkPos chunkPos, CarverOutput output) {
        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int topY = Math.min(CAVE_TOP, output.maxY());
        // CAVE_BOTTOM itself is bedrock and never carved.
        int bottomY = Math.max(CAVE_BOTTOM + 1, output.minY());
        if (topY < bottomY) return;

        float dispDenom = CAVE_TOP * 0.85f;
        float surfaceStart = CAVE_TOP - easeInDepth;

        for (int localX = 0; localX < 16; localX++) {
            int worldX = chunkMinX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = chunkMinZ + localZ;

                // The Perlin warp depends only on (x, z); precompute the base sample once per
                // column and multiply by the per-Y dispAmp inside the loop. Same math, ~3x fewer
                // Perlin lookups than computing fresh for every block.
                float warpBaseX = displacementNoisePerlin.GetNoise(worldX, worldZ);
                float warpBaseY = displacementNoisePerlin.GetNoise(worldX, worldZ + 67.0f);
                float warpBaseZ = displacementNoisePerlin.GetNoise(worldX, worldZ + 149.0f);

                for (int worldY = topY; worldY >= bottomY; worldY--) {
                    float adjustedNoiseCutoff = noiseCutoff;
                    if (worldY > surfaceStart) {
                        float t = (worldY - surfaceStart) / easeInDepth;
                        adjustedNoiseCutoff = Mth.lerp(t, noiseCutoff, surfaceCutoff);
                    }
                    if (worldY < FLOOR_SOFTEN_TOP) {
                        adjustedNoiseCutoff += (FLOOR_SOFTEN_TOP - worldY) * FLOOR_SOFTEN_PER_BLOCK;
                    }

                    // Worlium's warp amplitude: clamped at Y=1 so the extended floor mirrors the
                    // reference's deepest warp (~9.37) rather than extrapolating past Y=1.
                    int ampY = Math.max(worldY, 1);
                    float dispAmp = warpAmplifier * ((CAVE_TOP - ampY * 0.5f) / dispDenom);

                    float noise = worleyF1divF3.SingleCellular3Edge(
                            worldX * xzCompression + warpBaseX * dispAmp,
                            worldY * yCompression + warpBaseY * dispAmp,
                            worldZ * xzCompression + warpBaseZ * dispAmp);

                    if (noise > adjustedNoiseCutoff) {
                        output.carve(localX, worldY, localZ);
                    }
                }
            }
        }
    }
}
