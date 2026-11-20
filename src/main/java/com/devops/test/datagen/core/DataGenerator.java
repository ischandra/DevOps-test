package com.devops.test.datagen.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

/**
 * Produces records for a {@link Schema}. Output is fully deterministic for a
 * given seed, so a failing test can be reproduced exactly.
 *
 * <pre>
 * List&lt;Map&lt;String, Object&gt;&gt; users = DataGenerator.of(Schema.resolve("customer"))
 *         .seed(42)
 *         .generate(100);
 * </pre>
 */
public final class DataGenerator {

    private static final int MAX_UNIQUE_ATTEMPTS = 1000;

    private final Schema schema;
    private long seed = System.nanoTime();
    private double globalEdgeRate = 0;

    private DataGenerator(Schema schema) {
        this.schema = schema;
    }

    public static DataGenerator of(Schema schema) {
        return new DataGenerator(schema);
    }

    public DataGenerator seed(long seed) {
        this.seed = seed;
        return this;
    }

    public long seed() {
        return seed;
    }

    /**
     * Probability of replacing any value with a boundary/hostile edge case,
     * applied to every field that has no explicit {@code edge=} modifier.
     */
    public DataGenerator edgeRate(double rate) {
        if (rate < 0 || rate > 1) {
            throw new IllegalArgumentException("edge rate must be between 0 and 1, got " + rate);
        }
        this.globalEdgeRate = rate;
        return this;
    }

    public Schema schema() {
        return schema;
    }

    public List<Map<String, Object>> generate(int count) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(Math.max(0, count));
        Iterator<Map<String, Object>> it = stream(count);
        while (it.hasNext()) {
            out.add(it.next());
        }
        return out;
    }

    public Map<String, Object> generateOne() {
        return generate(1).get(0);
    }

    /** Lazily generates records - use for large volumes written straight to disk. */
    public Iterator<Map<String, Object>> stream(final long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        final GenContext ctx = new GenContext(new Random(seed));
        final Map<String, Set<Object>> seen = new HashMap<String, Set<Object>>();
        for (Field f : schema.fields()) {
            if (f.unique()) {
                seen.put(f.name(), new HashSet<Object>());
            }
        }
        return new Iterator<Map<String, Object>>() {
            private long index = 0;

            public boolean hasNext() {
                return index < count;
            }

            public Map<String, Object> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Map<String, Object> record = new LinkedHashMap<String, Object>();
                ctx.startRecord(index, record);
                for (Field f : schema.fields()) {
                    record.put(f.name(), valueFor(f, ctx, seen.get(f.name())));
                }
                index++;
                return record;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private Object valueFor(Field f, GenContext ctx, Set<Object> seenValues) {
        if (ctx.chance(f.nullRate())) {
            return null;
        }
        double edge = f.edgeRate() > 0 ? f.edgeRate() : globalEdgeRate;
        if (edge > 0 && seenValues == null) {
            Object[] edges = f.generator().edgeCases();
            if (edges.length > 0 && ctx.chance(edge)) {
                return ctx.pick(edges);
            }
        }
        if (seenValues == null) {
            return f.generator().generate(ctx);
        }
        for (int attempt = 0; attempt < MAX_UNIQUE_ATTEMPTS; attempt++) {
            Object v = f.generator().generate(ctx);
            if (seenValues.add(v)) {
                return v;
            }
        }
        throw new IllegalStateException("Could not generate a unique value for field '" + f.name()
            + "' after " + MAX_UNIQUE_ATTEMPTS + " attempts - the value space of '" + f.typeSpec()
            + "' is too small for the requested count");
    }
}
