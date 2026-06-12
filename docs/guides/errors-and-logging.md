---
description: "How errors surface to SQL users in VGI, and the three logging channels a Java worker has for diagnostics."
---

# Errors & logging

How failures reach the user, and the three logging channels you have.

## Errors become SQL errors

When your function throws, the framework propagates the exception to the engine
and it surfaces as a **SQL error** carrying your message. So the message *is* the
user experience — make it specific and function-named:

```java
try {
    s.total = Math.addExact(s.total, b.get(i));
} catch (ArithmeticException e) {
    throw new IllegalArgumentException("vgi_sum: int64 overflow", e);
}
```

```
Error: vgi_sum: int64 overflow
```

### Fail at bind time, not mid-stream

Validate arguments in `onBind()` (or `argumentSpecs`/`outputType`) so bad calls
are rejected **before** any rows are produced — a cleaner error and no wasted
work. `ParameterExtractor` gives you typed, well-worded validation for free:

```java
@Override public BindResponse onBind(TableBindParams params) {
    ParameterExtractor p = ParameterExtractor.of(params.arguments());
    p.positional(0, "count").asLong().notNull();      // bind-time error if missing/null
    p.named("batch_size").asLong().ge(1).notNull();
    return super.onBind(params);
}
```

For scalars, a `typeBound` on a `@Vector(any = true)` argument produces a
SQL-typed bind error automatically, e.g.
`double: value must be numeric (got VARCHAR)`. See [scalar](/functions/scalar).

A useful split: **bind** errors are for "this call can never work" (wrong types,
missing args); **execution** errors are for "this data can't be processed"
(overflow, a parse failure on row N).

## Three logging channels

| Channel | Goes to | Use for |
|---------|---------|---------|
| **SLF4J** (your logger) | the worker's stderr | worker-side diagnostics, request traces |
| **`clientLog`** | the engine's `duckdb_logs()` | messages the *user* should see for their query |
| **Debug env vars** | the worker's stderr | transport-level tracing (shm) |

### 1. Worker-side logging (SLF4J)

VGI uses [SLF4J](https://www.slf4j.org); add any binding (the examples use
`slf4j-simple`). Log as normal:

```java
private static final Logger log = LoggerFactory.getLogger(MyFunction.class);
// …
log.info("scanning {} rows for {}", count, ctx.requestId());
```

Under the `launch:` transport the launcher redirects the worker's fd 2 to
`/dev/null`, so set **`VGI_WORKER_STDERR`** to capture it:

```bash
VGI_WORKER_STDERR=/tmp/worker.log   # in the worker's environment
```

This is the first place to look when a `launch:` worker misbehaves.

### 2. Client-visible logging (`clientLog`)

To surface a message to the *user* — it shows up in the engine's `duckdb_logs()` —
use the call context (or the output collector in a producer):

```java
// in a process/combine/exchange method that has a CallContext `ctx`:
ctx.clientLog(Level.INFO, "buffered " + n + " batches");

// in a table producer's produceTick(out, ctx):
out.clientLog(new Message(Level.INFO, "generation complete", null));
```

```sql
SELECT * FROM demo.my_fn(...);
SELECT message FROM duckdb_logs() WHERE message LIKE '%buffered%';
```

Use this sparingly — for progress or diagnostics the query author would actually
want, not for worker-internal noise (that's SLF4J's job).

### 3. Transport debug

`VGI_RPC_SHM_DEBUG=1` logs shared-memory attach/resolve/fallback and a
per-connection CPU `timeline` to the worker's stderr — handy when checking
whether shm is engaging. See [shared memory](/advanced/shared-memory).

## Debugging a `launch:` worker

`launch:` workers are long-lived and pooled, which has two implications:

- **To see a crash:** set `VGI_WORKER_STDERR=/tmp/w.log`; without it, fd 2 is
  `/dev/null` and the stack trace is lost.
- **After a rebuild:** the pool keeps serving the *old* binary until it exits.
  `pkill -f <your main class>` (or wait out `--idle-timeout`) before re-attaching,
  or new functions appear missing and old ones run stale code.

See [CLI & environment](/reference/cli-and-env) for the full env-var list.
