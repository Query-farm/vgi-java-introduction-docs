# Task recipes

Each recipe is a self-contained task for a coding agent: a **goal**, a **prompt**
you can hand to an agent verbatim, the **files** to create/modify, and the exact
**verification** (commands + expected output). They all build inside the
`examples/` project and verify through the canonical C++ harness.

| Recipe | Kind | Builds |
|--------|------|--------|
| [add-scalar-function](add-scalar-function.md) | scalar | `reverse_string(VARCHAR) -> VARCHAR` |
| [add-table-function](add-table-function.md) | table | `fibonacci(count) -> n` |
| [add-table-in-out-function](add-table-in-out-function.md) | table-in-out | `drop_nulls(TABLE) -> *` |
| [add-aggregate-function](add-aggregate-function.md) | aggregate | `vgi_max(BIGINT) -> BIGINT` |
| [add-buffering-function](add-buffering-function.md) | buffering | `top_n(TABLE, n) -> *` |

Before starting any recipe, read [../AGENTS.md](../AGENTS.md) and the matching
page under [../../docs/functions/](../../docs/functions/). Start from the
matching [../skeletons/](../skeletons/) file.

## The verification loop (used by every recipe)

Both commands are fully public — they pull `farm.query:vgi` from Maven Central and
run a real engine through `uvx`, with no local engine checkout.

```bash
cd examples
./gradlew installDist                                  # 1. compile (resolves vgi from Central)
BIN="$PWD/build/install/vgi-java-examples/bin/vgi-java-examples"

# 2. Quick assertion with haybarn-cli — pipe SQL, check the value:
printf "INSTALL vgi FROM community; LOAD vgi;
ATTACH 'demo' AS demo (TYPE vgi, LOCATION 'launch:$BIN');
SELECT demo.<your_fn>(...);
DETACH demo;\n" | uvx haybarn-cli -noheader -list

# Rebuilt? kill the pooled worker so the new binary is served:
pkill -f farm.query.vgi.examples.AllInOneWorker
```

To run the whole `examples.test` golden-file suite, use `uvx haybarn-unittest`. It
discovers `.test` files under `test/sql/` and needs `vgi` loaded, so stage a
self-contained copy first:

```bash
mkdir -p test/sql
sed 's/^require vgi$/statement ok\nINSTALL vgi FROM community;\n\nstatement ok\nLOAD vgi;/' \
    test/examples.test > test/sql/examples.test
VGI_TEST_WORKER="launch:$BIN" uvx haybarn-unittest test/sql/examples.test
```

Expect `All tests passed`. A green run is the definition of done. (If you have the
vgi C++ repo, its `unittest` binary runs the same file via `require vgi` — optional.)
