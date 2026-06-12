---
description: "Serve a whole DuckDB catalog from Java with VGI — schemas, tables, views, and macros that attach and browse like a local database."
---

# Building a catalog

Functions are half of what a worker serves. The other half is the catalog: the
schemas, tables, views, and macros a SQL user expects to find. None of these
extend a base class. You build small descriptor objects and register them on the
`Worker` builder, and the engine surfaces them like any local catalog object.

Here is a whole catalog — a schema with a table, a view, and two macros:

<<< @/../examples/src/main/java/farm/query/vgi/examples/CatalogExample.java{java}

```sql
SELECT * FROM demo.catalog.first_five;     -- 0,1,2,3,4
SELECT * FROM demo.catalog.evens;          -- 0,2,4
SELECT demo.catalog.triple(4);             -- 12
SELECT * FROM demo.catalog.series(3);      -- 0,1,2
```

## Schemas

You don't create a schema explicitly. Registering a table, view, or macro into a
schema name brings it into being. `schemaComment` is how you attach a comment (and
implicitly declare the name up front):

```java
w.schemaComment("catalog", "Tables, views, and macros built by hand");
```

The default schema is `main`; change it with `Worker.defaultSchema(...)`.

## Tables

A table needs two things: its **columns** and something to **produce its rows**.

Columns are an Arrow schema serialized to IPC bytes:

```java
byte[] columns = SchemaUtil.serializeSchema(
        new Schema(List.of(Schemas.nullable("n", Schemas.INT64))));
```

Rows come from a **scan function** — a table function the engine calls when it
scans the table. It can be one of your own registered table functions or a
built-in. The example backs `first_five` with the worker's own `numbers`
function, bound to the argument `5`:

```java
CatalogTable.builder("catalog", "first_five", columns)
        .comment("The integers 0..4")
        .scanFunction("numbers", List.of((Object) 5L), Map.of())
        .cardinality(5, 5)
        .build()
```

The scan function's output columns map onto the table's declared columns by
position, so the function's `n` column becomes the table's `n`. Give the
optimizer a `cardinality` when you know it, and richer hints when you have them:

```java
CatalogTable.builder("catalog", "products", columns)
        .scanFunction("read_products")
        .statistics(List.of(
                ColumnStatistics.ofInt64("id", 1, 100, false, 100L),
                ColumnStatistics.ofUtf8("name", "Anvil", "Zebra", false, 100L, false, 30L)))
        .primaryKey(List.of(List.of(0)))   // column 0 is the PK
        .build()
```

See the `rff_*` and `products` tables in [`vgi-example-worker`](https://github.com/Query-farm/vgi-java/tree/main/vgi-example-worker) for statistics,
constraints, and foreign keys in full.

## Views

A view is SQL the engine expands at query time. It's bound to its own catalog and
schema, so its body can reference sibling tables by their plain schema-qualified
name:

```java
new View("catalog", "evens",
        "SELECT n FROM catalog.first_five WHERE n % 2 = 0",
        "Even values from first_five")
```

## Macros

A macro is parameterized SQL the engine inlines at the call site. Scalar macros
return a value; table macros return a relation.

```java
new Macro("catalog", "triple", MacroType.SCALAR,
        List.of("x"), "x * 3", "Triple a value")

new Macro("catalog", "series", MacroType.TABLE,
        List.of("k"), "SELECT n FROM range(k) t(n)", "The integers 0..k-1")
```

::: warning Macro bodies can't reference your catalog's tables
Unlike a view, a macro is pure text expanded in the *caller's* context, not the
macro's. A body like `SELECT … FROM catalog.first_five` fails, because the engine
resolves `catalog.first_five` against whatever catalog the caller is in — not your
worker's. And you can't fully qualify it (`demo.catalog.…`), because you don't
know the name the user will `ATTACH` under. So keep macro bodies self-contained
(built-ins, or the macro's own parameters), as `series` does, and use a **view**
when you need to wrap one of your own tables.
:::

## More than one catalog

A single worker can serve several catalogs, each attaching under its own name.
Register the extras with `registerExtraCatalog`; functions whose names start with
the catalog's prefix belong to it:

```java
w.registerExtraCatalog(new Worker.ExtraCatalog(
        "reports",          // ATTACH 'reports' AS …
        "1.0.0",            // implementation version
        "1.0.0",            // data version
        "Reporting catalog",
        "reports_"))        // owns functions named reports_*
 .registerTable(new ReportsScanFunction());   // e.g. reports_scan
```

## It all shows up in the catalog views

Everything a worker exposes lands in the engine's catalog views, so existing
tools and queries find it without changes:

```sql
SELECT table_name FROM information_schema.tables WHERE table_catalog = 'demo';
-- evens, first_five
```

The whole catalog in this page is exercised end-to-end in
[`examples/test/examples.test`](https://github.com/Query-farm/vgi-java); the
fuller surface (statistics, constraints, multi-branch scans, versioned catalogs)
lives in the [`vgi-example-worker`](https://github.com/Query-farm/vgi-java/tree/main/vgi-example-worker) module.
