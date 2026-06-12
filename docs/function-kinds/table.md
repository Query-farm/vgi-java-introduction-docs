---
description: "Write a DuckDB table function in Java with VGI — generate or scan rows from arguments, streamed in batches in flat memory."
---

# Table functions

<KindBanner kind="table" />

A **table function** produces rows from its arguments:
`SELECT * FROM demo.numbers(1000)`. Use it for generators, or to surface an
external data source (a file format, an API, a proprietary store) as a scannable
table.

## The model

The easiest base is `CountdownTableFunction` — for functions that emit a known
number of rows in fixed-size batches. It gives you the `count` positional
argument and the `batch_size := 2048` named argument for free. You declare:

- `outputSchema()` — the columns you emit.
- `createProducer()` — per-execution state whose `produceTick()` emits one batch
  per call and calls `out.finish()` when drained.

<<< @/../examples/src/main/java/farm/query/vgi/examples/TableExample.java{java}

```sql
SELECT * FROM demo.numbers(5) ORDER BY n;             -- 0,1,2,3,4
SELECT count(*) FROM demo.numbers(1000000);           -- streamed, flat memory
SELECT count(*) FROM (SELECT * FROM demo.numbers(1e9) LIMIT 7);  -- 7 (LIMIT pushdown)
```

## Why a producer + ticks?

Table functions **stream**. Instead of building the whole result in memory,
`produceTick()` is called repeatedly and emits one batch at a time. The engine
pulls batches as it consumes them, so a billion-row `numbers()` runs in constant
memory and a `LIMIT 7` above it stops the scan after the first batch.

## Parallel-safe by design

`numbers` sets `maxWorkers()` to 4, so the engine can scan it on several threads.
Each thread gets its *own* producer — which is why a naive generator counting
from 0 would emit the whole range once per thread. The producers avoid that by
coordinating through a shared atomic counter in `params.storage()`: every
`produceTick()` claims the next disjoint `[start, start + batch_size)` chunk with
`counterAdd`, so their union covers `0..count-1` exactly once. All parallel
producers of one scan share the same `execution_id`, which is what makes them
share one counter.

See [parallelism](/advanced/parallelism) for the measured before/after — and why
you must coordinate (or leave `maxWorkers` at 1) rather than hope.

## Arguments

`CountdownTableFunction` declares `count` + `batch_size` automatically. To add
your own named arguments, override `extraArgs()`:

```java
@Override protected List<ArgSpec> extraArgs() {
    return List.of(ArgSpec.named("increment", Schemas.INT64, "1"));   // default 1
}
```

Read them in `createProducer` with a `ParameterExtractor`, which validates and
coerces:

```java
ParameterExtractor p = ParameterExtractor.of(params.arguments());
long count     = p.positional(0, "count").asLong().required();
long batchSize = p.named("batch_size").asLong().ge(1).orElse(2048L);
long increment = p.named("increment").asLong().ge(1).orElse(1L);
```

Validate in `onBind()` too, so bad arguments are rejected before any rows are
produced:

```java
@Override public BindResponse onBind(TableBindParams params) {
    ParameterExtractor p = ParameterExtractor.of(params.arguments());
    p.positional(0, "count").asLong().notNull();
    p.named("batch_size").asLong().ge(1).notNull();
    return super.onBind(params);
}
```

## Pushdown

`metadata().withPushdown(projection, filter, limit)` opts a table function into
each kind of pushdown. The example opts into **filter/limit** pushdown, so the
producer receives the engine's predicates (via
`FilterApplier.from(params.pushdownFilters(), params.joinKeys())`) and `LIMIT`
short-circuits the stream. See
[filter & projection pushdown](/advanced/filter-projection-pushdown).

## Cardinality & statistics

`CountdownTableFunction` reports `count` as the cardinality estimate, and for a
single arithmetic column it supplies min/max statistics automatically — both
help the optimizer plan joins and orderings. Override `cardinality()` /
`statistics()` for other shapes.

## Beyond countdown

`CountdownTableFunction` is one base; the underlying interface is
`TableFunction` (via `SimpleTableFunction`). For a fully custom generator — one
backed by a file scan, an HTTP cursor, row-ids, sampling, or multiple output
branches — implement those directly. The
[vgi-java repo](https://github.com/Query-farm/vgi-java) has many:
[`MakeSeries`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/table/MakeSeriesFunctions.java),
[`FilterEcho`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/table/FilterEchoFunction.java),
[`RffRowidScan`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/table/RffRowidScanFunction.java) (real row-id generator),
`required_field_filter_paths` tables delegating to native `read_parquet`, and
the multi-branch scan fixtures.

Next: [table-in-out functions →](/function-kinds/table-in-out)
