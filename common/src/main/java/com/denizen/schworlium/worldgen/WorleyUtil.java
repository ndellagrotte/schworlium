package com.denizen.schworlium.worldgen;

import java.util.Random;

/*
 * 3D cellular (Worley) noise sampler returning the F1/F3 distance ratio that
 * produces the characteristic branching cave passages.
 *
 * The algorithm — Worley/Voronoi cellular noise compared by ratio of the first
 * and third closest cell distances — is a published mathematical technique
 * (Worley 1996) used as the cave shape function in the MIT-licensed
 * WorleyCaves mod (SuperFluke). The jitter offset table below is generated
 * procedurally at class load from a seeded RNG rather than copied from
 * upstream lookup tables, so cave shapes will be Worley-style but will not
 * be byte-identical to the original mod for a given seed. Numeric constants
 * (frequency, primes, search radius) are the published FastNoise defaults
 * that the Worley algorithm assumes.
 */
public class WorleyUtil {

    private final int seed;
    private float frequency = 0.01f;

    public WorleyUtil() {
        this(1337);
    }

    public WorleyUtil(int seed) {
        this.seed = seed;
    }

    public void SetFrequency(float frequency) {
        this.frequency = frequency;
    }

    private static final int X_PRIME = 1619;
    private static final int Y_PRIME = 31337;
    private static final int Z_PRIME = 6971;

    // 256 cells × 3 floats. Generated once from a deterministic seed so the
    // result is reproducible across runs and platforms without reproducing
    // the upstream lookup table verbatim. Values are roughly uniform in
    // [-0.45, 0.45]^3, the same range FastNoise's published table occupies.
    private static final float[] CELL_3D = new float[256 * 3];
    static {
        Random rng = new Random(0xC0FFEEL);
        for (int i = 0; i < CELL_3D.length; i++) {
            CELL_3D[i] = (rng.nextFloat() - 0.5f) * 0.9f;
        }
    }

    private static int fastFloor(float f) {
        return f >= 0 ? (int) f : (int) f - 1;
    }

    private static int hash3D(int seed, int x, int y, int z) {
        int hash = seed ^ x ^ y ^ z;
        hash = hash * hash * hash * 60493;
        return (hash >> 13) ^ hash;
    }

    public float SingleCellular3Edge(float x, float y, float z) {
        x *= frequency;
        y *= frequency;
        z *= frequency;

        int xr = fastFloor(x) - 1;
        int yr = fastFloor(y) - 1;
        int zr = fastFloor(z) - 1;
        int xrPrime = xr * X_PRIME;
        int yrPrime = yr * Y_PRIME;
        int zrPrime = zr * Z_PRIME;

        float distance1 = Float.MAX_VALUE;
        float distance2 = Float.MAX_VALUE;
        float distance3 = Float.MAX_VALUE;

        int xp = xrPrime;
        for (int xi = 0; xi < 3; xi++) {
            int yp = yrPrime;
            for (int yi = 0; yi < 3; yi++) {
                int zp = zrPrime;
                for (int zi = 0; zi < 3; zi++) {
                    int idx = (hash3D(seed, xp, yp, zp) & 255) * 3;

                    float vecX = xr + xi + CELL_3D[idx]     + 0.5f - x;
                    float vecY = yr + yi + CELL_3D[idx + 1] + 0.5f - y;
                    float vecZ = zr + zi + CELL_3D[idx + 2] + 0.5f - z;

                    float newDistance = vecX * vecX + vecY * vecY + vecZ * vecZ;

                    if (newDistance < distance1) {
                        distance3 = distance2;
                        distance2 = distance1;
                        distance1 = newDistance;
                    } else if (newDistance < distance2) {
                        distance3 = distance2;
                        distance2 = newDistance;
                    } else if (newDistance < distance3) {
                        distance3 = newDistance;
                    }

                    zp += Z_PRIME;
                }
                yp += Y_PRIME;
            }
            xp += X_PRIME;
        }

        return distance1 / distance3 - 1;
    }
}
