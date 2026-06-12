# vgi-java-examples

Minimal, self-contained, runnable VGI workers — one per function kind. Each file
in `src/main/java/farm/query/vgi/examples/` is a complete worker you can read
top-to-bottom:

| File | Kind | SQL function |
|------|------|--------------|
| `ScalarExample.java` | scalar | `upper_case(VARCHAR) -> VARCHAR` |
| `TableExample.java` | table | `numbers(count) -> n` |
| `TableInOutExample.java` | table-in-out | `echo(TABLE) -> *` |
| `AggregateExample.java` | aggregate | `vgi_sum(BIGINT) -> BIGINT` |
| `BufferingExample.java` | buffering | `collect(TABLE) -> *` |
| `AllInOneWorker.java` | all five | the combined demo worker |

The entire dependency on VGI is one line in `build.gradle.kts`:

```kotlin
implementation("farm.query:vgi:0.1.0")
```

## Build & run

```bash
./run.sh            # build, then print the ATTACH SQL for Haybarn
./run.sh --serve    # build, then serve the worker in the foreground
```

`./gradlew installDist` puts a launch script at
`build/install/vgi-java-examples/bin/vgi-java-examples`. Attach it from Haybarn:

```sql
INSTALL vgi FROM community; LOAD vgi;
ATTACH 'demo' AS demo (TYPE vgi, LOCATION 'launch:<abs path to that script>');
SELECT demo.upper_case('hi');
```

Run a single kind on its own socket:

```bash
./gradlew runScalar --args="--unix /tmp/s.sock --idle-timeout 60"
```

## Verify against Haybarn

The quickest end-to-end check uses Haybarn itself via `uvx` (no local install):

```bash
./gradlew installDist
BIN="$PWD/build/install/vgi-java-examples/bin/vgi-java-examples"
printf "INSTALL vgi FROM community; LOAD vgi;
ATTACH 'demo' AS demo (TYPE vgi, LOCATION 'launch:$BIN');
SELECT demo.upper_case('hello');
SELECT * FROM demo.numbers(5);
SELECT g, demo.vgi_sum(v) FROM (VALUES (1,10),(1,20),(2,5)) t(g,v) GROUP BY g;
DETACH demo;\n" | uvx haybarn-cli
```

Add `VGI_RPC_SHM_SIZE_BYTES=67108864 VGI_RPC_SHM_DEBUG=1
VGI_WORKER_STDERR=/tmp/w.log` in front of `uvx` to exercise (and watch) the
shared-memory transport.

## Verify with the sqllogictest harness (`uvx haybarn-unittest`)

The five functions are also checked by `test/examples.test`, a sqllogictest. Run
the whole golden-file suite through `uvx` — no local engine build required. The
harness discovers `.test` files under `test/sql/` and needs `vgi` loaded, so stage
a self-contained copy first (the `require vgi` directive only auto-loads *core*
extensions, not community ones):

```bash
./gradlew installDist
mkdir -p test/sql
sed 's/^require vgi$/statement ok\nINSTALL vgi FROM community;\n\nstatement ok\nLOAD vgi;/' \
    test/examples.test > test/sql/examples.test
VGI_TEST_WORKER="launch:$PWD/build/install/vgi-java-examples/bin/vgi-java-examples" \
  uvx haybarn-unittest test/sql/examples.test
```

Expect `All tests passed`. Add `VGI_RPC_SHM_SIZE_BYTES=67108864
VGI_RPC_SHM_DEBUG=1` to exercise the shared-memory transport; see
`../docs/advanced/shared-memory.md`.

> Have the vgi C++ repo checked out? Its `build/release/test/unittest` runs the
> same `test/examples.test` directly via `require vgi` — an optional alternative.
