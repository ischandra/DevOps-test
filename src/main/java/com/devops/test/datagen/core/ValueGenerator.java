package com.devops.test.datagen.core;

/**
 * Produces one value for a field. Implementations must draw all randomness
 * from {@link GenContext#random()} so output is reproducible for a given seed.
 */
public interface ValueGenerator {

    Object generate(GenContext ctx);

    /**
     * Boundary / hostile values used for negative testing when edge-case
     * injection is enabled for a field. Default: none.
     */
    default Object[] edgeCases() {
        return new Object[0];
    }
}
