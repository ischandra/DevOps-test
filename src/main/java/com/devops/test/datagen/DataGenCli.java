package com.devops.test.datagen;

import com.devops.test.datagen.core.DataGenerator;
import com.devops.test.datagen.core.Field;
import com.devops.test.datagen.core.Generators;
import com.devops.test.datagen.core.Schema;
import com.devops.test.datagen.output.RecordWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Command-line entry point.
 *
 * <pre>
 * java -jar sample.jar --schema customer --count 500 --format csv --seed 42 --out target/testdata/customers.csv
 * </pre>
 */
public final class DataGenCli {

    private static final String USAGE =
        "Synthetic test data generator\n\n"
        + "Usage: java -jar <jar> --schema <file|bundled-name> [options]\n\n"
        + "Options:\n"
        + "  --schema, -s <ref>     Schema file path, or bundled schema name (customer, order, login, product)\n"
        + "  --count, -n <n>        Number of records (default 10)\n"
        + "  --format, -f <fmt>     csv | json | jsonl | sql (default csv, or inferred from --out extension)\n"
        + "  --out, -o <file>       Output file (default stdout); parent dirs are created\n"
        + "  --seed <long>          Random seed for reproducible data (default: random, printed to stderr)\n"
        + "  --edge-rate <0..1>     Inject boundary/hostile values at this rate for negative testing\n"
        + "  --table <name>         Table name for sql format (default: schema name)\n"
        + "  --list-types           Print all supported field types\n"
        + "  --describe             Print the resolved schema and exit\n"
        + "  --help, -h             Show this help\n";

    private DataGenCli() {
    }

    public static void main(String[] args) {
        int code;
        try {
            code = run(args, System.out, System.err);
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("error: " + e.getMessage());
            code = 2;
        }
        System.exit(code);
    }

    /** Testable entry point; returns the process exit code. */
    public static int run(String[] args, PrintStream stdout, PrintStream stderr) throws IOException {
        String schemaRef = null;
        String format = null;
        String outFile = null;
        String table = null;
        long count = 10;
        Long seed = null;
        double edgeRate = 0;
        boolean describe = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--help": case "-h":
                    stdout.print(USAGE);
                    return 0;
                case "--list-types":
                    for (Map.Entry<String, String> e : Generators.describe().entrySet()) {
                        stdout.printf("  %-12s %s%n", e.getKey(), e.getValue());
                    }
                    return 0;
                case "--schema": case "-s": schemaRef = value(args, ++i, a); break;
                case "--count": case "-n": count = Long.parseLong(value(args, ++i, a)); break;
                case "--format": case "-f": format = value(args, ++i, a); break;
                case "--out": case "-o": outFile = value(args, ++i, a); break;
                case "--seed": seed = Long.parseLong(value(args, ++i, a)); break;
                case "--edge-rate": edgeRate = Double.parseDouble(value(args, ++i, a)); break;
                case "--table": table = value(args, ++i, a); break;
                case "--describe": describe = true; break;
                default:
                    throw new IllegalArgumentException("Unknown option '" + a + "'. Use --help.");
            }
        }
        if (schemaRef == null) {
            stderr.print(USAGE);
            return 2;
        }

        Schema schema = Schema.resolve(schemaRef);
        if (describe) {
            stdout.println("schema: " + schema.name());
            for (Field f : schema.fields()) {
                stdout.printf("  %-16s %s%s%s%s%n", f.name(), f.typeSpec(),
                    f.nullRate() > 0 ? " null=" + f.nullRate() : "",
                    f.edgeRate() > 0 ? " edge=" + f.edgeRate() : "",
                    f.unique() ? " unique" : "");
            }
            return 0;
        }

        RecordWriter.Format fmt = format != null ? RecordWriter.Format.parse(format) : inferFormat(outFile);
        DataGenerator gen = DataGenerator.of(schema).edgeRate(edgeRate);
        if (seed != null) {
            gen.seed(seed);
        }

        List<String> columns = new ArrayList<String>();
        for (Field f : schema.fields()) {
            columns.add(f.name());
        }

        long written;
        if (outFile == null) {
            Writer w = new BufferedWriter(new OutputStreamWriter(stdout, StandardCharsets.UTF_8));
            written = RecordWriter.write(fmt, columns, gen.stream(count), w, table != null ? table : schema.name());
            w.flush();
        } else {
            Path p = Paths.get(outFile);
            if (p.toAbsolutePath().getParent() != null) {
                Files.createDirectories(p.toAbsolutePath().getParent());
            }
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                written = RecordWriter.write(fmt, columns, gen.stream(count), w, table != null ? table : schema.name());
            }
        }
        stderr.printf("Generated %d %s record(s) as %s with seed %d%s%n", written, schema.name(),
            fmt.name().toLowerCase(), gen.seed(), outFile != null ? " -> " + outFile : "");
        return 0;
    }

    private static RecordWriter.Format inferFormat(String outFile) {
        if (outFile != null) {
            String lower = outFile.toLowerCase();
            if (lower.endsWith(".jsonl") || lower.endsWith(".ndjson")) { return RecordWriter.Format.JSONL; }
            if (lower.endsWith(".json")) { return RecordWriter.Format.JSON; }
            if (lower.endsWith(".sql")) { return RecordWriter.Format.SQL; }
        }
        return RecordWriter.Format.CSV;
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[i];
    }
}
