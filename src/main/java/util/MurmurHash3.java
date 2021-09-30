package util;

public class MurmurHash3 {
    // Constants for 32 bit variant
    private static final int C1_32 = 0xcc9e2d51;
    private static final int C2_32 = 0x1b873593;
    private static final int R1_32 = 15;
    private static final int R2_32 = 13;
    private static final int M_32 = 5;
    private static final int N_32 = 0xe6546b64;

    private static int mix32(int k, int hash) {
        k *= C1_32;
        k = Integer.rotateLeft(k, R1_32);
        k *= C2_32;
        hash ^= k;

        return Integer.rotateLeft(hash, R2_32) * M_32 + N_32;
    }

    private static int fmix32(final int length, int hash) {
        hash ^= length;
        hash ^= (hash >>> 16);
        hash *= 0x85ebca6b;
        hash ^= (hash >>> 13);
        hash *= 0xc2b2ae35;
        hash ^= (hash >>> 16);

        return hash;
    }

    public static int hash32(final long l, final int seed) {
        int hash = seed;
        final long r0 = l;
        final long r1 = Long.reverseBytes(l);

        hash = mix32((int) r0, hash);
        hash = mix32((int) (r0 >>> 32), hash);
        hash = mix32((int) (r1), hash);
        hash = mix32((int) (r1 >>> 32), hash);

        return fmix32(Long.BYTES * 2, hash);
    }

}
