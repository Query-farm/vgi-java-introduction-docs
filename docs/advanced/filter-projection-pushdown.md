---
description: "How filter and projection pushdown let DuckDB push work into your VGI worker so it scans and returns less data."
---

# Filter & projection pushdown

Pushdown lets the engine move work *into* your function so it returns less data.
There are three kinds, and a function opts into each explicitly via metadata.

```java
metadata().withPushdown(projection, filter, limit)   // three booleans
```

| Pushdown | What the engine pushes | What you do with it |
|----------|------------------------|---------------------|
| **projection** | the set of columns actually needed | emit only those columns |
| **filter** | predicates like `n > 100`, `s LIKE 'a%'` | skip rows that can't match |
| **limit** | a row cap (`LIMIT 7`) | stop producing early |

Pushing work down is purely an optimization: if you ignore a pushed filter,
the engine still applies it above your operator, so results stay correct — you just
moved more bytes than necessary.

## Filter & limit pushdown (table functions)

The [`numbers`](/function-kinds/table) example opts into filter and limit pushdown:

```java
@Override public FunctionMetadata metadata() {
    return FunctionMetadata.describe("Generate the integers 0..count-1")
            .withPushdown(false, true, false)   // projection=off, filter=on, limit handled
            .withCategories("generator");
}
```

In `createProducer`, build a `FilterApplier` from the pushed predicates and the
join keys, and hand it to the batch loop:

```java
FilterApplier filters = FilterApplier.from(params.pushdownFilters(), params.joinKeys());
// …
BatchUtil.produceBatch(batch, OUTPUT_SCHEMA, filters, out, (root, n, start) -> { … });
```

`BatchUtil.produceBatch` applies the filter to each emitted batch, and a `LIMIT`
above the scan stops `produceTick` from being called once the cap is met —
verified by the example test:

```sql
SELECT count(*) FROM (SELECT * FROM demo.numbers(1000000) LIMIT 7);   -- 7, not 1000000
```

The reference worker's `FilterEcho` / `DynamicFilterEcho` functions echo the
`pushed_filters` back as a column so you can *see* exactly what the optimizer
pushed for a given query — a great debugging aid.

## Projection pushdown (table-in-out & buffering)

For [table-in-out](/function-kinds/table-in-out) and
[buffering](/function-kinds/buffering), the useful pushdown is **projection** — emit
only the columns the query selects:

```java
metadata().withPushdown(true, false, false)   // projection on
```

The framework narrows your declared output schema to the requested columns. In a
TIO exchange, `params.outputSchema()` reflects the narrowed set — select those
columns by name when you build the output batch (the
[echo example](/function-kinds/table-in-out) does this). In a buffering finalize
producer, `BufferingFinalizeProducer.emitProjected` narrows each batch for you.

When projection pushdown is on, no narrowing `PROJECTION` node is planned above
your operator — the saving is real, not cosmetic.

## Which pushdown for which kind?

| Kind | projection | filter | limit |
|------|:----------:|:------:|:-----:|
| Scalar | — | — | — |
| Table | ✓ | ✓ | ✓ |
| Table-in-out | ✓ | (engine runs a FILTER node) | — |
| Buffering | ✓ | ✓ (in the finalize producer) | — |

Start with everything off (correct, just not minimal), then opt into the kinds
your function can exploit. The
[vgi-java repo](https://github.com/Query-farm/vgi-java) has a dedicated
`filter_pushdown/` test group covering every predicate subtype.

Next: [CLI & environment reference →](/reference/cli-and-env)
