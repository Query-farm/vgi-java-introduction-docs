---
description: "Three levels of testing a VGI function in Java — from fast unit tests of your logic to end-to-end SQL assertions against Haybarn."
---

# Testing your function

There are three levels at which to test a VGI function, fastest to most
realistic. Use the lowest one that gives you confidence, and at least one
end-to-end check.

## 1. Unit-test the logic in JUnit (fastest)

A `ScalarFn`'s `compute()` is just a method over Arrow vectors. You can call it
directly — allocate the input and output vectors, populate the input, invoke
`compute`, and assert on the output. No worker, no engine, millisecond feedback.

```java
@Test
void upperCases() {
    try (var alloc = new RootAllocator();
         var in = new VarCharVector("value", alloc);
         var out = new VarCharVector("result", alloc)) {

        in.allocateNew();
        in.setSafe(0, "hi".getBytes(UTF_8));
        in.setNull(1);
        in.setValueCount(2);

        new ScalarExample().compute(in, out);   // call the method directly

        out.setValueCount(2);
        assertEquals("HI", new String(out.get(0), UTF_8));
        assertTrue(out.isNull(1));
    }
}
```

This is ideal for the row-level logic: null handling, edge cases, overflow. It
does **not** exercise bind, the wire, or argument coercion — that's the next
levels. (Table/aggregate/buffering logic is similarly unit-testable: build a
`VectorSchemaRoot`, call `update()`/`onInputBatch()`/`produceTick()` against a
small collector, and assert.)

::: tip Surface Arrow leaks
Run tests with `-Darrow.memory.debug.allocator=true`. Arrow then fails the test
at teardown if any buffer wasn't closed — catching a missing `close()` or a
double-`emit` immediately. See [JVM flags](/reference/jvm-flags).
:::

## 2. End-to-end against Haybarn (most realistic, still easy)

`uvx haybarn-cli` gives you a real engine with the `vgi` extension and no local
install. Pipe SQL, capture output, assert. This exercises the full path: ATTACH,
bind, argument coercion, the wire, your execution.

<!--@include: ../_snippets/install-haybarn.md-->

```bash
BIN="$PWD/build/install/vgi-java-examples/bin/vgi-java-examples"
out=$(printf "INSTALL vgi FROM community; LOAD vgi;
ATTACH 'demo' AS demo (TYPE vgi, LOCATION 'launch:$BIN');
SELECT demo.upper_case('hi');
DETACH demo;\n" | uvx haybarn-cli -noheader -list)
[ "$out" = "HI" ] && echo PASS || { echo "FAIL: got '$out'"; exit 1; }
```

`-list -noheader` makes Haybarn print bare, pipe-separated values that are easy to
diff in a shell or a test runner. This is the quickest way to assert real SQL
behavior, and it's what you'd wire into CI.

## 3. The sqllogictest harness (golden files)

For a suite of cases with expected results, the canonical vgi C++ harness runs
**sqllogictest** `.test` files — the same mechanism the upstream conformance
suite uses. A `.test` is SQL plus expected rows under `----`:

```
require-env VGI_TEST_WORKER
require vgi

statement ok
ATTACH 'demo' AS demo (TYPE vgi, LOCATION '${VGI_TEST_WORKER}');

query I
SELECT demo.upper_case('hi');
----
HI

statement ok
DETACH demo;
```

Point `VGI_TEST_WORKER` at your `launch:`-able worker and run it through the
`unittest` binary:

```bash
VGI_TEST_WORKER="launch:$PWD/build/install/vgi-java-examples/bin/vgi-java-examples" \
  ~/Development/vgi/build/release/test/unittest /path/to/your.test
```

::: warning Registered-tree caveat
The `unittest` binary only runs `.test` files that live under **its** registered
test directory (`~/Development/vgi/test/sql/...`). To run your own file, copy it
in, run, and remove it — the `examples/` project's `test/examples.test` is checked
this way. For most projects, levels 1 and 2 are enough; reach for the harness when
you want a large golden-file suite.
:::

## What to test at each level

| Concern | Level |
|---------|-------|
| Per-row logic, nulls, overflow, edge values | 1 (JUnit) |
| Bind: argument types, dynamic output type, rejection messages | 2 or 3 |
| Pushdown actually applied (LIMIT stops early, filters drop rows) | 2 or 3 |
| Aggregate `combine` correctness under grouping | 2 or 3 |
| Memory hygiene (no leaks) | 1, with the debug allocator |

A good default: JUnit for the logic, plus a handful of Haybarn assertions (level
2) in CI for the wire-level behavior.
