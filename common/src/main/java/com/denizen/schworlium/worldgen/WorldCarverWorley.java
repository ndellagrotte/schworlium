package com.denizen.schworlium.worldgen;

import com.denizen.schworlium.config.SchworliumConfig;
import com.denizen.schworlium.util.WorldSeedHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.WorldCarver;

import java.util.function.Function;

/*
 * Adapted from fluke.worleycaves.world.WorldCarverWorley (MIT, SuperFluke) to
 * the Minecraft 26.2 carver API. Caves span world Y -64 to 128. Logic that
 * depended on the pre-1.18 SurfaceBuilder API (top/filler block restoration in
 * digBlock) has been dropped because that API no longer exists; vanilla
 * carvers in 1.21+ do not perform that restoration either.
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
public class WorldCarverWorley extends WorldCarver<CaveCarverConfiguration> {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState RED_SAND = Blocks.RED_SAND.defaultBlockState();
    private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
    private static final BlockState RED_SANDSTONE = Blocks.RED_SANDSTONE.defaultBlockState();

    private static final int CAVE_TOP = 128;
    private static final int CAVE_BOTTOM = -64;
    // Lava fills the 8 blocks above bedrock (worldY in [-63, -56]; -64 is bedrock and not carved).
    private static final int LAVA_TOP = CAVE_BOTTOM + 8;
    private static final boolean ADDITIONAL_WATER_CHECKS = false;
    private static final int SEA_LEVEL = 63;
    // Floor softening — below this Y the cutoff is biased back toward solid so caves taper out
    // before slicing into bedrock. Matches Worlium's MIN_CAVE_HEIGHT + 5 constant.
    private static final int FLOOR_SOFTEN_TOP = CAVE_BOTTOM + 5;
    private static final float FLOOR_SOFTEN_PER_BLOCK = 0.05f;

    private WorleyUtil worleyF1divF3;
    private FastNoiseLite displacementNoisePerlin;
    private volatile boolean initialized = false;

    private BlockState lavaBlock = Blocks.LAVA.defaultBlockState();
    private float noiseCutoff = -0.18f;
    private float warpAmplifier = 8.0f;
    private float easeInDepth = 15f;
    private float yCompression = 2.0f;
    private float xzCompression = 1.0f;
    private float surfaceCutoff = -0.081f;

    public WorldCarverWorley() {
        super(CaveCarverConfiguration.CODEC);
    }

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
        lavaBlock = SchworliumConfig.resolveLavaBlock();

        initialized = true;
    }

    private void ensureInitialized() {
        if (initialized) return;
        long seed = WorldSeedHolder.HAS_SEED ? WorldSeedHolder.SEED : 1337L;
        init(seed);
    }

    @Override
    public boolean isStartChunk(CaveCarverConfiguration config, RandomSource random) {
        return true;
    }

    @Override
    public boolean carve(CarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk,
                         Function<BlockPos, Holder<Biome>> biomeAccessor, RandomSource random,
                         Aquifer aquifer, ChunkPos chunkPos, CarvingMask carvingMask) {
        ensureInitialized();
        if (!chunk.getPos().equals(chunkPos)) {
            return false;
        }
        carveWorleyCaves(chunk, config, chunkPos);
        return true;
    }

    @Override
    protected boolean canReplaceBlock(CaveCarverConfiguration config, BlockState state) {
        return super.canReplaceBlock(config, state);
    }

    private void carveWorleyCaves(ChunkAccess chunk, CaveCarverConfiguration config, ChunkPos chunkPos) {
        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int chunkMaxHeight = Math.min(getMaxSurfaceHeight(chunk), CAVE_TOP);
        if (chunkMaxHeight <= CAVE_BOTTOM) return;

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

        float dispDenom = CAVE_TOP * 0.85f;
        float surfaceStart = CAVE_TOP - easeInDepth;

        for (int localX = 0; localX < 16; localX++) {
            int worldX = chunkMinX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = chunkMinZ + localZ;
                int depth = 0;

                // The Perlin warp depends only on (x, z); precompute the base sample once per
                // column and multiply by the per-Y dispAmp inside the loop. Same math, ~3x fewer
                // Perlin lookups than computing fresh for every block.
                float warpBaseX = displacementNoisePerlin.GetNoise(worldX, worldZ);
                float warpBaseY = displacementNoisePerlin.GetNoise(worldX, worldZ + 67.0f);
                float warpBaseZ = displacementNoisePerlin.GetNoise(worldX, worldZ + 149.0f);

                for (int worldY = chunkMaxHeight; worldY > CAVE_BOTTOM; worldY--) {
                    worldPos.set(worldX, worldY, worldZ);
                    BlockState currentBlock = chunk.getBlockState(worldPos);

                    // Depth tracks blocks descended since the column's first replaceable block.
                    // Used by the fluid-adjacency safety check below.
                    if (depth == 0) {
                        if (!canReplaceBlock(config, currentBlock)) continue;
                        depth = 1;
                    } else {
                        depth++;
                    }

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

                    if (noise <= adjustedNoiseCutoff) continue;

                    abovePos.set(worldX, worldY + 1, worldZ);
                    BlockState aboveBlock = chunk.getBlockState(abovePos);
                    if (aboveBlock == null) aboveBlock = AIR;

                    if (isFluidBlock(aboveBlock) && worldY > LAVA_TOP) continue;

                    if ((depth < easeInDepth || worldY > (SEA_LEVEL - 8) || ADDITIONAL_WATER_CHECKS)
                            && worldY > LAVA_TOP) {
                        if (localX < 15
                                && isFluidBlock(chunk.getBlockState(neighborPos.set(worldX + 1, worldY, worldZ))))
                            continue;
                        if (localX > 0
                                && isFluidBlock(chunk.getBlockState(neighborPos.set(worldX - 1, worldY, worldZ))))
                            continue;
                        if (localZ < 15
                                && isFluidBlock(chunk.getBlockState(neighborPos.set(worldX, worldY, worldZ + 1))))
                            continue;
                        if (localZ > 0
                                && isFluidBlock(chunk.getBlockState(neighborPos.set(worldX, worldY, worldZ - 1))))
                            continue;
                    }

                    if (canReplaceBlock(config, currentBlock)) {
                        digBlock(chunk, worldPos, worldY, aboveBlock);
                    }
                }
            }
        }
    }

    // 6-point hexagon sample of the surface heightmap, matching the original carver.
    private int getMaxSurfaceHeight(ChunkAccess chunk) {
        int max = CAVE_BOTTOM;
        int[][] testCoords = {{2, 6}, {3, 11}, {7, 2}, {9, 13}, {12, 4}, {13, 9}};
        for (int[] c : testCoords) {
            int h = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, c[0], c[1]);
            if (h > max) {
                max = h;
                if (max > CAVE_TOP) return max;
            }
        }
        return max;
    }

    private static boolean isFluidBlock(BlockState state) {
        return state != null && !state.getFluidState().isEmpty();
    }

    private void digBlock(ChunkAccess chunk, BlockPos pos, int worldY, BlockState aboveBlock) {
        if (worldY <= LAVA_TOP) {
            chunk.setBlockState(pos, lavaBlock, 0);
            return;
        }

        chunk.setBlockState(pos, AIR, 0);

        if (aboveBlock != null) {
            if (aboveBlock.is(SAND.getBlock())) {
                chunk.setBlockState(pos.above(), SANDSTONE, 0);
            } else if (aboveBlock.is(RED_SAND.getBlock())) {
                chunk.setBlockState(pos.above(), RED_SANDSTONE, 0);
            }
        }
    }
}
