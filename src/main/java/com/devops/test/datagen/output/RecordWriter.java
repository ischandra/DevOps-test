package com.devops.test.datagen.output;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Serialises generated records. Supported formats:
 * {@code csv}, {@code json} (array), {@code jsonl} (one object per line) and
 * {@code sql} (INSERT statements).
 */
public final class RecordWriter {

    public enum Format {
        CSV, JSON, JSONL, SQL;

        public static Format parse(String s) {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown format '" + s + "'. Use csv, json, jsonl or sql");
            }
        }
    }

    private RecordWriter() {
    }

    public static long write(Format format, List<String> columns, Iterator<Map<String, Object>> records,
                             Writer out, String tableName) throws IOException {
        switch (format) {
            case CSV:   return writeCsv(columns, records, out);
            case JSON:  return writeJson(columns, records, out, true);
            case JSONL: return writeJson(columns, records, out, false);
            case SQL:   return writeSql(columns, records, out, tableName);
            default:    throw new IllegalArgumentException("Unsupported format " + format);
        }
    }

    // ----------------------------------------------------------------- CSV

    private static long writeCsv(List<String> columns, Iterator<Map<String, Object>> records, Writer out)
            throws IOException {
        StringBuilder header = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) { header.append(','); }
            header.append(csvCell(columns.get(i)));
        }
        out.write(header.append("\r\n").toString());
        long n = 0;
        while (records.hasNext()) {
            Map<String, Object> r = records.next();
            StringBuilder row = new StringBuilder();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) { row.append(','); }
                Object v = r.get(columns.get(i));
                row.append(v == null ? "" : csvCell(v.toString()));
            }
            out.write(row.append("\r\n").toString());
            n++;
        }
        return n;
    }

    /** RFC 4180 quoting. */
    static String csvCell(String s) {
        if (s.isEmpty() || s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0
                || s.indexOf('\r') >= 0 || !s.trim().equals(s)) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    // ---------------------------------------------------------------- JSON

    private static long writeJson(List<String> columns, Iterator<Map<String, Object>> records, Writer out,
                                  boolean asArray) throws IOException {
        long n = 0;
        if (asArray) { out.write("[\n"); }
        while (records.hasNext()) {
            Map<String, Object> r = records.next();
            StringBuilder sb = new StringBuilder();
            if (asArray) {
                sb.append(n > 0 ? ",\n  " : "  ");
            }
            sb.append('{');
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) { sb.append(", "); }
                String c = columns.get(i);
                sb.append(jsonString(c)).append(": ").append(jsonValue(r.get(c)));
            }
            sb.append('}');
            if (!asArray) { sb.append('\n'); }
            out.write(sb.toString());
            n++;
        }
        if (asArray) { out.write(n > 0 ? "\n]\n" : "]\n"); }
        return n;
    }

    static String jsonValue(Object v) {
        if (v == null) { return "null"; }
        if (v instanceof Boolean) { return v.toString(); }
        if (v instanceof BigDecimal) { return ((BigDecimal) v).toPlainString(); }
        if (v instanceof Number) { return v.toString(); }
        return jsonString(v.toString());
    }

    static String jsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    // ----------------------------------------------------------------- SQL

    private static long writeSql(List<String> columns, Iterator<Map<String, Object>> records, Writer out,
                                 String tableName) throws IOException {
        StringBuilder cols = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) { cols.append(", "); }
            cols.append(sqlIdentifier(columns.get(i)));
        }
        String prefix = "INSERT INTO " + sqlIdentifier(tableName) + " (" + cols + ") VALUES (";
        long n = 0;
        while (records.hasNext()) {
            Map<String, Object> r = records.next();
            StringBuilder sb = new StringBuilder(prefix);
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) { sb.append(", "); }
                sb.append(sqlValue(r.get(columns.get(i))));
            }
            out.write(sb.append(");\n").toString());
            n++;
        }
        return n;
    }

    static String sqlValue(Object v) {
        if (v == null) { return "NULL"; }
        if (v instanceof Boolean) { return ((Boolean) v) ? "TRUE" : "FALSE"; }
        if (v instanceof BigDecimal) { return ((BigDecimal) v).toPlainString(); }
        if (v instanceof Number) { return v.toString(); }
        return "'" + v.toString().replace("'", "''") + "'";
    }

    static String sqlIdentifier(String name) {
        if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return name;
        }
        return '"' + name.replace("\"", "\"\"") + '"';
    }
}
