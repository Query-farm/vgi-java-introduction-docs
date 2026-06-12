---
description: "Write a DuckDB aggregate function in Java with VGI — fold many rows into one value per group, with parallel partial aggregation."
---

# Aggregate functions

<KindBanner kind="aggregate" />

An **aggregate** collapses many rows into one value per group:
`SELECT g, demo.vgi_sum(v) ... GROUP BY g`. VGI aggregates are built for DuckDB's
**parallel, partial** aggregation model, which is why the interface has four
methods rather than one.

## The four methods

Implement `AggregateFunction<State>`:

| Method | Role |
|--------|------|
| `newState()` | create a fresh, empty per-group accumulator |
| `update(states, groupIds, batch)` | fold a batch of rows into the accumulators |
| `combine(target, source)` | merge two partial accumulators |
| `finalize(result, rowIndex, state)` | write a group's accumulator as the output value |

`combine()` exists because the engine may aggregate **in parallel**: several threads
(or processes) each build partial `State`s over a slice of the data, then merge
them. Your `State` is `Serializable` so a partial can cross a process boundary.
Keep it small.

<<< @/../examples/src/main/java/farm/query/vgi/examples/AggregateExample.java{java}

```sql
SELECT g, demo.vgi_sum(v)
  FROM (VALUES (1,10),(1,20),(2,5)) t(g,v) GROUP BY g ORDER BY g;   -- 1->30, 2->5
```

## How the pieces fit

![Several threads each fold rows into a partial State via update(); combine() merges them into one State; finalize() writes the result value.](/diagrams/partial-aggregation.svg)

- **`update`** is the hot loop. `groupIds[i]` is row `i`'s group; mint an
  accumulator with `states.computeIfAbsent(gid, k -> new State())`. Read the
  input columns from `batch.getFieldVectors()`.
- **`combine`** must be associative and commutative — it's called in an
  unspecified order across partials.
- **`finalize`** writes one output row. The output column is the
  `outputSchema()` you declared (here a single `result BIGINT`).

## Arguments and output

The `FunctionSpec` declares the input arguments and `outputSchema()` the result:

```java
private static final FunctionSpec SPEC = FunctionSpec.builder("vgi_sum")
        .description("Sum integer values")
        .arg("value", Schemas.INT64)        // one input column
        .build();
```

A **nullary** aggregate (like `vgi_count()`) declares no `arg` and counts rows.
Override `finalizeEmpty(result, rowIndex)` to control the empty-group result —
`COUNT` returns `0` there, while `SUM` returns `NULL` (the default):

```java
@Override public void finalizeEmpty(FieldVector result, int rowIndex) {
    ((BigIntVector) result).setSafe(rowIndex, 0L);   // count of nothing is 0
}
```

## Correctness notes

- **Guard overflow.** `Math.addExact` / `Math.multiplyExact` turn a silent wrap
  into a clear `vgi_sum: int64 overflow` error.
- **Don't stash Arrow vectors in `State`.** The input batch is reused; copy out
  the scalar values you need. (`State` is serialized — it must be plain data.)
- **`combine` may run before or after any `update`.** Never assume an order.

## Going further

[`vgi-example-worker/src/main/java/farm/query/vgi/example/aggregate/`](https://github.com/Query-farm/vgi-java/tree/main/vgi-example-worker/src/main/java/farm/query/vgi/example/aggregate) has richer
aggregates: [`Avg`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/aggregate/AvgFunction.java) (a two-field running state), [`ListAgg`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/aggregate/ListAggFunction.java) (a growing list state),
and [`Count`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/aggregate/CountFunction.java). They all follow the same four-method shape.

Next: [buffering functions →](/function-kinds/buffering)
