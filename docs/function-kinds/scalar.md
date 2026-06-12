---
description: "Write a DuckDB scalar function in Java with VGI — map each row to one value over an Arrow vector, with the SQL signature derived for you."
---

# Scalar functions

<KindBanner kind="scalar" />

A **scalar** maps each input row to one output row: `upper_case('hi') -> 'HI'`.
It's the simplest kind and the best place to learn the annotation model the
whole library is built on.

## The model

Extend `ScalarFn`, give it a `name()`, and write a single `compute()` method. The
framework reads `compute()`'s **parameter annotations** to derive the SQL
signature, the output type, and the per-batch dispatch. You write a loop; you
never write schema-marshalling code.

<<< @/../examples/src/main/java/farm/query/vgi/examples/ScalarExample.java{java}

Attach and call it:

```sql
SELECT demo.upper_case('hello');                      -- HELLO
SELECT demo.upper_case(x) FROM (VALUES ('a'),(NULL)) t(x);  -- A, NULL
```

## Parameter rules

`compute()` parameters are read positionally and by annotation:

- **`@Vector SomeVector v`** — a per-row **input column**. The Arrow vector class
  fixes the SQL type: `BigIntVector`→`BIGINT`, `VarCharVector`→`VARCHAR`,
  `Float8Vector`→`DOUBLE`, and so on.
- **`@Vector(any = true) FieldVector v`** — an input column of **any** type
  (resolve the real type in `outputType`).
- **`@Vector(varargs = true) List<X> vs`** — varargs of typed columns.
- **`@Const <java type> c`** — a **bind-time constant** argument. Type mapping:
  `long/int`→`INT64`, `double`→`FLOAT64`, `String`→`UTF8`, `boolean`→`BOOL`,
  `byte[]`→`BINARY`.
- **`@Setting <java type> s`** — a **session setting** (`SET demo.x = …`); same
  type mapping, optional `default_`.
- **`@OutputLength int n`** — the batch row count, injected (for functions with no
  input column).
- ***last unannotated vector*** — the **output**, framework-allocated and sized to
  the row count.

`compute()` returns `void`; you fill the output vector. Parameter **names**
become the SQL argument names, which is why the `-parameters` compiler flag is
mandatory.

### A constant and a setting

```java
// multiply_by(value BIGINT, factor BIGINT) using a session-tunable cap
public void compute(
        @Vector BigIntVector value,
        @Const long factor,
        @Setting(default_ = "9223372036854775807") long cap,
        BigIntVector result) {
    int rows = value.getValueCount();
    result.allocateNew(rows);
    for (int i = 0; i < rows; i++) {
        if (value.isNull(i)) { result.setNull(i); continue; }
        result.set(i, Math.min(value.get(i) * factor, cap));
    }
}
```

```sql
SET demo.cap = 1000;
SELECT demo.multiply_by(x, 3) FROM ...;
```

## Dynamic output types

When the output type depends on the input type or a const arg, override
`outputType()`. This is how a numeric `double(x)` returns `BIGINT` for an
integer input but `DOUBLE` for a floating input, and validates at bind time:

```java
public final class Double extends ScalarFn {
    @Override public String name() { return "double"; }

    // accept any numeric column; reject the rest at bind time
    public void compute(
            @Vector(any = true, typeBound = TypeBoundPredicate.IS_ADDABLE) FieldVector value,
            FieldVector result) { /* … type-dispatched loop … */ }

    @Override
    protected ArrowType outputType(Schema inputSchema, Arguments args) {
        ArrowType in = inputSchema.getFields().get(0).getType();
        // promote int widths up one size, float32 -> float64, etc.
        return promote(in);
    }
}
```

A `typeBound` violation is reported at bind time with a SQL-typed message, e.g.
`double: value must be numeric (got VARCHAR)` — before any data moves.

For non-flat outputs (`STRUCT` / `LIST` / `FixedSizeList`), override `outputSchema()`
instead, declaring the child fields. See the geo fixtures in [`vgi-example-worker`](https://github.com/Query-farm/vgi-java/tree/main/vgi-example-worker).

## Null handling

A row is null if `vector.isNull(i)`. You decide what null in means: pass it
through (`result.setNull(i)`), or treat it as an identity. Note the engine
short-circuits an all-literal-NULL call before it reaches the worker, so
`typeof(demo.double(NULL::INT))` is `NULL` — that's the engine, not your code.

## Performance notes

- The framework reuses a per-thread output `VectorSchemaRoot` across batches, so
  steady-state scalar dispatch allocates nothing on the hot path.
- Presize the output when you can. `result.allocateNew(rows)` (fixed-width) or
  `result.allocateNew(dataBytes, rows)` (varlen) avoids repeated grow-and-copy
  inside `setSafe`.

## Going further

The full scalar surface — varargs, any-typed columns, nested `STRUCT`/`LIST`
outputs, binary packing, secret accessors — is exercised by
[`vgi-example-worker/src/main/java/farm/query/vgi/example/scalar/`](https://github.com/Query-farm/vgi-java/tree/main/vgi-example-worker/src/main/java/farm/query/vgi/example/scalar) in the
[vgi-java repo](https://github.com/Query-farm/vgi-java)
([`Double`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/scalar/DoubleFunction.java),
[`AddValues`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/scalar/AddValuesFunction.java),
[`Multiply`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/scalar/MultiplyFunction.java),
[`BinaryPacket`](https://github.com/Query-farm/vgi-java/blob/main/vgi-example-worker/src/main/java/farm/query/vgi/example/scalar/BinaryPacketFunction.java),
the geo centroid/distance trio, and more).

Next: [table functions →](/function-kinds/table)
