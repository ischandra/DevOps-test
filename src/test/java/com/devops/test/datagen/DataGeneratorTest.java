package com.devops.test.datagen;

import com.devops.test.datagen.core.DataGenerator;
import com.devops.test.datagen.core.Generators;
import com.devops.test.datagen.core.Schema;
import com.devops.test.datagen.output.RecordWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

public class DataGeneratorTest extends TestCase {

    public void testSameSeedProducesIdenticalData() throws Exception {
        Schema s = Schema.resolve("customer");
        List<Map<String, Object>> a = DataGenerator.of(s).seed(7).generate(50);
        List<Map<String, Object>> b = DataGenerator.of(s).seed(7).generate(50);
        List<Map<String, Object>> c = DataGenerator.of(s).seed(8).generate(50);
        assertEquals(a, b);
        assertFalse(a.equals(c));
    }

    public void testAllBundledSchemasGenerate() throws Exception {
        for (String name : Arrays.asList("customer", "order", "login", "product")) {
            List<Map<String, Object>> rows = DataGenerator.of(Schema.resolve(name)).seed(1).generate(200);
            assertEquals(name, 200, rows.size());
        }
    }

    public void testRangesAndSequence() {
        Schema s = Schema.builder("t")
            .field("id", "sequence(10, 5)")
            .field("age", "int(18, 20)")
            .field("price", "decimal(1.00, 2.00, 2)")
            .field("d", "date(2024-02-01, 2024-02-03)")
            .build();
        List<Map<String, Object>> rows = DataGenerator.of(s).seed(3).generate(300);
        Set<Object> ages = new HashSet<Object>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            assertEquals(10L + i * 5L, r.get("id"));
            long age = (Long) r.get("age");
            assertTrue(age >= 18 && age <= 20);
            ages.add(age);
            BigDecimal p = (BigDecimal) r.get("price");
            assertTrue(p.compareTo(new BigDecimal("1.00")) >= 0 && p.compareTo(new BigDecimal("2.00")) <= 0);
            assertEquals(2, p.scale());
            assertTrue(Arrays.asList("2024-02-01", "2024-02-02", "2024-02-03").contains(r.get("d")));
        }
        assertEquals("both inclusive bounds should be reachable", 3, ages.size());
    }

    public void testDerivedFieldsAreConsistent() {
        Schema s = Schema.builder("t")
            .field("firstName", "firstName")
            .field("lastName", "lastName")
            .field("fullName", "fullName")
            .field("email", "email")
            .field("city", "city")
            .field("state", "state")
            .build();
        for (Map<String, Object> r : DataGenerator.of(s).seed(11).generate(100)) {
            assertEquals(r.get("firstName") + " " + r.get("lastName"), r.get("fullName"));
            String email = (String) r.get("email");
            assertTrue(email, email.startsWith(r.get("firstName").toString().toLowerCase().replaceAll("[^a-z0-9]", "")));
            assertTrue(email, email.matches("[a-z0-9.]+@[a-z.]*example\\.(com|org|net)"));
            if ("Austin".equals(r.get("city"))) {
                assertEquals("TX", r.get("state"));
            }
        }
    }

    public void testUniqueModifier() {
        Schema s = Schema.builder("t").field("code", "pattern(##) | unique").build();
        List<Map<String, Object>> rows = DataGenerator.of(s).seed(5).generate(100);
        Set<Object> codes = new HashSet<Object>();
        for (Map<String, Object> r : rows) {
            assertTrue("duplicate " + r.get("code"), codes.add(r.get("code")));
        }
        try {
            DataGenerator.of(s).seed(5).generate(101);
            fail("only 100 distinct 2-digit codes exist");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("unique"));
        }
    }

    public void testNullAndWeightedRates() {
        Schema s = Schema.builder("t")
            .field("maybe", "constant(x) | null=0.3")
            .field("status", "oneOf(A:90, B:10)")
            .build();
        int nulls = 0;
        int as = 0;
        List<Map<String, Object>> rows = DataGenerator.of(s).seed(9).generate(10000);
        for (Map<String, Object> r : rows) {
            if (r.get("maybe") == null) { nulls++; }
            if ("A".equals(r.get("status"))) { as++; }
        }
        assertTrue("null rate ~30%: " + nulls, nulls > 2700 && nulls < 3300);
        assertTrue("weight ~90%: " + as, as > 8700 && as < 9300);
    }

    public void testEdgeCaseInjection() {
        Schema s = Schema.builder("t").field("age", "int(18, 65) | edge=1.0").build();
        Set<Object> seen = new HashSet<Object>();
        for (Map<String, Object> r : DataGenerator.of(s).seed(2).generate(200)) {
            seen.add(r.get("age"));
        }
        assertEquals(new HashSet<Object>(Arrays.<Object>asList(18L, 65L, 17L, 66L)), seen);
    }

    public void testPatternAndUuid() {
        Schema s = Schema.builder("t")
            .field("ord", "pattern(ORD-###-??-\\#)")
            .field("id", "uuid")
            .build();
        for (Map<String, Object> r : DataGenerator.of(s).seed(4).generate(50)) {
            assertTrue(r.get("ord").toString(), r.get("ord").toString().matches("ORD-\\d{3}-[A-Z]{2}-#"));
            assertTrue(r.get("id").toString().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
        }
    }

    public void testSchemaParsingAndErrors() {
        Schema s = Schema.parse("inline",
            "# comment\n\nid: sequence   # trailing comment\ncode: pattern(A-##)  | null=0.5 | unique\n");
        assertEquals(2, s.fields().size());
        assertEquals("pattern(A-##)", s.fields().get(1).typeSpec());
        assertTrue(s.fields().get(1).unique());

        assertParseError("x: nope", "Unknown field type");
        assertParseError("x: int(a,b)", "Invalid arguments");
        assertParseError("x: int | sometimes", "Unknown modifier");
        assertParseError("x: int\nx: int", "Duplicate field");
        assertParseError("just text", "expected '<field>: <type>'");
        assertParseError("x: int | null=2", "between 0 and 1");
    }

    private static void assertParseError(String text, String expected) {
        try {
            Schema.parse("bad", text);
            fail("expected failure for: " + text);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expected));
        }
    }

    public void testEveryRegisteredTypeWorksWithDefaults() {
        for (String type : Generators.describe().keySet()) {
            String spec = type.equals("oneOf") ? "oneOf(A)" : type;
            Schema s = Schema.builder("t").field("v", spec + " | edge=0.5").build();
            assertEquals(type, 20, DataGenerator.of(s).seed(1).generate(20).size());
        }
    }

    public void testWritersEscapeCorrectly() throws Exception {
        Schema s = Schema.builder("my table")
            .field("txt", "constant(He said \"hi\"; it's ok)")
            .field("n", "int(5, 5)")
            .field("b", "boolean(1)")
            .field("z", "constant(x) | null=1")
            .build();
        List<String> cols = Arrays.asList("txt", "n", "b", "z");

        String csv = write(RecordWriter.Format.CSV, s, cols);
        assertEquals("txt,n,b,z\r\n\"He said \"\"hi\"\"; it's ok\",5,true,\r\n", csv);

        String json = write(RecordWriter.Format.JSONL, s, cols);
        assertEquals("{\"txt\": \"He said \\\"hi\\\"; it's ok\", \"n\": 5, \"b\": true, \"z\": null}\n", json);

        String sql = write(RecordWriter.Format.SQL, s, cols);
        assertEquals("INSERT INTO \"my table\" (txt, n, b, z) VALUES ('He said \"hi\"; it''s ok', 5, TRUE, NULL);\n", sql);
    }

    private static String write(RecordWriter.Format f, Schema s, List<String> cols) throws Exception {
        StringWriter w = new StringWriter();
        RecordWriter.write(f, cols, DataGenerator.of(s).seed(1).stream(1), w, s.name());
        return w.toString();
    }

    public void testCliWritesFile() throws Exception {
        File out = File.createTempFile("datagen", ".json");
        out.deleteOnExit();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = DataGenCli.run(new String[] {"--schema", "product", "-n", "25", "--seed", "42", "-o", out.getPath()},
            new PrintStream(new ByteArrayOutputStream()), new PrintStream(err, true, "UTF-8"));
        assertEquals(0, code);
        String content = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.startsWith("[\n  {\"sku\": \"SKU-"));
        List<String> lines = new ArrayList<String>(Arrays.asList(content.split("\n")));
        assertEquals(27, lines.size()); // [ + 25 rows + ]
        assertTrue(err.toString("UTF-8").contains("Generated 25 product record(s) as json with seed 42"));
    }
}
