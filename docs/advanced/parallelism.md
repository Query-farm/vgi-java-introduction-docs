---
description: "The two axes of concurrency in a VGI worker — per-connection virtual threads and parallel scans — and how to tune them."
---

# Parallelism

There are two independent axes of concurrency in a VGI worker:

1. **Across queries / connections** — many SQL statements (or many engine
   processes) hitting one worker at once.
2. **Within a single scan** — the engine running one table function on several
   threads in parallel.

## Across connections: virtual threads

In `AF_UNIX` (`launch:`) and HTTP mode, the worker accepts connections and
dispatches **each on its own virtual thread**:

```java
ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
```

Virtual threads make a connection essentially free, so one worker comfortably
serves many concurrent clients. This is what makes `launch:` a true shared pool:
several engine processes on the machine attach to the *same* worker socket (via
flock coordination) and are served concurrently.

The practical consequence for you: **your function code can run on many threads
at once.** Anything shared across instances must be thread-safe.

- A fresh `ScalarFn` / table producer / aggregate `State` is created per call or
  per execution, so per-call state is naturally isolated.
- The framework keeps thread-local output buffers (so scalar dispatch allocates
  nothing steady-state) — those are per-thread, not shared, by construction.
- If *you* introduce shared mutable state (a cache, a connection pool, a
  counter), guard it. The function objects you register are singletons; their
  fields are shared across every concurrent call.

## Within a scan: `maxWorkers`

A table function defaults to a single scan worker (`maxWorkers()` returns 1), and
a producer counting `0..count-1` is correct. Raise `maxWorkers()` and the engine
may scan the function on several threads — and **each thread gets its own producer.**
A producer that still counts from 0 re-emits the whole range once per thread:

```sql
-- numbers() with maxWorkers=4 but no coordination:
SELECT count(*), count(DISTINCT n) FROM demo.numbers(10000000);
-- 40000000, 10000000   ← every value emitted 4×
```

That's measured, not hypothetical. The framework does **not** auto-partition;
your producers have to coordinate.

The lever: every parallel producer of one scan shares the same `execution_id`, so
`params.storage()` hands them all the *same* [`BoundStorage`](/guides/catalog).
An atomic counter there is a single cursor they each draw disjoint chunks from:

```java
@Override public long maxWorkers() { return 4L; }   // allow parallel scan threads

@Override public TableProducerState createProducer(TableInitParams p) {
    // params.storage() is scoped to the execution_id every worker shares
    return new State(count, batchSize, p.storage());
}

// produceTick(): claim the next chunk atomically, then emit just those rows
long end   = storage.counterAdd(CURSOR_NS, CURSOR_KEY, batchSize);  // post-add value
long start = end - batchSize;
if (start >= count) { out.finish(); return; }                       // cursor passed the end
// … emit rows start .. start + min(batchSize, count - start) - 1 …
```

With the shared counter, the same query is exact:

```sql
SELECT count(*), count(DISTINCT n) FROM demo.numbers(10000000);
-- 10000000, 10000000   ← every value once, no gaps
```

The [`numbers`](/function-kinds/table) example coordinates exactly this way; its
end-to-end test asserts `count == count(DISTINCT) == N` (and the exact sum) over a
5M-row parallel scan. Only raise `maxWorkers()` once your producer partitions like
this — the default of 1 needs no coordination. (`storage` also exposes a FIFO
queue, for handing out work items that aren't a simple contiguous range.)

## Aggregates are parallel by construction

Aggregate functions are designed for parallel partial aggregation: the engine
builds several partial `State`s on different threads and merges them with `combine()`.
You don't opt in — you just make `combine` associative and commutative and keep
`State` serializable. See [aggregates](/function-kinds/aggregate).

## Buffering and parallel sinks

A buffering function's Sink phase (`process`) can be driven by multiple threads;
that's why state lives in `params.storage()` rather than on the function. If your
buffering logic needs ordered ingest, override `sinkOrderDependent()` (and
`requiresInputBatchIndex()` to receive the engine's global batch index). See
[buffering](/function-kinds/buffering).

## A mental model

| Construct | Concurrency it introduces | Your obligation |
|-----------|---------------------------|-----------------|
| Multiple connections | per-connection virtual thread | make shared fields on registered functions thread-safe |
| `maxWorkers() > 1` | parallel scan threads on one table function | partition the producer or share state safely |
| Aggregate `combine` | parallel partial states merged | keep `combine` associative/commutative |
| Buffering Sink | parallel `process` calls | buffer through `storage`, not fields |

Next: [shared-memory transport →](/advanced/shared-memory)
