package com.devops.test.datagen.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An ordered list of {@link Field}s describing one record shape.
 *
 * <p>Text format (one field per line, {@code #} starts a comment):</p>
 * <pre>
 * id:        sequence(1000)
 * firstName: firstName
 * email:     email                      | unique
 * age:       int(18, 90)                | null=0.05
 * status:    oneOf(ACTIVE:80, LOCKED:20) | edge=0.1
 * </pre>
 * Modifiers after {@code |}: {@code null=<rate>}, {@code edge=<rate>}, {@code unique}.
 * Fields are generated top-to-bottom, so derived types (email, fullName, state)
 * should be declared after the fields they read.
 */
public final class Schema {

    private final String name;
    private final List<Field> fields;

    private Schema(String name, List<Field> fields) {
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("Schema '" + name + "' has no fields");
        }
        this.name = name;
        this.fields = Collections.unmodifiableList(new ArrayList<Field>(fields));
    }

    public String name() { return name; }
    public List<Field> fields() { return fields; }

    // -------------------------------------------------------------- parsing

    public static Schema parse(String name, String text) {
        try {
            return parse(name, new StringReader(text));
        } catch (IOException e) {
            throw new IllegalStateException(e); // cannot happen for StringReader
        }
    }

    public static Schema parse(String name, Reader reader) throws IOException {
        Builder b = builder(name);
        BufferedReader br = new BufferedReader(reader);
        String line;
        int lineNo = 0;
        while ((line = br.readLine()) != null) {
            lineNo++;
            String l = stripComment(line).trim();
            if (l.isEmpty()) {
                continue;
            }
            int colon = l.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException(name + ":" + lineNo + ": expected '<field>: <type>' but got: " + line);
            }
            try {
                b.field(l.substring(0, colon).trim(), l.substring(colon + 1).trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(name + ":" + lineNo + ": " + e.getMessage(), e);
            }
        }
        return b.build();
    }

    public static Schema load(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String base = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(base, r);
        }
    }

    /**
     * Resolves a schema reference: an existing file path, or the name of a
     * bundled schema under {@code /schemas} on the classpath (e.g. "customer").
     */
    public static Schema resolve(String ref) throws IOException {
        Path p = Paths.get(ref);
        if (Files.isRegularFile(p)) {
            return load(p);
        }
        String base = ref.endsWith(".schema") ? ref.substring(0, ref.length() - 7) : ref;
        InputStream in = Schema.class.getResourceAsStream("/schemas/" + base + ".schema");
        if (in == null) {
            throw new IOException("Schema not found as file or bundled schema: " + ref);
        }
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(base, r);
        }
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        // '#' inside a type spec (e.g. pattern(ORD-###)) is not a comment
        while (hash >= 0) {
            int open = line.lastIndexOf('(', hash);
            int close = line.lastIndexOf(')', hash);
            if (open < 0 || close > open) {
                return line.substring(0, hash);
            }
            hash = line.indexOf('#', hash + 1);
        }
        return line;
    }

    // -------------------------------------------------------------- builder

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private final List<Field> fields = new ArrayList<Field>();
        private final Set<String> names = new HashSet<String>();

        private Builder(String name) {
            this.name = name == null ? "records" : name;
        }

        /** Adds a field from a spec like {@code "int(1,10) | null=0.1 | unique"}. */
        public Builder field(String fieldName, String specWithModifiers) {
            String[] parts = specWithModifiers.split("\\|");
            String typeSpec = parts[0].trim();
            double nullRate = 0;
            double edgeRate = 0;
            boolean unique = false;
            for (int i = 1; i < parts.length; i++) {
                String m = parts[i].trim();
                if (m.equals("unique")) {
                    unique = true;
                } else if (m.startsWith("null=")) {
                    nullRate = Double.parseDouble(m.substring(5).trim());
                } else if (m.startsWith("edge=")) {
                    edgeRate = Double.parseDouble(m.substring(5).trim());
                } else if (!m.isEmpty()) {
                    throw new IllegalArgumentException("Unknown modifier '" + m + "' (expected null=<rate>, edge=<rate> or unique)");
                }
            }
            return add(new Field(fieldName, typeSpec, Generators.create(typeSpec), nullRate, edgeRate, unique));
        }

        /** Adds a field backed by a custom generator (Java API only). */
        public Builder field(String fieldName, ValueGenerator generator) {
            return add(new Field(fieldName, "custom", generator, 0, 0, false));
        }

        public Builder add(Field f) {
            if (!names.add(f.name())) {
                throw new IllegalArgumentException("Duplicate field name: " + f.name());
            }
            fields.add(f);
            return this;
        }

        public Schema build() {
            return new Schema(name, fields);
        }
    }
}
