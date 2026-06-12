# AGENTS.md — authoring VGI-Java functions

Orientation for a coding agent adding a new function to a VGI-Java worker. Read
this, then follow a [recipe](recipes/) for the kind you're building. The
[skeletons](skeletons/) are copy-paste starting points.

## The model in three sentences

A VGI worker is a Java process that serves SQL functions to DuckDB/Haybarn over
Apache Arrow IPC. You write a class that extends one base per function kind,
register it on a `Worker.builder()`, and run it. Inputs and outputs are Arrow
**vectors** (whole columns), never rows.

## Pick the kind

| Kind | Base type | Shape | Build when… |
|------|-----------|-------|-------------|
| scalar | `farm.query.vgi.scalar.ScalarFn` | row → row | one value in, one value out |
| table | `farm.query.vgi.table.CountdownTableFunction` (or `SimpleTableFunction`) | args → rows | generating/scanning rows |
| table-in-out | `farm.query.vgi.tableinout.PassthroughTIOFunction` (or `TableInOutFunction`) | rows → rows, **per batch** | streaming relation transform |
| aggregate | `farm.query.vgi.aggregate.AggregateFunction<State>` | rows → one per group | parallel reduction |
| buffering | `farm.query.vgi.buffering.TableBufferingFunction` | **all** rows → rows | sort / top-k / whole-relation |

Decision rule for the table-shaped three: emits per input batch → **table-in-out**;
folds rows into per-group state → **aggregate**; must see every row before
emitting → **buffering**.

## Required methods by kind

- **scalar**: `name()`, `compute(...)`. Optional: `description()`, `metadata()`,
  `outputType()`, `outputSchema()`, `argumentSpecs()`.
- **table**: `name()`, `outputSchema()`, `createProducer()` (+ producer
  `produceTick()`). Optional: `metadata()`, `extraArgs()`, `onBind()`,
  `cardinality()`, `statistics()`, `maxWorkers()`.
- **table-in-out**: `name()`, `argumentSpecs()` (a `TABLE` arg),
  `createExchange()` (+ exchange `onInputBatch()`). `onBind()` is inherited from
  `PassthroughTIOFunction` when output schema == input schema.
- **aggregate**: `spec()`, `outputSchema()`, `newState()`, `update()`,
  `combine()`, `finalize()`. Optional: `finalizeEmpty()`.
- **buffering**: `spec()`, `onBind()`, `process()`, `combine()`,
  `createFinalizeProducer()` (+ producer `produceTick()`).

## Register it

```java
Worker.builder().catalogName("demo")
    .registerScalar(new MyScalar())
    .registerTable(new MyTable())
    .registerTableInOut(new MyTio())
    .registerAggregate(new MyAgg())
    .registerTableBuffering(new MyBuffering())
    .runFromArgs(args);
```

(Plural `registerScalars(Iterable)` etc. also exist.)

## Conventions (do these)

- **`-parameters` is mandatory.** Argument names come from method parameter
  names. The build must pass `-parameters` to javac, or binding breaks.
- **Wire names are `snake_case`** and equal the Java parameter name. Name a
  constant `long batchSize` → arg `batch_size`.
- **Allocate from `Allocators.root()`** (or use the framework helpers `BatchUtil`,
  `TransferPair`, `BufferingFinalizeProducer`).
- **`OutputCollector.emit(root)` takes ownership** — never `close()` a root you
  emitted; do close roots you read and didn't emit.
- **Guard integer math** with `Math.addExact` / `Math.multiplyExact` and raise a
  function-named error (`"my_fn: int64 overflow"`).
- **No needless comments.** Comment the *why*, never restate code.
- **Producer/exchange/State classes need a public no-arg constructor** — the
  framework serializes them.

## Gotchas (avoid these)

- **Never `out.emit(input.root())` in a table-in-out.** The input root is reused
  for the next batch; emitting it gets it closed and corrupts the stream.
  `TransferPair` the vectors into a root you own. (Skeleton shows it.)
- **Don't stash Arrow vectors in aggregate `State`** — the batch is reused and
  `State` is serialized. Copy out scalar values.
- **Buffering state goes in `params.storage()`, not a field** — the Sink may run
  on parallel threads.
- **`_vgi/` namespaces in storage are reserved.** Use your own.
- **Stale `launch:` worker** after a rebuild: `pkill -f <your main class>` (or
  wait out the idle timeout) before re-attaching, or new functions appear
  missing.

## Verify your function

Both loops below are fully public: they resolve `farm.query:vgi` from Maven
Central and run a real engine through `uvx` — no local engine checkout needed.

1. **Compile**: `cd examples && ./gradlew installDist`.
2. **Quick check — `uvx haybarn-cli`** (fastest; pipe SQL, assert on the value):

   ```bash
   BIN="$PWD/build/install/vgi-java-examples/bin/vgi-java-examples"
   printf "INSTALL vgi FROM community; LOAD vgi;
   ATTACH 'demo' AS demo (TYPE vgi, LOCATION 'launch:$BIN');
   SELECT demo.reverse_string('abc');
   DETACH demo;\n" | uvx haybarn-cli -noheader -list      # -> cba
   ```

3. **Golden-file suite — `uvx haybarn-unittest`**: add a `query` block (expected
   rows below `----`) to `examples/test/examples.test`, then run it. The harness
   discovers `.test` files under `test/sql/` relative to the CWD and needs `vgi`
   loaded, so stage a self-contained copy (the `require vgi` directive only
   auto-loads *core* extensions, not community ones):

   ```bash
   mkdir -p test/sql
   sed 's/^require vgi$/statement ok\nINSTALL vgi FROM community;\n\nstatement ok\nLOAD vgi;/' \
       test/examples.test > test/sql/examples.test
   VGI_TEST_WORKER="launch:$PWD/build/install/vgi-java-examples/bin/vgi-java-examples" \
     uvx haybarn-unittest test/sql/examples.test          # -> All tests passed
   ```

Rebuilt the worker? `pkill -f farm.query.vgi.examples.AllInOneWorker` so the
pooled `launch:` worker is replaced by the new binary. A green run is the
definition of done.

> If you have the vgi C++ repo checked out, its `build/release/test/unittest`
> binary runs the same `.test` via `require vgi` — optional, not required.

## Source of truth

- Minimal, verified examples: `../examples/src/main/java/farm/query/vgi/examples/`.
- The HTML guide: `../docs/` (one page per kind).
- The exhaustive 90+ fixture set: the `vgi-example-worker` module in the
  [vgi-java repo](https://github.com/Query-farm/vgi-java) — the place to find a
  prior art for any advanced feature (pushdown, partitioning, time travel,
  statistics, multi-branch scans, secrets).
