---
layout: home
description: "Expose Java libraries as DuckDB SQL functions and tables over Apache Arrow. VGI for Java — scalar, table, aggregate, and streaming functions, served from your own process."

hero:
  name: VGI for Java
  text: Turn Java code into SQL functions and tables
  tagline: Expose Java libraries as SQL functions and tables, via Apache Arrow.
  image:
    src: /vgi-logo.png
    alt: Vector Gateway Interface
  actions:
    - theme: brand
      text: Quickstart
      link: /intro/quickstart

features:
  - title: An extension for the database, but in Java
    details: A separate Java process the engine attaches and calls like a built-in — your functions and tables, served over Apache Arrow. An alternative to writing a C++ extension, without leaving the JVM or its libraries.
    link: /intro/what-is-vgi
    linkText: What is VGI?
  - title: Runs anywhere
    details: Plain Java on JDK 21–25, reached over a Unix socket, a pipe, or HTTP — across the network, or even from a browser via WebAssembly.
    link: /intro/anatomy-of-a-worker#local-remote-or-in-the-browser
    linkText: Transports
  - title: Coding Agents can help you build
    details: The point is your Java, reachable from SQL — coding agents just get you there faster. The repo ships an agent pack (AGENTS.md, a recipe per kind, and skeletons) so an agent can add a function and verify it end-to-end against a real engine.
    link: /agents/
    linkText: The agent pack
---

<div class="built-on">
  <span class="built-on-label">Built on</span>
  <a class="logo-java" href="https://dev.java" aria-label="Java">
    <img src="/logos/java.svg" alt="Java" />
  </a>
  <a class="logo-arrow" href="https://arrow.apache.org" aria-label="Apache Arrow">
    <img class="light-only" src="/logos/apache-arrow-light.svg" alt="Apache Arrow" />
    <img class="dark-only" src="/logos/apache-arrow-dark.svg" alt="Apache Arrow" />
  </a>
  <a class="logo-haybarn" href="https://github.com/Query-farm-haybarn" aria-label="Haybarn">
    <img src="/logos/haybarn.png" alt="Haybarn" />
  </a>
  <a class="logo-duckdb" href="https://duckdb.org" aria-label="DuckDB">
    <img class="light-only" src="/logos/duckdb-light.svg" alt="DuckDB" />
    <img class="dark-only" src="/logos/duckdb-dark.svg" alt="DuckDB" />
  </a>
</div>

A **VGI worker** is a Java process your database engine attaches and calls as if
its functions were built in. You implement the functions — and, optionally, whole
catalogs of tables, views, and macros — then point Haybarn or DuckDB at the
process; it runs them inside ordinary SQL, exchanging columns as Arrow IPC. Think
of it as an alternative to an in-process C++ extension, without leaving the JVM or
its libraries. [What is VGI? →](/intro/what-is-vgi)

## Five function kinds, one shape language

Every VGI worker offers these five kinds of functions. Each kind maps an input
shape to an output shape. The five shapes are quick to learn, and it's easy to see
where each is most useful. You may already recognize them if you've built a DuckDB
extension before.

<KindGallery />

## Fast, and measured

Not projections — `bench.sh` and a stopwatch on a **MacBook Air (M3)**, median of
9 warm, multi-gigabyte runs with the spread reported:

<div class="home-stats">
  <div class="stat">
    <div class="stat-num">459M</div>
    <div class="stat-unit">rows / sec</div>
    <div class="stat-label">a parallel <code>numbers</code> scan over shared memory</div>
  </div>
  <div class="stat">
    <div class="stat-num">163M</div>
    <div class="stat-unit">rows / sec</div>
    <div class="stat-label">the same scan over a plain pipe, no shm</div>
  </div>
  <div class="stat">
    <div class="stat-num">2.82×</div>
    <div class="stat-unit">faster</div>
    <div class="stat-label">with shared memory, on a 16 GB scan</div>
  </div>
</div>

Data crosses between the engine and the worker as Arrow IPC — whole columns, no
row-by-row serialization — so running out-of-process stays cheap; the
[shared-memory transport](/advanced/shared-memory) removes even that copy for large
batches. The [benchmarks](/advanced/benchmarks) page has the methodology, the
round-trip and scalar numbers, and a script you can run yourself.

## A worker, in full

Both functions here — uppercasing a string, generating a range — are things
DuckDB already does on its own. That's on purpose: shown start to finish, with
nothing exotic in the way, they're the *shape* every worker takes. The same
`compute()` and `produceTick()` carry over unchanged when the body is real
work — an ML model, a pricing engine, any JVM library SQL can't reach.

### A scalar function

```java
// A scalar function maps one value to one value. Extend ScalarFn and write a
// single compute(); the framework reads its parameters to derive the SQL
// signature, the output type, and the per-batch dispatch — no schema code.
public final class UpperCase extends ScalarFn {
    @Override public String name() { return "upper_case"; }   // the SQL function name

    // @Vector marks a per-row input column. The trailing, unannotated vector is
    // the output: the framework allocates it, sized to the batch, and you fill it.
    public void compute(@Vector VarCharVector value, VarCharVector result) {
        int rows = value.getValueCount();        // a batch is a whole column of rows
        result.allocateNew();
        for (int i = 0; i < rows; i++) {
            if (value.isNull(i)) { result.setNull(i); continue; }   // NULL in, NULL out
            String up = new String(value.get(i), UTF_8).toUpperCase(ROOT);
            byte[] b = up.getBytes(UTF_8);
            result.setSafe(i, b, 0, b.length);   // write row i of the output column
        }
    }

    // A worker is just a main() that registers functions and serves them. The
    // catalog name ("demo") is what you ATTACH; runFromArgs picks the transport.
    public static void main(String[] a) {
        Worker.builder().catalogName("demo")
            .registerScalar(new UpperCase()).runFromArgs(a);
    }
}
```

```sql
ATTACH 'demo' AS demo (TYPE vgi, LOCATION 'launch:/path/to/worker');
SELECT demo.upper_case('hello');   -- HELLO
```

On a MacBook Air (M3), `upper_case` processes about **9M rows/s** over a stream of
strings — the per-row Unicode work dominates. See [benchmarks](/advanced/benchmarks).

### A table function

```java
// A table function produces rows. Extend CountdownTableFunction (gives you the
// `count` and `batch_size := 2048` args), declare an output schema and a producer.
// produceTick() emits one batch per call, so numbers(1_000_000_000) runs in flat
// memory.
//
// maxWorkers() lets the engine scan it on several threads — and each gets its own
// producer. They stay correct by coordinating through a shared atomic counter in
// params.storage(): each claims a disjoint chunk, covering 0..count-1 exactly once.
public final class Numbers extends CountdownTableFunction {
    private static final Schema OUT = Schemas.of(Schemas.nullable("n", Schemas.INT64));
    private static final byte[] NS = "cursor".getBytes(UTF_8), KEY = new byte[0];

    @Override public String name() { return "numbers"; }
    @Override public long maxWorkers() { return 4; }            // allow parallel scan threads
    @Override protected Schema outputSchema() { return OUT; }

    @Override public TableProducerState createProducer(TableInitParams p) {
        long count = ParameterExtractor.of(p.arguments()).positional(0, "count").asLong().required();
        return new State(count, 2048, p.storage());            // storage is shared across workers
    }

    public static final class State extends TableProducerState {
        public long count, batch; public BoundStorage storage;
        public State() {}
        State(long c, long b, BoundStorage s) { count = c; batch = b; storage = s; }

        @Override public void produceTick(OutputCollector out, CallContext ctx) {
            long end = storage.counterAdd(NS, KEY, batch);     // atomically claim the next chunk
            long start = end - batch;
            if (start >= count) { out.finish(); return; }
            int n = (int) Math.min(batch, count - start);
            var root = VectorSchemaRoot.create(OUT, Allocators.root());
            var v = (BigIntVector) root.getVector("n");
            v.allocateNew(n);
            for (int i = 0; i < n; i++) v.set(i, start + i);
            v.setValueCount(n); root.setRowCount(n);
            out.emit(root);
        }
    }
    // Register: .registerTable(new Numbers()) on Worker.builder()
}
```

```sql
SELECT * FROM demo.numbers(5);                  -- 0,1,2,3,4
SELECT count(*) FROM demo.numbers(1000000000);  -- streamed in batches, flat memory
```

Scanned on four threads, this delivers about **163M rows/s** over the pipe and
**459M rows/s** over [shared memory](/advanced/shared-memory) on a MacBook Air (M3). See
[benchmarks](/advanced/benchmarks).

That's two of the five
[function kinds](/intro/what-is-vgi#the-five-function-kinds), and the whole
worker. Nothing changes as the logic gets harder — the body is just Java, so the
step from `toUpperCase()` to a real library is yours to take, not the
framework's. The [quickstart](/intro/quickstart) builds and runs them.
