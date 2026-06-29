package com.denizen.schworlium.worldgen;

import com.denizen.schworlium.config.SchworliumConfig;
import com.denizen.schworlium.util.WorldSeedHolder;
import dev.worldgen.lithostitched.api.worldgen.densityfunction.fastnoise.FNL;
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
 * Port of fluke.worleycaves.world.WorldCarverWorley (MIT, SuperFluke) to the
 * Minecraft 26.2 carver API. Algorithm, numeric constants and noise pipeline
 * are preserved verbatim; only the surrounding Minecraft API calls have been
 * updated. Logic that depended on the pre-1.18 SurfaceBuilder API (top/filler
 * block restoration in digBlock) has been dropped because that API no longer
 * exists; vanilla carvers in 1.21+ do not perform that restoration either.
 */
public class WorldCarverWorley extends WorldCarver<CaveCarverConfiguration> {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState RED_SAND = Blocks.RED_SAND.defaultBlockState();
    private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
    private static final BlockState RED_SANDSTONE = Blocks.RED_SANDSTONE.defaultBlockState();

    private static final int HAS_CAVES_FLAG = 129;
    private static final boolean ADDITIONAL_WATER_CHECKS = false;
    private static final int SEA_LEVEL = 63;
    // The 1.16.5 carver assumed bedrock at y=0; in 26.2 bedrock is at y=-64,
    // so shift the carver's world-Y outputs down by 64 to keep the cave region
    // in the same position relative to the bedrock floor. The algorithm itself
    // (sampling, depth tracking, lava check) keeps operating in the original
    // "internal Y" 0..127 space; only the BlockPos we hand to the chunk gets
    // translated. localY  -> worldY = localY + Y_OFFSET.
    private static final int Y_OFFSET = -64;

    private WorleyUtil worleyF1divF3;
    private FNL displacementNoisePerlin;
    private volatile boolean initialized = false;
    private long initSeed = 0L;

    private BlockState lavaBlock = Blocks.LAVA.defaultBlockState();
    private int maxCaveHeight = 128;
    private int minCaveHeight = 1;
    private float noiseCutoff = -0.18f;
    private float warpAmplifier = 8.0f;
    private float easeInDepth = 15f;
    private float yCompression = 2.0f;
    private float xzCompression = 1.0f;
    private float surfaceCutoff = -0.081f;
    private int lavaDepth = 10;

    public WorldCarverWorley() {
        super(CaveCarverConfiguration.CODEC);
    }

    public synchronized void init(long worldSeed) {
        worleyF1divF3 = new WorleyUtil((int) worldSeed);
        worleyF1divF3.SetFrequency(0.016f);

        displacementNoisePerlin = new FNL();
        displacementNoisePerlin.SetSeed((int) worldSeed);
        displacementNoisePerlin.SetNoiseType(FNL.NoiseType.Perlin);
        displacementNoisePerlin.SetFrequency(0.05f);

        maxCaveHeight = SchworliumConfig.maxCaveHeight;
        minCaveHeight = SchworliumConfig.minCaveHeight;
        noiseCutoff = (float) SchworliumConfig.noiseCutoffValue;
        warpAmplifier = (float) SchworliumConfig.warpAmplifier;
        easeInDepth = (float) SchworliumConfig.easeInDepth;
        yCompression = (float) SchworliumConfig.verticalCompressionMultiplier;
        xzCompression = (float) SchworliumConfig.horizonalCompressionMultiplier;
        surfaceCutoff = (float) SchworliumConfig.surfaceCutoffValue;
        lavaDepth = SchworliumConfig.lavaDepth;
        lavaBlock = SchworliumConfig.resolveLavaBlock();

        initSeed = worldSeed;
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
        int chunkX = chunkMinX >> 4;
        int chunkZ = chunkMinZ >> 4;

        int chunkMaxHeight = getMaxSurfaceHeight(chunk);
        float[][][] samples = sampleNoise(chunkX, chunkZ, chunkMaxHeight + 1);
        float oneQuarter = 0.25F;
        float oneHalf = 0.5F;
        BlockState currentBlock;
        BlockPos.MutableBlockPos localPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                int depth = 0;

                if (samples[x][HAS_CAVES_FLAG][z] == 0
                        && samples[x + 1][HAS_CAVES_FLAG][z] == 0
                        && samples[x][HAS_CAVES_FLAG][z + 1] == 0
                        && samples[x + 1][HAS_CAVES_FLAG][z + 1] == 0) {
                    continue;
                }

                for (int y = (maxCaveHeight / 2) - 1; y >= 0; y--) {
                    float x0y0z0 = samples[x][y][z];
                    float x0y0z1 = samples[x][y][z + 1];
                    float x1y0z0 = samples[x + 1][y][z];
                    float x1y0z1 = samples[x + 1][y][z + 1];
                    float x0y1z0 = samples[x][y + 1][z];
                    float x0y1z1 = samples[x][y + 1][z + 1];
                    float x1y1z0 = samples[x + 1][y + 1][z];
                    float x1y1z1 = samples[x + 1][y + 1][z + 1];

                    float noiseStepY00 = (x0y1z0 - x0y0z0) * -oneHalf;
                    float noiseStepY01 = (x0y1z1 - x0y0z1) * -oneHalf;
                    float noiseStepY10 = (x1y1z0 - x1y0z0) * -oneHalf;
                    float noiseStepY11 = (x1y1z1 - x1y0z1) * -oneHalf;

                    float noiseStartX0 = x0y0z0;
                    float noiseStartX1 = x0y0z1;
                    float noiseEndX0 = x1y0z0;
                    float noiseEndX1 = x1y0z1;

                    for (int suby = 1; suby >= 0; suby--) {
                        int localY = suby + y * 2;
                        float noiseStartZ = noiseStartX0;
                        float noiseEndZ = noiseStartX1;

                        float noiseStepX0 = (noiseEndX0 - noiseStartX0) * oneQuarter;
                        float noiseStepX1 = (noiseEndX1 - noiseStartX1) * oneQuarter;

                        for (int subx = 0; subx < 4; subx++) {
                            int localX = subx + x * 4;
                            float noiseStepZ = (noiseEndZ - noiseStartZ) * oneQuarter;
                            float noiseVal = noiseStartZ;

                            for (int subz = 0; subz < 4; subz++) {
                                int localZ = subz + z * 4;
                                currentBlock = null;
                                int worldX = chunkMinX + localX;
                                int worldZ = chunkMinZ + localZ;
                                int worldY = localY + Y_OFFSET;
                                localPos.set(worldX, worldY, worldZ);

                                if (depth == 0) {
                                    if (subx == 0 && subz == 0) {
                                        currentBlock = chunk.getBlockState(localPos);
                                        if (canReplaceBlock(config, currentBlock)) {
                                            depth++;
                                        }
                                    } else {
                                        continue;
                                    }
                                } else if (subx == 0 && subz == 0) {
                                    depth++;
                                }

                                float adjustedNoiseCutoff = noiseCutoff;
                                if (depth < easeInDepth) {
                                    adjustedNoiseCutoff = (float) Mth.clampedLerp(
                                            noiseCutoff, surfaceCutoff,
                                            (easeInDepth - (float) depth) / easeInDepth);
                                }

                                if (localY < (minCaveHeight + 5)) {
                                    adjustedNoiseCutoff += ((minCaveHeight + 5) - localY) * 0.05f;
                                }

                                if (noiseVal > adjustedNoiseCutoff) {
                                    abovePos.set(worldX, worldY + 1, worldZ);
                                    BlockState aboveBlock = chunk.getBlockState(abovePos);
                                    if (aboveBlock == null) aboveBlock = AIR;

                                    if (!isFluidBlock(aboveBlock) || localY <= lavaDepth) {
                                        if ((depth < easeInDepth || localY > (SEA_LEVEL - 8) || ADDITIONAL_WATER_CHECKS)
                                                && localY > lavaDepth) {
                                            if (localX < 15 && isFluidBlock(chunk.getBlockState(abovePos.set(worldX + 1, worldY, worldZ)))) {
                                                noiseVal += noiseStepZ;
                                                continue;
                                            }
                                            if (localX > 0 && isFluidBlock(chunk.getBlockState(abovePos.set(worldX - 1, worldY, worldZ)))) {
                                                noiseVal += noiseStepZ;
                                                continue;
                                            }
                                            if (localZ < 15 && isFluidBlock(chunk.getBlockState(abovePos.set(worldX, worldY, worldZ + 1)))) {
                                                noiseVal += noiseStepZ;
                                                continue;
                                            }
                                            if (localZ > 0 && isFluidBlock(chunk.getBlockState(abovePos.set(worldX, worldY, worldZ - 1)))) {
                                                noiseVal += noiseStepZ;
                                                continue;
                                            }
                                        }

                                        if (currentBlock == null) {
                                            currentBlock = chunk.getBlockState(localPos);
                                        }
                                        if (canReplaceBlock(config, currentBlock)) {
                                            digBlock(chunk, localPos, localY, aboveBlock);
                                        }
                                    }
                                }

                                noiseVal += noiseStepZ;
                            }

                            noiseStartZ += noiseStepX0;
                            noiseEndZ += noiseStepX1;
                        }

                        noiseStartX0 += noiseStepY00;
                        noiseStartX1 += noiseStepY01;
                        noiseEndX0 += noiseStepY10;
                        noiseEndX1 += noiseStepY11;
                    }
                }
            }
        }
    }

    private float[][][] sampleNoise(int chunkX, int chunkZ, int maxSurfaceHeight) {
        int originalMaxHeight = 128;
        float[][][] noiseSamples = new float[5][130][5];
        float noise;
        for (int x = 0; x < 5; x++) {
            int realX = x * 4 + chunkX * 16;
            for (int z = 0; z < 5; z++) {
                int realZ = z * 4 + chunkZ * 16;
                boolean columnHasCaveFlag = false;

                for (int y = 128; y >= 0; y--) {
                    float realY = y * 2;
                    if (realY > maxSurfaceHeight || realY > maxCaveHeight || realY < minCaveHeight) {
                        noiseSamples[x][y][z] = -1.1F;
                    } else {
                        float dispAmp = (float) (warpAmplifier * ((originalMaxHeight - y) / (originalMaxHeight * 0.85)));

                        float xDisp = displacementNoisePerlin.GetNoise(realX, realZ) * dispAmp;
                        float yDisp = displacementNoisePerlin.GetNoise(realX, realZ + 67.0f) * dispAmp;
                        float zDisp = displacementNoisePerlin.GetNoise(realX, realZ + 149.0f) * dispAmp;

                        noise = worleyF1divF3.SingleCellular3Edge(
                                realX * xzCompression + xDisp,
                                realY * yCompression + yDisp,
                                realZ * xzCompression + zDisp);
                        noiseSamples[x][y][z] = noise;

                        if (noise > noiseCutoff) {
                            columnHasCaveFlag = true;
                            if (x > 0) {
                                noiseSamples[x - 1][y][z] = (noise * 0.2f) + (noiseSamples[x - 1][y][z] * 0.8f);
                            }
                            if (z > 0) {
                                noiseSamples[x][y][z - 1] = (noise * 0.2f) + (noiseSamples[x][y][z - 1] * 0.8f);
                            }

                            if (y < 128) {
                                float noiseAbove = noiseSamples[x][y + 1][z];
                                if (noise > noiseAbove) {
                                    noiseSamples[x][y + 1][z] = (noise * 0.8F) + (noiseAbove * 0.2F);
                                }
                                if (y < 127) {
                                    float noiseTwoAbove = noiseSamples[x][y + 2][z];
                                    if (noise > noiseTwoAbove) {
                                        noiseSamples[x][y + 2][z] = (noise * 0.35F) + (noiseTwoAbove * 0.65F);
                                    }
                                }
                            }
                        }
                    }
                }
                noiseSamples[x][HAS_CAVES_FLAG][z] = columnHasCaveFlag ? 1 : 0;
            }
        }
        return noiseSamples;
    }

    // 6-point hexagon sample of the surface heightmap, matching the original carver.
    // Result is returned in the carver's internal Y space (so it can be compared
    // against maxCaveHeight directly), i.e. world Y shifted up by -Y_OFFSET.
    private int getMaxSurfaceHeight(ChunkAccess chunk) {
        int max = 0;
        int[][] testCoords = {{2, 6}, {3, 11}, {7, 2}, {9, 13}, {12, 4}, {13, 9}};
        for (int[] c : testCoords) {
            int h = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, c[0], c[1]) - Y_OFFSET;
            if (h > max) {
                max = h;
                if (max > maxCaveHeight) return max;
            }
        }
        return max;
    }

    private static boolean isFluidBlock(BlockState state) {
        return state != null && !state.getFluidState().isEmpty();
    }

    // pos is already in world-Y space; localY is the algorithm's internal Y used
    // for the lava-depth comparison (which is also in internal-Y space).
    private void digBlock(ChunkAccess chunk, BlockPos pos, int localY, BlockState aboveBlock) {
        if (localY <= lavaDepth) {
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
