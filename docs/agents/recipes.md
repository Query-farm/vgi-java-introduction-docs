---
description: "Task recipes for adding a VGI-Java function — one self-contained, agent-ready task per function kind, each verified end-to-end against a real engine."
---

# Task recipes

Each recipe is a self-contained task a coding agent can take end to end: a
**goal**, a **prompt** you can hand over verbatim, the **files** to create or
modify, and the exact **verification** — commands plus the output to expect. They
all build inside the [`examples/`](https://github.com/Query-farm/vgi-java) project
and verify through the same public `uvx` loop.

| Recipe | Kind | Builds |
|--------|------|--------|
| [add-scalar-function](https://github.com/Query-farm/vgi-java) | [scalar](/function-kinds/scalar) | `reverse_string(VARCHAR) -> VARCHAR` |
| [add-table-function](https://github.com/Query-farm/vgi-java) | [table](/function-kinds/table) | `fibonacci(count) -> n` |
| [add-table-in-out-function](https://github.com/Query-farm/vgi-java) | [table-in-out](/function-kinds/table-in-out) | `drop_nulls(TABLE) -> *` |
| [add-aggregate-function](https://github.com/Query-farm/vgi-java) | [aggregate](/function-kinds/aggregate) | `vgi_max(BIGINT) -> BIGINT` |
| [add-buffering-function](https://github.com/Query-farm/vgi-java) | [buffering](/function-kinds/buffering) | `top_n(TABLE, n) -> *` |

Before starting any recipe, an agent reads
[`AGENTS.md`](https://github.com/Query-farm/vgi-java) and the matching
[function page](/function-kinds/scalar), then starts from the matching
[skeleton](https://github.com/Query-farm/vgi-java).

## The shape of every recipe

Each one is small and prescriptive on purpose — an agent should be able to finish
it without guessing:

1. **Create** the function class from the kind's skeleton, filling the TODOs.
2. **Register** it on the `AllInOneWorker` (`.registerScalar(...)`, etc.).
3. **Add a test** — a `query` block in `examples/test/examples.test` with the
   expected rows below `----`.
4. **Verify** with the loop below. A green run is the definition of done.

## The verification loop

Fully public: it resolves `farm.query:vgi` from
[Maven Central](https://central.sonatype.com/artifact/farm.query/vgi) and runs a
real engine through [`uvx`](https://docs.astral.sh/uv/) — no local engine build.

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

To run the whole golden-file suite, use `uvx haybarn-unittest` (it discovers
`.test` files under `test/sql/` and needs `vgi` loaded):

```bash
mkdir -p test/sql
sed 's/^require vgi$/statement ok\nINSTALL vgi FROM community;\n\nstatement ok\nLOAD vgi;/' \
    test/examples.test > test/sql/examples.test
VGI_TEST_WORKER="launch:$BIN" uvx haybarn-unittest test/sql/examples.test
```

Expect `All tests passed`. If you have the vgi C++ repo checked out, its
`unittest` binary runs the same file via `require vgi` — an optional alternative.
