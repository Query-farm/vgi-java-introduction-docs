---
description: "The three parts of every VGI worker — the builder that registers functions, the functions themselves, and the main() that serves them."
---

# Anatomy of a worker

Every VGI worker has the same three parts: a **builder** that registers your
functions and the catalog metadata around them, a **transport** that carries the
engine's calls in and your Arrow batches back, and the **functions** themselves.
The functions are where your code goes, and they get a [page
each](/functions/scalar). This page is the other two — the scaffolding that looks
the same in every worker you'll write.

## The builder

A worker starts at `Worker.builder()`. You name the catalog, register a function
for each thing you want callable from SQL, and hand off to a transport — the
whole worker is one chain:

```java
Worker.builder()
    .catalogName("demo")                       // the ATTACH name and default alias
    .catalogComment("My example catalog")
    .registerScalar(new UpperCase())           // one scalar
    .registerTable(new Numbers())              // one table function
    .registerAggregate(new VgiSum())           // one aggregate
    .registerTableInOut(new Echo())            // one table-in-out
    .registerTableBuffering(new Collect())     // one buffering function
    .runFromArgs(args);
```

There are `registerScalars`/`registerTables`/… plural forms that take any
`Iterable`, plus hooks for views, macros, catalog tables, settings, secret
types, attach options, and versioning. The introductory set is the five
`register*` calls above; the rest is in the
[vgi-java reference worker](https://github.com/Query-farm/vgi-java).

Here is the worker the examples actually run, with one of every kind registered
at once:

<<< @/../examples/src/main/java/farm/query/vgi/examples/AllInOneWorker.java{java}

## Transports

`runFromArgs(args)` is the canonical CLI dispatcher. It reads a handful of flags
and picks a transport — the worker side of the three `LOCATION` schemes you
[chose from in SQL](/intro/what-is-vgi#running-it-with-haybarn-or-duckdb):

| Invocation | Transport | When it's used |
|------------|-----------|----------------|
| *(no args)* | **stdio** | the engine forks the worker and talks over stdin/stdout |
| `--unix <path> --idle-timeout <s>` | **AF_UNIX socket** | the `launch:` scheme — a pooled, long-lived worker |
| `--http [--host h] [--port p]` | **HTTP** | a standalone, network-reachable worker |

In `AF_UNIX` mode the worker prints `UNIX:<path>` to stdout once the listener is
bound; in HTTP mode it prints `PORT:<n>`. The `launch:` client reads that line to
discover the worker.

You rarely call these flags yourself — `launch:` appends `--unix` and
`--idle-timeout` for you. To run a worker by hand for debugging:

```bash
bin/vgi-java-examples --unix /tmp/demo.sock --idle-timeout 60
# prints: UNIX:/tmp/demo.sock
```

### Local, remote, or in the browser

`stdio` and `AF_UNIX` run the worker on the *same machine* as the engine. **HTTP
doesn't have to.** Point a `LOCATION` at `http://host:port` and the worker can
live on another machine entirely, so a single SQL query reaches across the network
to run your Java — one worker, or a pool of them behind a load balancer. (Shared
memory is local-only, so it doesn't apply over HTTP.)

That reach extends to the **browser**. The `vgi` extension itself compiles to
WebAssembly, so a [DuckDB-Wasm](https://duckdb.org/docs/api/wasm/overview)
SQL engine running in a web page can `ATTACH` a worker over HTTP. The Java runs
wherever you host it; the browser-side SQL calls it like any other function. (A
browser can't open a Unix socket or fork a subprocess, so the WASM path always
uses HTTP.)

## The request lifecycle

For each function, the engine drives a small RPC conversation:

![Sequence: the engine calls init (worker returns catalog + capabilities), then bind per call site (worker returns the output schema), then execute (input and output Arrow batches stream both ways).](/diagrams/rpc-lifecycle.svg)

1. **`init`** — the worker advertises its catalog, functions, protocol version,
   and transport capabilities (including whether it can use
   [shared memory](/advanced/shared-memory)).
2. **`bind`** — for a specific call site, the engine sends the argument types and
   input schema; the worker returns the **output schema**. This is where
   argument validation and dynamic output types happen.
3. **execute** — the engine streams Arrow input batches (if any) and pulls Arrow
   output batches. Scalars and table-in-out functions process batch-by-batch;
   table functions produce batches; aggregates fold then finalize; buffering
   functions sink-then-source.

The base classes (`ScalarFn`, `CountdownTableFunction`, `PassthroughTIOFunction`,
…) implement `init`/`bind` for you from your method signatures and schema
declarations, so most functions only write execution logic.

## Allocators and memory

VGI uses Arrow's off-heap memory. Two rules cover almost everything:

- **Allocate from the shared root.** Use `Allocators.root()` (or a child of it)
  when you build vectors to emit. The examples do this via the framework helpers
  (`BatchUtil`, `TransferPair`) so you rarely touch it directly.
- **`emit()` takes ownership.** `OutputCollector.emit(root)` adopts the root and
  closes it after writing. Do **not** close a root you've emitted; do close roots
  you read and don't emit.

Get these wrong and you'll see Arrow allocator-leak errors at shutdown (the test
JVM turns those into hard failures) — a useful early warning, not a silent bug.

Next: write your first [scalar function →](/functions/scalar)
