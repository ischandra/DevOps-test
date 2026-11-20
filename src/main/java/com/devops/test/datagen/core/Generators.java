package com.devops.test.datagen.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of built-in field types. A type spec looks like
 * {@code name} or {@code name(arg1, arg2, ...)}, e.g. {@code int(18,90)}.
 *
 * <p>All generated contact data uses reserved/fictional ranges
 * (example.* domains, 555 phone numbers, public test card numbers,
 * RFC 5737 documentation IPs) so it can never collide with real people.</p>
 */
public final class Generators {

    /** Factory turning parsed args into a generator. */
    interface Factory {
        ValueGenerator create(List<String> args);
    }

    private static final Map<String, Factory> FACTORIES = new LinkedHashMap<String, Factory>();
    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<String, String>();

    // ---------------------------------------------------------------- data

    static final String[] FIRST_NAMES = {
        "James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael", "Linda", "David", "Elizabeth",
        "William", "Barbara", "Richard", "Susan", "Joseph", "Jessica", "Thomas", "Sarah", "Charles", "Karen",
        "Aarav", "Priya", "Wei", "Mei", "Hiroshi", "Yuki", "Mohammed", "Fatima", "Carlos", "Sofia",
        "Liam", "Olivia", "Noah", "Emma", "Mateo", "Isabella", "Arjun", "Ananya", "Lucas", "Chloe",
        "Ravi", "Sanjana", "Omar", "Leila", "Diego", "Valentina", "Kwame", "Amara", "Sven", "Ingrid"
    };

    static final String[] LAST_NAMES = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez",
        "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin",
        "Chandra", "Sharma", "Patel", "Reddy", "Nair", "Chen", "Wang", "Li", "Tanaka", "Suzuki",
        "Kim", "Park", "Nguyen", "Khan", "Ali", "Silva", "Santos", "Muller", "Schmidt", "Rossi",
        "Dubois", "Novak", "Okafor", "Mensah", "Larsen", "Johansson", "O'Brien", "Murphy", "Cohen", "Ivanova"
    };

    static final String[] DOMAINS = {"example.com", "example.org", "example.net", "test.example.com"};

    static final String[] STREET_NAMES = {
        "Main", "Oak", "Pine", "Maple", "Cedar", "Elm", "Washington", "Lake", "Hill", "Park",
        "Sunset", "River", "Church", "Highland", "Mill", "Spring", "Forest", "Meadow", "Ridge", "Valley"
    };
    static final String[] STREET_SUFFIXES = {"St", "Ave", "Rd", "Blvd", "Ln", "Dr", "Ct", "Way", "Pl"};

    static final String[][] CITY_STATE = {
        {"Springfield", "IL"}, {"Austin", "TX"}, {"Denver", "CO"}, {"Portland", "OR"}, {"Seattle", "WA"},
        {"Boston", "MA"}, {"Raleigh", "NC"}, {"Phoenix", "AZ"}, {"Columbus", "OH"}, {"Madison", "WI"},
        {"Atlanta", "GA"}, {"Nashville", "TN"}, {"San Jose", "CA"}, {"Miami", "FL"}, {"Salt Lake City", "UT"},
        {"Minneapolis", "MN"}, {"Richmond", "VA"}, {"Albany", "NY"}, {"Trenton", "NJ"}, {"Boise", "ID"}
    };

    static final String[] COUNTRIES = {
        "United States", "India", "United Kingdom", "Canada", "Germany", "France", "Japan", "Australia",
        "Brazil", "Mexico", "Singapore", "Netherlands", "Sweden", "Spain", "Italy", "South Africa"
    };

    static final String[] COMPANY_PREFIX = {
        "Acme", "Globex", "Initech", "Umbrella", "Stark", "Wayne", "Vandelay", "Hooli", "Soylent", "Tyrell",
        "Cyberdyne", "Wonka", "Gringotts", "Oscorp", "Massive", "Blue Sun", "Pied Piper", "Aperture"
    };
    static final String[] COMPANY_SUFFIX = {"Inc", "LLC", "Ltd", "Corp", "Group", "Labs", "Systems", "Holdings"};

    static final String[] WORDS = (
        "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore "
        + "et dolore magna aliqua enim ad minim veniam quis nostrud exercitation ullamco laboris nisi aliquip "
        + "ex ea commodo consequat duis aute irure in reprehenderit voluptate velit esse cillum fugiat nulla "
        + "pariatur excepteur sint occaecat cupidatat non proident sunt culpa qui officia deserunt mollit anim "
        + "id est laborum").split(" ");

    /** Publicly documented payment-gateway test card numbers (never real accounts). */
    static final String[] TEST_CARDS = {
        "4111111111111111", "4012888888881881", "4242424242424242", "5555555555554444",
        "5105105105105100", "2223003122003222", "378282246310005", "371449635398431",
        "6011111111111117", "3566002020360505"
    };

    /** Generic hostile / boundary strings for negative testing. */
    static final Object[] STRING_EDGE_CASES = {
        "", " ", "   leading-and-trailing   ", repeat('x', 256), repeat('y', 4096),
        "O'Brien", "Robert'); DROP TABLE students;--", "' OR '1'='1",
        "<script>alert(1)</script>", "Zoë Ångström-Müller", "李小龙", "مرحبا", "😀🚀✅",
        "line1\nline2", "tab\tseparated", "comma,separated", "\"quoted\"", "null", "NULL", "\\"
    };

    // ------------------------------------------------------------ registry

    static {
        register("sequence", "sequence(start=1, step=1) - incrementing number", new Factory() {
            public ValueGenerator create(List<String> a) {
                final long start = argLong(a, 0, 1);
                final long step = argLong(a, 1, 1);
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) { return start + ctx.rowIndex() * step; }
                };
            }
        });

        register("uuid", "uuid - random RFC 4122 v4 UUID", new Factory() {
            public ValueGenerator create(List<String> a) {
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) {
                        long msb = ctx.random().nextLong();
                        long lsb = ctx.random().nextLong();
                        msb = (msb & 0xffffffffffff0fffL) | 0x0000000000004000L; // version 4
                        lsb = (lsb & 0x3fffffffffffffffL) | 0x8000000000000000L; // IETF variant
                        return new java.util.UUID(msb, lsb).toString();
                    }
                };
            }
        });

        register("int", "int(min=0, max=1000) - integer in range (inclusive)", new Factory() {
            public ValueGenerator create(List<String> a) {
                final long min = argLong(a, 0, 0);
                final long max = argLong(a, 1, 1000);
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) { return ctx.nextLong(min, max); }
                    public Object[] edgeCases() {
                        List<Object> e = new ArrayList<Object>();
                        e.add(min); e.add(max); e.add(min - 1); e.add(max + 1);
                        if (min < 0 && max > 0) { e.add(0L); }
                        return e.toArray();
                    }
                };
            }
        });

        register("decimal", "decimal(min=0, max=1000, scale=2) - decimal number", new Factory() {
            public ValueGenerator create(List<String> a) {
                final BigDecimal min = new BigDecimal(arg(a, 0, "0"));
                final BigDecimal max = new BigDecimal(arg(a, 1, "1000"));
                final int scale = (int) argLong(a, 2, 2);
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) {
                        BigDecimal span = max.subtract(min);
                        return min.add(span.multiply(BigDecimal.valueOf(ctx.random().nextDouble())))
                                  .setScale(scale, RoundingMode.HALF_UP);
                    }
                    public Object[] edgeCases() {
                        BigDecimal ulp = BigDecimal.ONE.movePointLeft(scale);
                        return new Object[] {
                            min.setScale(scale, RoundingMode.HALF_UP), max.setScale(scale, RoundingMode.HALF_UP),
                            min.subtract(ulp).setScale(scale, RoundingMode.HALF_UP),
                            max.add(ulp).setScale(scale, RoundingMode.HALF_UP)
                        };
                    }
                };
            }
        });

        register("boolean", "boolean(trueProbability=0.5)", new Factory() {
            public ValueGenerator create(List<String> a) {
                final double p = Double.parseDouble(arg(a, 0, "0.5"));
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) { return ctx.chance(p); }
                };
            }
        });

        register("oneOf", "oneOf(A, B, C) or weighted oneOf(A:70, B:20, C:10)", new Factory() {
            public ValueGenerator create(List<String> a) {
                if (a.isEmpty()) {
                    throw new IllegalArgumentException("oneOf needs at least one value");
                }
                final List<String> values = new ArrayList<String>();
                final List<Double> cumulative = new ArrayList<Double>();
                double total = 0;
                for (String raw : a) {
                    String v = raw;
                    double w = 1;
                    int colon = raw.lastIndexOf(':');
                    if (colon > 0) {
                        try {
                            w = Double.parseDouble(raw.substring(colon + 1).trim());
                            v = raw.substring(0, colon).trim();
                        } catch (NumberFormatException ignore) {
                            // colon is part of the literal value (e.g. a time) - keep it
                        }
                    }
                    total += w;
                    values.add(v);
                    cumulative.add(total);
                }
                final double sum = total;
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) {
                        double r = ctx.random().nextDouble() * sum;
                        for (int i = 0; i < values.size(); i++) {
                            if (r < cumulative.get(i)) { return values.get(i); }
                        }
                        return values.get(values.size() - 1);
                    }
                    public Object[] edgeCases() {
                        return new Object[] {"", "UNKNOWN_VALUE", values.get(0).toLowerCase(Locale.ROOT)};
                    }
                };
            }
        });

        register("constant", "constant(value) - always the same value", new Factory() {
            public ValueGenerator create(List<String> a) {
                final String v = arg(a, 0, "");
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) { return v; }
                };
            }
        });

        register("firstName", "firstName - given name", stringPick(FIRST_NAMES));
        register("lastName", "lastName - family name", stringPick(LAST_NAMES));

        register("fullName", "fullName - uses firstName/lastName fields of the record if present", new Factory() {
            public ValueGenerator create(List<String> a) {
                return new StringGen() {
                    public Object generate(GenContext ctx) {
                        return first(ctx) + " " + last(ctx);
                    }
                };
            }
        });

        register("email", "email - derived from firstName/lastName when present; example.* domains only", new Factory() {
            public ValueGenerator create(List<String> a) {
                final String domain = arg(a, 0, null);
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) {
                        String local = slug(first(ctx)) + "." + slug(last(ctx));
                        if (ctx.chance(0.5)) { local += ctx.nextInt(1, 999); }
                        return local + "@" + (domain != null ? domain : ctx.pick(DOMAINS));
                    }
                    public Object[] edgeCases() {
                        return new Object[] {
                            "", "plainaddress", "@example.com", "user@", "user@@example.com",
                            "user name@example.com", "user@example", "a@b.c",
                            repeat('a', 64) + "@example.com", repeat('a', 65) + "@example.com",
                            "first.last+tag@example.com", "UPPER.CASE@EXAMPLE.COM", "user@sub.domain.example.com"
                        };
                    }
                };
            }
        });

        register("username", "username - derived from name when present", new Factory() {
            public ValueGenerator create(List<String> a) {
                return new StringGen() {
                    public Object generate(GenContext ctx) {
                        String f = slug(first(ctx));
                        return f.charAt(0) + slug(last(ctx)) + ctx.nextInt(10, 9999);
                    }
                };
            }
        });

        register("password", "password(length=12) - random password with upper/lower/digit/symbol", new Factory() {
            public ValueGenerator create(List<String> a) {
                final int len = Math.max(4, (int) argLong(a, 0, 12));
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) {
                        String[] pools = {"ABCDEFGHJKLMNPQRSTUVWXYZ", "abcdefghijkmnopqrstuvwxyz", "23456789", "!@#$%^&*-_"};
                        List<Character> chars = new ArrayList<Character>();
                        for (String p : pools) { chars.add(p.charAt(ctx.random().nextInt(p.length()))); }
                        String all = pools[0] + pools[1] + pools[2] + pools[3];
                        while (chars.size() < len) { chars.add(all.charAt(ctx.random().nextInt(all.length()))); }
                        Collections.shuffle(chars, ctx.random());
                        StringBuilder sb = new StringBuilder();
                        for (char c : chars) { sb.append(c); }
                        return sb.toString();
                    }
                    public Object[] edgeCases() {
                        return new Object[] {"", "a", "password", "12345678", repeat('A', len - 1), repeat('Z', 129)};
                    }
                };
            }
        });

        register("phone", "phone - fictional US number in the 555 exchange", new Factory() {
            public ValueGenerator create(List<String> a) {
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) {
                        return String.format("+1-%03d-555-%04d", ctx.nextInt(201, 989), ctx.nextInt(100, 199));
                    }
                    public Object[] edgeCases() {
                        return new Object[] {"", "123", "+1-000-000-0000", "abc-def-ghij", "+1 (555) 555-0100 ext. 12345"};
                    }
                };
            }
        });

        register("street", "street - street address line", new Factory() {
            public ValueGenerator create(List<String> a) {
                return new StringGen() {
                    public Object generate(GenContext ctx) {
                        return ctx.nextInt(1, 9999) + " " + ctx.pick(STREET_NAMES) + " " + ctx.pick(STREET_SUFFIXES);
                    }
                };
            }
        });

        register("city", "city", new Factory() {
            public ValueGenerator create(List<String> a) {
                return new StringGen() {
                    public Object generate(GenContext ctx) { return ctx.pick(CITY_STATE)[0]; }
                };
            }
        });

        register("state", "state - US state code (matches city when city field precedes it)", new Factory() {
            public ValueGenerator create(List<String> a) {
                return new StringGen() {
                    public Object generate(GenContext ctx) {
                        Object city = ctx.get("city");
                        for (String[] cs : CITY_STATE) {
                            if (cs[0].equals(city)) { return cs[1]; }
                        }
                        return ctx.pick(CITY_STATE)[1];
                    }
                };
            }
        });

        register("zip", "zip - 5 digit postal code", new Factory() {
            public ValueGenerator create(List<String> a) {
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) { return String.format("%05d", ctx.nextInt(1001, 99950)); }
                    public Object[] edgeCases() { return new Object[] {"", "00000", "1234", "123456", "ABCDE", "12345-6789"}; }
                };
            }
        });

        register("country", "country", stringPick(COUNTRIES));

        register("address", "address - full single-line address", new Factory() {
            public ValueGenerator create(List<String> a) {
                return new StringGen() {
                    public Object generate(GenContext ctx) {
                        String[] cs = ctx.pick(CITY_STATE);
                        return ctx.nextInt(1, 9999) + " " + ctx.pick(STREET_NAMES) + " " + ctx.pick(STREET_SUFFIXES)
                            + ", " + cs[0] + ", " + cs[1] + " " + String.format("%05d", ctx.nextInt(1001, 99950));
                    }
                };
            }
        });

        register("company", "company - fictional company name", new Factory() {
            public ValueGenerator create(List<String> a) {
                return new StringGen() {
                    public Object generate(GenContext ctx) {
                        return ctx.pick(COMPANY_PREFIX) + " " + ctx.pick(COMPANY_SUFFIX);
                    }
                };
            }
        });

        register("date", "date(from=2020-01-01, to=today, format=yyyy-MM-dd)", new Factory() {
            public ValueGenerator create(List<String> a) {
                final LocalDate from = LocalDate.parse(arg(a, 0, "2020-01-01"));
                String toArg = arg(a, 1, "today");
                final LocalDate to = "today".equalsIgnoreCase(toArg) ? LocalDate.now(ZoneOffset.UTC) : LocalDate.parse(toArg);
                final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(arg(a, 2, "yyyy-MM-dd"), Locale.ROOT);
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) {
                        return from.plusDays(ctx.nextLong(0, to.toEpochDay() - from.toEpochDay())).format(fmt);
                    }
                    public Object[] edgeCases() {
                        return new Object[] {
                            from.format(fmt), to.format(fmt), from.minusDays(1).format(fmt), to.plusDays(1).format(fmt),
                            LocalDate.of(2024, 2, 29).format(fmt), "", "2023-02-30", "not-a-date"
                        };
                    }
                };
            }
        });

        register("datetime", "datetime(from=2020-01-01, to=today) - ISO-8601 UTC timestamp", new Factory() {
            public ValueGenerator create(List<String> a) {
                final LocalDate from = LocalDate.parse(arg(a, 0, "2020-01-01"));
                String toArg = arg(a, 1, "today");
                final LocalDate to = "today".equalsIgnoreCase(toArg) ? LocalDate.now(ZoneOffset.UTC) : LocalDate.parse(toArg);
                final long startSec = from.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
                final long endSec = to.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) - 1;
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) {
                        return LocalDateTime.ofEpochSecond(ctx.nextLong(startSec, endSec), 0, ZoneOffset.UTC) + "Z";
                    }
                    public Object[] edgeCases() {
                        return new Object[] {"", "1970-01-01T00:00:00Z", "9999-12-31T23:59:59Z", "2024-13-01T00:00:00Z", "not-a-timestamp"};
                    }
                };
            }
        });

        register("pattern", "pattern(ORD-####-??) - # digit, ? uppercase letter, * alphanumeric, \\ escapes", new Factory() {
            public ValueGenerator create(List<String> a) {
                final String pattern = arg(a, 0, "####");
                return new StringGen() {
                    public Object generate(GenContext ctx) {
                        String alnum = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < pattern.length(); i++) {
                            char c = pattern.charAt(i);
                            if (c == '\\' && i + 1 < pattern.length()) {
                                sb.append(pattern.charAt(++i));
                            } else if (c == '#') {
                                sb.append((char) ('0' + ctx.random().nextInt(10)));
                            } else if (c == '?') {
                                sb.append((char) ('A' + ctx.random().nextInt(26)));
                            } else if (c == '*') {
                                sb.append(alnum.charAt(ctx.random().nextInt(alnum.length())));
                            } else {
                                sb.append(c);
                            }
                        }
                        return sb.toString();
                    }
                };
            }
        });

        register("text", "text(minWords=3, maxWords=12) - lorem ipsum sentence", new Factory() {
            public ValueGenerator create(List<String> a) {
                final int min = (int) argLong(a, 0, 3);
                final int max = (int) argLong(a, 1, 12);
                return new StringGen() {
                    public Object generate(GenContext ctx) {
                        int n = ctx.nextInt(min, max);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < n; i++) {
                            if (i > 0) { sb.append(' '); }
                            sb.append(ctx.pick(WORDS));
                        }
                        if (sb.length() > 0) {
                            sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
                            sb.append('.');
                        }
                        return sb.toString();
                    }
                };
            }
        });

        register("creditCard", "creditCard - publicly documented gateway TEST card number", new Factory() {
            public ValueGenerator create(List<String> a) {
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) { return ctx.pick(TEST_CARDS); }
                    public Object[] edgeCases() {
                        return new Object[] {"", "4111111111111112", "4111 1111 1111 1111", "1234", "0000000000000000"};
                    }
                };
            }
        });

        register("ipv4", "ipv4 - RFC 5737 documentation-range address", new Factory() {
            public ValueGenerator create(List<String> a) {
                final String[] nets = {"192.0.2.", "198.51.100.", "203.0.113."};
                return new ValueGenerator() {
                    public Object generate(GenContext ctx) { return ctx.pick(nets) + ctx.nextInt(1, 254); }
                    public Object[] edgeCases() {
                        return new Object[] {"", "0.0.0.0", "255.255.255.255", "256.1.1.1", "1.2.3", "::1"};
                    }
                };
            }
        });
    }

    private Generators() {
    }

    // -------------------------------------------------------------- public

    /** Builds a generator from a spec such as {@code int(1,10)}. */
    public static ValueGenerator create(String spec) {
        String s = spec == null ? "" : spec.trim();
        String name = s;
        List<String> args = new ArrayList<String>();
        int open = s.indexOf('(');
        if (open >= 0) {
            if (!s.endsWith(")")) {
                throw new IllegalArgumentException("Unbalanced parentheses in type spec: " + spec);
            }
            name = s.substring(0, open).trim();
            String inner = s.substring(open + 1, s.length() - 1).trim();
            if (!inner.isEmpty()) {
                for (String part : inner.split(",", -1)) {
                    args.add(part.trim());
                }
            }
        }
        Factory f = FACTORIES.get(name);
        if (f == null) {
            throw new IllegalArgumentException("Unknown field type '" + name + "'. Known types: " + FACTORIES.keySet());
        }
        try {
            return f.create(args);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid arguments for '" + spec + "': " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid arguments for '" + spec + "': " + e, e);
        }
    }

    /** Type name -> one-line usage description, in registration order. */
    public static Map<String, String> describe() {
        return Collections.unmodifiableMap(DESCRIPTIONS);
    }

    // ------------------------------------------------------------- helpers

    private static void register(String name, String description, Factory f) {
        FACTORIES.put(name, f);
        DESCRIPTIONS.put(name, description);
    }

    private static Factory stringPick(final String[] values) {
        return new Factory() {
            public ValueGenerator create(List<String> a) {
                return new StringGen() {
                    public Object generate(GenContext ctx) { return ctx.pick(values); }
                };
            }
        };
    }

    /** Base for free-text generators: shares the generic hostile string edge cases. */
    abstract static class StringGen implements ValueGenerator {
        public Object[] edgeCases() { return STRING_EDGE_CASES.clone(); }
    }

    static String first(GenContext ctx) {
        Object v = ctx.get("firstName");
        return v != null ? v.toString() : ctx.pick(FIRST_NAMES);
    }

    static String last(GenContext ctx) {
        Object v = ctx.get("lastName");
        return v != null ? v.toString() : ctx.pick(LAST_NAMES);
    }

    static String slug(String s) {
        String r = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return r.isEmpty() ? "user" : r;
    }

    static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(Math.max(0, n));
        for (int i = 0; i < n; i++) { sb.append(c); }
        return sb.toString();
    }

    private static String arg(List<String> a, int i, String def) {
        return i < a.size() && !a.get(i).isEmpty() ? a.get(i) : def;
    }

    private static long argLong(List<String> a, int i, long def) {
        String v = arg(a, i, null);
        return v == null ? def : Long.parseLong(v);
    }
}
