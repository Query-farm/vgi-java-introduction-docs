---
description: "An agent pack for VGI-Java — orientation, task recipes, and skeletons that let a coding agent add a new function and verify it end-to-end against a real engine."
---

# Building with a coding agent

The same examples this guide is built from ship with an **agent pack**: a short
orientation file, one task recipe per function kind, and copy-paste skeletons.
Point a coding agent at it and it can add a working function — registered, tested,
and verified end-to-end — without you narrating the framework.

It lives in the [`agents/`](https://github.com/Query-farm/vgi-java) directory of
the repo, alongside the [`examples/`](https://github.com/Query-farm/vgi-java)
project the recipes build into.

| Piece | What it is |
|-------|-----------|
| [`AGENTS.md`](https://github.com/Query-farm/vgi-java) | Orientation — the model in three sentences, how to pick a kind, the required methods, conventions, and the gotchas that bite. |
| [`recipes/`](/agents/recipes) | One self-contained task per kind: a goal, a prompt you can hand over verbatim, the files to touch, and the exact verification. |
| [`skeletons/`](https://github.com/Query-farm/vgi-java) | A minimal, TODO-marked starting class per kind — the shape to fill in. |

## Pick the kind

The recipe an agent follows is chosen by the shape of the work. This is the same
decision the [function pages](/functions/scalar) walk through, in one table:

| Kind | Base type | Shape | Build when… |
|------|-----------|-------|-------------|
| [scalar](/functions/scalar) | `ScalarFn` | row → row | one value in, one value out |
| [table](/functions/table) | `CountdownTableFunction` | args → rows | generating or scanning rows |
| [table-in-out](/functions/table-in-out) | `PassthroughTIOFunction` | rows → rows, **per batch** | streaming relation transform |
| [aggregate](/functions/aggregate) | `AggregateFunction<State>` | rows → one per group | parallel reduction |
| [buffering](/functions/buffering) | `TableBufferingFunction` | **all** rows → rows | sort / top-k / whole-relation |

The decision rule for the three table-shaped kinds: emits per input batch →
**table-in-out**; folds rows into per-group state → **aggregate**; must see every
row before emitting → **buffering**.

## What the agent must get right

The pack front-loads the handful of rules the framework actually enforces, so an
agent doesn't discover them through allocator-leak errors:

- **`-parameters` is mandatory.** Argument names come from method parameter names;
  without it, binding breaks. The `examples/` build already passes it.
- **Wire names are `snake_case`** and equal the Java parameter name — a `long
  batchSize` parameter becomes the `batch_size` argument.
- **`emit()` takes ownership.** Never `close()` a root you emitted; do close roots
  you read and didn't emit. Never `emit(input.root())` in a table-in-out — the
  input root is reused for the next batch.
- **Guard integer math** with `Math.addExact` / `Math.multiplyExact` and raise a
  function-named error (`"my_fn: int64 overflow"`).
- **Producer / exchange / `State` classes need a public no-arg constructor** — the
  framework serializes them between ticks and across process boundaries.

The full list, with the reasoning behind each, is in
[`AGENTS.md`](https://github.com/Query-farm/vgi-java).

## How a function gets verified

Every recipe ends the same way, and the loop is now fully public — it resolves
`farm.query:vgi` from [Maven Central](https://central.sonatype.com/artifact/farm.query/vgi)
and runs a real engine through [`uvx`](https://docs.astral.sh/uv/), with no local
engine build to check out.

```bash
cd examples
./gradlew installDist                                  # resolves vgi from Central
BIN="$PWD/build/install/vgi-java-examples/bin/vgi-java-examples"

# Quick assertion — pipe SQL through haybarn-cli and check the value:
printf "INSTALL vgi FROM community; LOAD vgi;
ATTACH 'demo' AS demo (TYPE vgi, LOCATION 'launch:$BIN');
SELECT demo.upper_case('hello');
DETACH demo;\n" | uvx haybarn-cli -noheader -list      # -> HELLO
```

To run the whole [`examples.test`](https://github.com/Query-farm/vgi-java)
golden-file suite, use `uvx haybarn-unittest`. It discovers `.test` files under
`test/sql/` and needs `vgi` loaded, so stage a self-contained copy first (the
`require vgi` directive only auto-loads *core* extensions, not community ones):

```bash
mkdir -p test/sql
sed 's/^require vgi$/statement ok\nINSTALL vgi FROM community;\n\nstatement ok\nLOAD vgi;/' \
    test/examples.test > test/sql/examples.test
VGI_TEST_WORKER="launch:$BIN" uvx haybarn-unittest test/sql/examples.test
```

A green `All tests passed` on a clean machine is the definition of done. See
[testing](/guides/testing) for the same three levels written for humans.

Next: [the task recipes →](/agents/recipes)
