---
description: "Buffering VGI functions see every input row before emitting — the basis for sorts, top-k, and whole-relation work in Java."
---

# Buffering functions

<KindBanner kind="buffering" />

A **buffering** (Sink + Source) function must see **all** of its input before it
produces **any** output. That's the defining difference from a
[table-in-out](/functions/table-in-out) function, which emits per input batch.
Reach for buffering when the operation is inherently whole-relation: sort,
top-k, median, dedup, or an aggregation that emits many rows.

## The three-phase lifecycle

Implement `TableBufferingFunction`:

| Phase | Method | Role |
|-------|--------|------|
| **Sink** | `process(batch, params)` | stash each input batch, return an opaque `state_id` |
| *(end of input)* | `combine(stateIds, params)` | group the `state_id`s into the finalize streams the Source will drain |
| **Source** | `createFinalizeProducer(params)` | emit the buffered rows back out, one batch per tick |

State is stashed in `params.storage()` — a durable, **execution-scoped** key/value
and append-log store. Buffering through storage (rather than a field on your
function) is what lets the engine spread the Sink across parallel workers and
still have the Source see everything.

<<< @/../examples/src/main/java/farm/query/vgi/examples/BufferingExample.java{java}

```sql
SELECT n FROM demo.collect((SELECT * FROM demo.numbers(4))) ORDER BY n;  -- 0,1,2,3
```

## Walking the lifecycle

1. **`process`** is called once per input batch. Here it appends the batch's IPC
   bytes to an append-log namespace (`storage.stateAppend(ns, key, bytes)`) and
   returns the execution id as the `state_id` — so every batch of one execution
   lands in the same log.
2. **`combine`** runs once when input is exhausted. It returns the
   `finalize_state_id`s — one per output stream the Source will produce. The
   example returns a single stream keyed by the execution id.
3. **`createFinalizeProducer`** returns a producer (extending
   `BufferingFinalizeProducer`) whose `produceTick()` cursor-drains the log, one
   buffered batch per tick, until the log is empty and it calls `out.finish()`.

`BufferingFinalizeProducer.emitProjected(full, out)` narrows each buffered batch
to the projected columns and applies pushed-down filters before emitting — the
same projection/filter handling the engine expects, for free.

## Where real work goes

The example is a passthrough (`collect`) so the lifecycle is visible. A *useful*
buffering function does its work where it has the whole picture:

- **In `process`/`combine`** for incremental reductions — e.g. accumulate a
  running sum or a heap of the top-K rows in storage as batches arrive, then have
  the Source emit just the result.
- **In the finalize producer** for whole-relation passes — e.g. read all buffered
  batches in `produceTick`, sort them, and emit in order.

`storage` gives you more than an append-log: scoped key/value blobs, atomic int64
counters, ranged scans, and a FIFO work-queue — enough to implement a distributed
sort or a streaming top-K. See the `accumulate` fixtures in the vgi-java repo.

## Storage notes

- **Execution-scoped.** `params.storage()` is pinned to one execution; keys you
  write are invisible to other queries and cleaned up after.
- **Namespaces starting `_vgi/` are reserved** for the framework. Use your own.
- **Order isn't guaranteed across parallel sinks** unless you ask. Override
  `sinkOrderDependent()`/`sourceOrderDependent()`/`requiresInputBatchIndex()` to
  force ordered ingest or draining, or to receive the engine's global batch index
  in `process`.

## Going further

[`vgi-example-worker/src/main/java/farm/query/vgi/example/buffering/`](https://github.com/Query-farm/vgi-java/tree/main/vgi-example-worker/src/main/java/farm/query/vgi/example/buffering) has the full
set: [`SumAllColumns`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/buffering/SumAllColumnsBufferingFunction.java) (a real reduction), [`DistributedSum`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/buffering/DistributedSumBufferingFunction.java), ordered and
batch-indexed variants, large-state and crash/cancellation fixtures, plus the
[`AbstractBufferAndDrain`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/buffering/AbstractBufferAndDrain.java) helper this example is modeled on.

That's all five kinds. Next: [parallelism →](/advanced/parallelism)
