---
description: "Table-in-out VGI functions consume a relation and stream one back, emitting per input batch — streaming relation transforms in Java."
---

# Table-in-out functions

<KindBanner kind="table-in-out" />

A **table-in-out** function consumes a relation and streams a relation
back — a batch-by-batch transform you'd rather express in Java than SQL:
`SELECT * FROM demo.echo((SELECT * FROM t))`.

The defining property: a table-in-out function emits **per input batch**. It sees a batch,
emits a batch, and never needs to hold the whole input. (When you *do* need the
whole input — sort, top-k — use a [buffering function](/functions/buffering)
instead.)

## The model

The easiest base is `PassthroughTIOFunction`, for functions whose **output
schema equals their input schema** (echo, filter, enrich-in-place). It supplies
the bind; you write the exchange — an object whose `onInputBatch()` runs once per
input batch.

<<< @/../examples/src/main/java/farm/query/vgi/examples/TableInOutExample.java{java}

```sql
SELECT n FROM demo.echo((SELECT * FROM demo.numbers(3))) ORDER BY n;  -- 0,1,2
```

## The ownership rule that bites everyone

The one subtlety in table-in-out is buffer ownership:

> You **cannot** `out.emit(input.root())`.

The framework `close()`s every root you emit, after writing it. But the input
root is owned by the reader and **reused for the next batch** — closing it
corrupts the stream (you'll see the second batch come back truncated or empty).

The fix, shown in the example, is to `TransferPair` the input vectors into a
fresh root that you own and emit:

```java
List<FieldVector> out = new ArrayList<>();
for (FieldVector v : in.getFieldVectors()) {
    TransferPair tp = v.getTransferPair(Allocators.root());
    tp.transfer();                       // move buffers; reader keeps its schema
    out.add((FieldVector) tp.getTo());
}
VectorSchemaRoot copy = new VectorSchemaRoot(out);
copy.setRowCount(in.getRowCount());
out.emit(copy);                          // emit() now owns and will close `copy`
```

`TransferPair` (not a row-by-row copy) is also what preserves dictionary-encoded
(e.g. `ENUM`) children correctly.

## Doing actual work

`onInputBatch` is where a real transform lives. To filter, build an output root
holding only the rows you keep. To enrich, append computed columns. To reshape,
emit a different (declared) schema. A few patterns from the reference worker:

- **`filter_by_setting`** — drop rows failing a session-configured predicate.
- **`repeat_inputs`** — emit each input batch N times.
- **`echo_witness`** — passthrough that also reports projection-pushdown witness
  columns.

When your output schema differs from the input, implement `TableInOutFunction`
directly (not `PassthroughTIOFunction`) and return the real schema from
`onBind()`.

## Projection pushdown

`metadata().withPushdown(true, false, false)` opts into projection pushdown:
the engine sends the set of columns actually needed, the framework narrows the
declared output schema, and `params.outputSchema()` in `createExchange` reflects
it — emit only those columns and no narrowing `PROJECTION` node sits above your
operator. Filter pushdown is intentionally off for table-in-out (the engine always runs a
`FILTER` node above the operator). See
[pushdown](/advanced/filter-projection-pushdown).

## Going further

The full table-in-out surface — projection witnesses, ordering modes, partitioned output,
batch-index tagging, cancellation — is in
[`vgi-example-worker/src/main/java/farm/query/vgi/example/tableinout/`](https://github.com/Query-farm/vgi-java/tree/main/vgi-example-worker/src/main/java/farm/query/vgi/example/tableinout) in the
[vgi-java repo](https://github.com/Query-farm/vgi-java). A few worth reading:

- [`filter_by_setting`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/tableinout/FilterBySettingFunction.java) — drop rows failing a session-configured predicate.
- [`repeat_inputs`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/tableinout/RepeatInputsFunction.java) — emit each input batch N times.
- [`echo_witness`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/tableinout/EchoWitnessFunction.java) — passthrough that also reports projection-pushdown witness columns.

Next: [aggregate functions →](/functions/aggregate)
