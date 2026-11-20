package com.devops.test.datagen.core;

import java.util.Map;
import java.util.Random;

/**
 * State available to a generator while a single record is being built:
 * the shared seeded {@link Random}, the zero-based row index and the values
 * already produced for earlier fields of the same record (so that, e.g.,
 * an email can be derived from first/last name).
 */
public final class GenContext {

    private final Random random;
    private Map<String, Object> currentRecord;
    private long rowIndex;

    public GenContext(Random random) {
        this.random = random;
    }

    public Random random() {
        return random;
    }

    public long rowIndex() {
        return rowIndex;
    }

    public Object get(String fieldName) {
        return currentRecord == null ? null : currentRecord.get(fieldName);
    }

    void startRecord(long index, Map<String, Object> record) {
        this.rowIndex = index;
        this.currentRecord = record;
    }

    // ---- small random helpers shared by generators ----

    public int nextInt(int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) {
            throw new IllegalArgumentException("max < min: " + minInclusive + ".." + maxInclusive);
        }
        long span = (long) maxInclusive - minInclusive + 1;
        return (int) (minInclusive + (long) (random.nextDouble() * span));
    }

    public long nextLong(long minInclusive, long maxInclusive) {
        if (maxInclusive < minInclusive) {
            throw new IllegalArgumentException("max < min: " + minInclusive + ".." + maxInclusive);
        }
        double span = (double) maxInclusive - minInclusive + 1;
        long v = minInclusive + (long) Math.floor(random.nextDouble() * span);
        return Math.min(v, maxInclusive);
    }

    public boolean chance(double probability) {
        return probability > 0 && random.nextDouble() < probability;
    }

    public <T> T pick(T[] values) {
        return values[random.nextInt(values.length)];
    }
}
