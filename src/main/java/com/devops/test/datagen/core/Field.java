package com.devops.test.datagen.core;

/**
 * A named column in a {@link Schema}, with its generator and modifiers.
 */
public final class Field {

    private final String name;
    private final String typeSpec;
    private final ValueGenerator generator;
    private final double nullRate;
    private final double edgeRate;
    private final boolean unique;

    public Field(String name, String typeSpec, ValueGenerator generator,
                 double nullRate, double edgeRate, boolean unique) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Field name must not be empty");
        }
        checkRate("null", nullRate);
        checkRate("edge", edgeRate);
        this.name = name.trim();
        this.typeSpec = typeSpec;
        this.generator = generator;
        this.nullRate = nullRate;
        this.edgeRate = edgeRate;
        this.unique = unique;
    }

    private static void checkRate(String label, double rate) {
        if (rate < 0 || rate > 1) {
            throw new IllegalArgumentException(label + " rate must be between 0 and 1, got " + rate);
        }
    }

    public String name() { return name; }
    public String typeSpec() { return typeSpec; }
    public ValueGenerator generator() { return generator; }
    public double nullRate() { return nullRate; }
    public double edgeRate() { return edgeRate; }
    public boolean unique() { return unique; }
}
