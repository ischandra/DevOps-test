# Synthetic Test Data Generator

A zero-dependency Java tool (Java 8+) that generates realistic, **reproducible** test data
for test automation, as a CLI (CSV / JSON / JSONL / SQL) or straight from your test code.

- **Deterministic:** the same `--seed` always gives the same data, so a failing test can be replayed exactly.
- **Consistent records:** `email`, `fullName` and `username` reuse the record's `firstName`/`lastName`, and `state` matches `city`.
- **Negative testing:** edge-case injection (boundaries, off-by-one, empty, very long, unicode, SQL-injection and XSS strings, malformed emails and dates).
- **Safe by design:** emails use `example.*` domains only, phones use the fictional 555 exchange, IPs come from RFC 5737 documentation ranges, and card numbers are public gateway test numbers. Nothing can match a real person.
- **Streams to disk:** millions of rows can be written without holding them in memory.

## Quick start

```bash
mvn package
JAR=target/sample-0.0.1-SNAPSHOT.jar

java -jar $JAR --schema customer --count 1000 --seed 42 --out target/testdata/customers.csv
java -jar $JAR --schema order    --count 5000 --out target/testdata/orders.jsonl     # format from extension
java -jar $JAR --schema login    --count 200  --format sql --table app_users
java -jar $JAR --schema login    --count 200  --edge-rate 0.2 -f json                # fuzz a login form
java -jar $JAR --list-types
java -jar $JAR --help
```

Bundled schemas (in `src/main/resources/schemas/`): `customer`, `order`, `login`, `product`.
`--schema` also accepts a path to your own `.schema` file.

## Schema format

One field per line, `name: type(args) | modifiers`. `#` starts a comment.
Fields are generated top to bottom, so declare `firstName`/`lastName` before `email`, and `city` before `state`.

```text
# my-api-user.schema
id:        sequence(1000)
firstName: firstName
lastName:  lastName
email:     email                          | unique
age:       int(18, 90)                    | null=0.05
plan:      oneOf(FREE:70, PRO:25, ENTERPRISE:5)
balance:   decimal(0, 10000, 2)           | edge=0.1
ref:       pattern(REF-####-??)
signupAt:  datetime(2024-01-01, today)
```

### Modifiers

| Modifier | Effect |
|---|---|
| `null=0.1` | 10% of values are null (empty in CSV, `NULL` in SQL) |
| `edge=0.2` | 20% of values are replaced with that type's boundary or hostile values. Overrides `--edge-rate` for that field |
| `unique` | No repeated values. Fails with a clear error if the type has too few possible values. Edge cases are never injected into unique fields |

### Field types

| Type | Notes |
|---|---|
| `sequence(start=1, step=1)` | Incrementing number |
| `uuid` | Random v4 UUID |
| `int(min=0, max=1000)` | Inclusive range. Edge cases: min, max, min-1, max+1, 0 |
| `decimal(min=0, max=1000, scale=2)` | Fixed-scale decimal (numeric in JSON/SQL) |
| `boolean(trueProbability=0.5)` | |
| `oneOf(A, B, C)` / `oneOf(A:70, B:30)` | Uniform or weighted choice |
| `constant(value)` | Always the same value |
| `firstName`, `lastName`, `fullName` | Diverse international name pool |
| `email(domain?)` | `first.last[NNN]@example.*` |
| `username`, `password(length=12)` | Password always contains upper, lower, digit and symbol |
| `phone` | `+1-NNN-555-01NN` |
| `street`, `city`, `state`, `zip`, `country`, `address` | US-style addresses |
| `company` | Fictional company names |
| `date(from, to=today, format=yyyy-MM-dd)` | Any `DateTimeFormatter` pattern |
| `datetime(from, to=today)` | ISO-8601 UTC, e.g. `2025-03-01T10:15:00Z` |
| `pattern(ORD-###-??)` | `#` digit, `?` A–Z, `*` alphanumeric, `\` escapes the next character |
| `text(minWords=3, maxWords=12)` | Lorem-ipsum sentence |
| `creditCard` | Public gateway test card numbers only |
| `ipv4` | 192.0.2.x / 198.51.100.x / 203.0.113.x |

## Using it from test code (JUnit, TestNG, Selenium, REST Assured…)

```java
import com.devops.test.datagen.core.*;

// From a bundled or file schema
List<Map<String, Object>> customers = DataGenerator.of(Schema.resolve("customer"))
        .seed(42)            // log the seed; re-use it to reproduce a failure
        .generate(100);

// Inline, no file needed
Schema signup = Schema.builder("signup")
        .field("firstName", "firstName")
        .field("lastName",  "lastName")
        .field("email",     "email | unique")
        .field("password",  "password(16)")
        .field("age",       "int(13, 120) | edge=0.25")   // 25% boundary values for validation tests
        .build();
Map<String, Object> user = DataGenerator.of(signup).seed(7).generateOne();

// Custom generator for anything domain-specific
Schema.builder("acct")
        .field("iban", ctx -> "GB" + ctx.nextInt(10, 99) + "TEST" + ctx.nextLong(10_000_000L, 99_999_999L))
        .build();
```

If you don't set a seed, a random one is used. The CLI prints it to stderr, and `DataGenerator#seed()` returns it,
so you can log it in your test report.

## In the Jenkins pipeline

Add a stage before your tests run, and archive the data next to the results:

```groovy
stage('Generate Test Data') {
    steps {
        sh 'mvn -q -DskipTests package'
        sh 'java -jar target/sample-*-SNAPSHOT.jar -s customer -n 1000 --seed ${BUILD_NUMBER} -o target/testdata/customers.csv'
        sh 'java -jar target/sample-*-SNAPSHOT.jar -s order    -n 5000 --seed ${BUILD_NUMBER} -o target/testdata/orders.sql'
        archiveArtifacts artifacts: 'target/testdata/**', fingerprint: true
    }
}
```

Using `BUILD_NUMBER` as the seed gives each build different data, and any build's data can be regenerated later.

> **Note:** the generator needs Java 8+. `Jenkinsfile.txt` currently pins `jdk 'Java 1.7.0'`; point it at a Java 8+ JDK tool.

## Extending

Add a new type in `Generators.java` with `register("name", "description", factory)`.
To support negative testing, override `edgeCases()`. It's then picked up by `--list-types` and by the
`testEveryRegisteredTypeWorksWithDefaults` test automatically.
