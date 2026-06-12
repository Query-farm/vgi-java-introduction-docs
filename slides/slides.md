---
theme: default
title: VGI for Java
info: |
  Serving Haybarn / DuckDB functions from Java over Apache Arrow IPC.
class: text-center
highlighter: shiki
lineNumbers: false
drawings:
  persist: false
transition: slide-left
mdc: true
---

<img src="/vgi-logo.png" alt="Vector Gateway Interface" class="h-48 mx-auto mb-4" />

# VGI for Java

### DuckDB functions, served from Java

Expose Java libraries as Haybarn / DuckDB **scalar · table · table-in-out ·
aggregate · buffering** functions — over Apache Arrow IPC

<div class="pt-8 opacity-70">
  scalar &middot; table &middot; table-in-out &middot; aggregate &middot; buffering
  &nbsp;|&nbsp; parallelism &middot; shared memory
</div>

<div class="abs-br m-6 text-sm opacity-50">
  github.com/Query-farm/vgi-java
</div>

---

# What is VGI?

A protocol that lets a **separate Java process** serve a catalog — its tables and
functions — to a DuckDB engine, over **Apache Arrow IPC**.

```text
┌────────────────────┐      Arrow IPC over a            ┌─────────────────────┐
│  Haybarn / DuckDB  │ ◀── Unix socket / stdio / HTTP ─▶│   Your Java worker  │
│  (the vgi client)  │     (+ optional shared memory)   │  (vgi-java library) │
│  SELECT demo.f(x)  │                                  │  class F extends    │
│  FROM demo.t(...)  │                                  │      ScalarFn { … } │
└────────────────────┘                                  └─────────────────────┘
```

- Columnar end to end — your code sees whole Arrow **vectors**, not rows
- Use the **JVM ecosystem** from SQL: parsers, ML, geo, pricing engines…
- Process **isolation**; one worker can back many engines — local, remote over
  **HTTP**, even a **DuckDB-Wasm** page in the browser

---

# One worker, in full

```java
public final class UpperCase extends ScalarFn {
    @Override public String name() { return "upper_case"; }

    public void compute(@Vector VarCharVector value, VarCharVector result) {
        int rows = value.getValueCount();
        result.allocateNew();
        for (int i = 0; i < rows; i++) {
            if (value.isNull(i)) { result.setNull(i); continue; }
            byte[] up = new String(value.get(i), UTF_8)
                            .toUpperCase(ROOT).getBytes(UTF_8);
            result.setSafe(i, up, 0, up.length);
        }
    }

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

---

# Add one dependency

A JVM project pulls libraries from a shared repository — **Maven Central** — by a
`group:artifact:version` coordinate. VGI is one such library: name it in your
build tool and it's available.

<div grid="~ cols-2 gap-4">

<div>

**Gradle** — `build.gradle.kts`

```kotlin
dependencies {
    implementation("farm.query:vgi:0.1.0")
}
```

</div>

<div>

**Maven** — `pom.xml`

```xml
<dependency>
  <groupId>farm.query</groupId>
  <artifactId>vgi</artifactId>
  <version>0.1.0</version>
</dependency>
```

</div>

</div>

Published on **Maven Central** — no native libraries, no codegen. `./gradlew
installDist` then produces a runnable worker script.

---

# Declare arguments by annotation

You never write schema-marshalling code. Annotate your `compute()` parameters and
VGI derives the SQL signature, the output type, and the per-batch dispatch.

| Parameter | Meaning |
|-----------|---------|
| `@Vector SomeVector v` | a per-row **input column** (vector class ⇒ SQL type) |
| `@Const long c` | a **bind-time constant** argument |
| `@Setting long s` | a **session setting** (`SET demo.s = …`) |
| `@OutputLength int n` | the batch row count |
| *last unannotated vector* | the **output**, framework-allocated |

`compute()` returns `void`; you fill the output vector. Parameter **names** become
SQL argument names → compile with `-parameters`.

---
layout: section
---

# The five function kinds

<KindGallery />

<div class="text-center opacity-60 pt-6 text-sm">

one shape language — learn the five, read any signature at a glance

</div>

---

# Scalar — row → row

<KindIcon kind="scalar" :size="56" class="abs-tr mt-8 mr-8" />

```java
public void compute(@Vector VarCharVector value, VarCharVector result) {
    for (int i = 0; i < value.getValueCount(); i++) { /* fill result[i] */ }
}
```

- Extend `ScalarFn`, write one `compute()`
- Annotations derive the signature, output type, dispatch
- Dynamic output types → override `outputType()`; validate with `typeBound`

<div class="opacity-70 pt-4">

`SELECT demo.upper_case('hi');  -- HI`

</div>

---

# Table — args → rows (streamed)

<KindIcon kind="table" :size="56" class="abs-tr mt-8 mr-8" />

```java
public final class Numbers extends CountdownTableFunction {
    @Override protected Schema outputSchema() { return OUTPUT_SCHEMA; }

    @Override public TableProducerState createProducer(TableInitParams p) {
        long count = ParameterExtractor.of(p.arguments())
                         .positional(0, "count").asLong().required();
        return new NumbersState(new BatchState(count, 2048), /* filters */);
    }
    // producer.produceTick(out, ctx): emit ONE batch per call, then out.finish()
}
```

- Streams in fixed-size batches → constant memory, even for `numbers(1e9)`
- Opts into **filter / limit pushdown** → `LIMIT 7` stops the generator early

```sql
SELECT count(*) FROM (SELECT * FROM demo.numbers(1000000) LIMIT 7);  -- 7
```

---

# Table-in-out — rows → rows, per batch

<KindIcon kind="table-in-out" :size="56" class="abs-tr mt-8 mr-8" />

```java
public void onInputBatch(AnnotatedBatch in, OutputCollector out, CallContext c) {
    // TransferPair the input into a root WE own — never emit(input.root())!
    var copy = transfer(in.root());
    out.emit(copy);              // emit() takes ownership & closes it
}
```

- Emits **per input batch** (vs. buffering, which needs all input first)
- The gotcha: the input root is reused for the next batch — **transfer, don't
  alias**
- `PassthroughTIOFunction` gives you "output schema = input schema" for free

---

# Aggregate — rows → one per group

<KindIcon kind="aggregate" :size="56" class="abs-tr mt-8 mr-8" />

```java
State newState();                                  // empty accumulator
void  update(Map<Long,State> states, long[] gids, VectorSchemaRoot batch);
void  combine(State target, State source);         // merge partials (parallel!)
void  finalize(FieldVector result, int row, State s);
```

```text
rows ─update─▶ partial A ─┐
rows ─update─▶ partial B ─┼─combine─▶ merged ─finalize─▶ value
rows ─update─▶ partial C ─┘
```

- Built for DuckDB's **parallel partial aggregation**
- `State` is `Serializable` — partials may cross processes; keep it small
- `combine` must be associative & commutative

---

# Buffering — all rows → rows (Sink + Source)

<KindIcon kind="buffering" :size="52" class="abs-tr mt-8 mr-8" />

```java
byte[]       process(VectorSchemaRoot batch, …);   // Sink: stash, return state_id
List<byte[]> combine(List<byte[]> stateIds, …);    // group → finalize streams
TableProducerState createFinalizeProducer(…);      // Source: replay/emit
```

- For **whole-relation** work: sort, top-k, median, dedup
- State lives in `params.storage()` (durable, execution-scoped) → survives
  **parallel sinks**
- `storage` also offers counters, ranged scans, a FIFO queue

```sql
SELECT n FROM demo.collect((SELECT * FROM demo.numbers(4)));  -- 0,1,2,3
```

---

# Parallelism

Two independent axes:

**Across connections** — each connection on its own **virtual thread**

```java
ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
```
→ one `launch:` worker serves many engine processes at once. *Guard shared
fields.*

**Within a scan** — opt a table function into parallel scan threads

```java
@Override public long maxWorkers() { return 4L; }   // default 1
```

Aggregates parallelize **by construction** (that's what `combine` is for).

---

# Shared-memory transport

Hand big batches through a **POSIX shm segment** both processes map — no worker
code changes.

```bash
export VGI_RPC_SHM_SIZE_BYTES=67108864      # 64 MiB — enables it (client side)
```

```text
[vgi-shm] attached vgi_shm_…_0 size=67108864
[vgi-shm] conn closed …: outbound shm=1/1 (100%), inbound shm=1/1 (100%)
```

- Client owns the segment; worker just **attaches** (JDK 22+, FFM `mmap`)
- Falls back to the pipe for tiny / dict-encoded / oversize batches — never wrong
- Verified: identical results inline vs. shm

---

# Benchmarks — measured, not claimed

Isolating transport cost on an **Apple M3** (Haybarn 1.5.3, JDK 25), median of 9
warm runs, 32 MB batches:

| Workload | Inline | Shared memory | Speedup |
|----------|-------:|--------------:|:-------:|
| Scan 2B rows (16 GB, one-way) | 163M rows/s | 459M rows/s | **2.82×** |
| Round-trip 200M rows (4.8 GB) | 23M rows/s | 61M rows/s | **2.62×** |

- A table function of simple columns runs at **hundreds of millions of rows/s**;
  a scalar doing real per-row string work, **tens of millions**
- The win tracks how **transport-bound** you are — reproduce with `./bench.sh`

---

# Build it with a coding agent

The repo ships an **agent pack** — point a coding agent at it and it adds a
working function: registered, tested, verified.

<div grid="~ cols-2 gap-4">

<div>

- **`AGENTS.md`** — orientation, conventions, the gotchas that bite
- **`recipes/`** — one task per kind: goal, prompt, files, verification
- **`skeletons/`** — a TODO-marked starting class per kind

</div>

<div>

Verified end-to-end, **no local engine build**:

```bash
./gradlew installDist     # vgi from Central
… | uvx haybarn-cli       # quick SQL check
uvx haybarn-unittest …    # golden-file suite
```

</div>

</div>

A green run is the definition of done.

---

# Run it yourself

```bash
cd examples && ./gradlew installDist   # resolves vgi from Maven Central
BIN=$PWD/build/install/vgi-java-examples/bin/vgi-java-examples
```

```sql
INSTALL vgi FROM community; LOAD vgi;          -- or: pipe to `uvx haybarn-cli`, no install
ATTACH 'demo' AS demo (TYPE vgi, LOCATION 'launch:<BIN>');
SELECT demo.upper_case('hello');                       -- HELLO
SELECT * FROM demo.numbers(5);                         -- 0..4
SELECT g, demo.vgi_sum(v) FROM … GROUP BY g;           -- parallel aggregate
```

<div class="pt-8 opacity-70">

Public & reproducible: **Maven Central** + `uvx` &middot; Docs site &middot;
Code `examples/` &middot; Full fixtures `vgi-example-worker`

</div>
