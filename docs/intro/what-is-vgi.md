---
description: "VGI lets a Java process serve DuckDB functions and catalogs over Apache Arrow IPC — an out-of-process alternative to native extensions."
---

# What is VGI?

<img src="/logos/java.svg" alt="Java" style="float: right; height: 84px; width: auto; margin: 0.25rem 0 1rem 1.5rem;" />VGI (Vector Gateway Interface) lets a Java process — a **worker** — serve SQL
queries through DuckDB. You implement one or more functions in the worker,
register them, and point DuckDB at it. From then on the functions are callable
like any built-in. A worker can go further and serve whole catalogs — schemas,
tables, functions, and macros — and host several at once, with each catalog
appearing as a database you can query.

It's an alternative to writing an in-process DuckDB extension. Rather than
compiling C++ into the engine, you run your code as its own process and let
DuckDB call into it. You keep your language, your libraries, and a crash boundary
between your code and the database.

The two sides speak [Apache Arrow IPC](https://arrow.apache.org/docs/format/Columnar.html#serialization-and-interprocess-communication-ipc).
Columns cross between them whole, in the columnar layout they already live in, so
nothing gets marshalled row by row. `vgi-java` is the worker half: ordinary Java
the engine calls.

<div class="built-on">
  <span class="built-on-label">Built on</span>
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

## The architecture

It helps to picture three pieces in a line:

1. **A SQL client.** A prompt, a BI tool, or an application sending a query.
2. **A database engine.** Haybarn or DuckDB, which plans and runs that query.
3. **One or more VGI workers.** Your Java processes, which the engine calls for
   the functions and tables it doesn't have built in.

![Three tiers in a row: a SQL client exchanges SQL and results with the database engine; the engine, acting as the client, exchanges calls and Arrow batches with one or more VGI workers.](/diagrams/architecture.svg)

The roles nest. To the SQL client, the engine is the server: send it SQL, get
back rows. But to a worker, *the engine is the client*. The engine opens the
connection, calls the worker's functions, and scans its tables; the worker only
answers. A VGI worker is itself a server, and its one client is the database
engine, never the end user directly.

In other words, the database you query is, one layer down, a client of your Java
code.

## Running it with Haybarn or DuckDB

VGI is a protocol, so the worker is engine-agnostic. What an engine loads to speak
it is the client-side `vgi` extension. That extension ships today for **Haybarn**,
Query Farm's DuckDB-derived engine, through its community channel. It hasn't been
released for stock DuckDB yet, so these docs use Haybarn. A worker you write now
will work unchanged once DuckDB support lands.

Enable the extension once per session:

```sql
INSTALL vgi FROM community;
LOAD vgi;
```

Now you can attach a Java worker and query it like a local catalog:

```sql
ATTACH 'demo' AS demo (TYPE vgi, LOCATION 'launch:/path/to/worker');
SELECT demo.upper_case('hello');           -- a scalar your Java code computes
SELECT * FROM demo.numbers(1000);          -- a table your Java code generates
```

::: tip The `LOCATION` prefix chooses the transport
`launch:` is one of three ways DuckDB can reach a worker:

- **`launch:<command>`** — the *launcher* starts the worker once and reuses it
  across every query and process, coordinated through a Unix socket. Best for JVM
  workers, since the slow cold start is paid only once.
- **`<command>`** (no prefix) — DuckDB forks the worker as a *subprocess* for the
  attach and talks to it over its stdio pipes.
- **`http://host:port`** — DuckDB connects to a worker you're already running as
  an *HTTP* service, on this machine or **another machine across the network** —
  so a query can call Java that lives somewhere else entirely.

The launcher and subprocess transports run the worker on the same machine, so
they can also use the [shared-memory transport](/advanced/shared-memory) to hand
large batches across without copying them through a pipe. HTTP can't, since the
worker may be remote — but it reaches the furthest: the `vgi` extension also
compiles to WebAssembly, so even a DuckDB-Wasm engine
[running in a browser](/intro/anatomy-of-a-worker#local-remote-or-in-the-browser)
can attach a worker over HTTP.
:::

<!--@include: ../_snippets/install-haybarn.md-->

## Why run it out of process?

Mostly, to use Java from SQL without rewriting it in C++. A worker can wrap
anything the JVM does — a JSON parser, an ML model, a geo library, a pricing
engine — and expose it as a function DuckDB calls mid-query.

Hosting the JVM in-process is rarely practical anyway. DuckDB gets embedded in
all kinds of processes and libraries — Rust, Python, C#, a plain C++ app — and standing up a JVM
inside that same process, then bridging it to DuckDB's internals, is difficult
and often infeasible. A separate process avoids the question entirely: the JVM
runs where it runs well, and the two sides meet over a well-defined wire.

Running in a separate process also buys isolation. The worker can crash, leak
memory, or get resource-capped on its own, without touching the database that
called it. And a single worker can serve several DuckDB processes at once (see
[parallelism](/advanced/parallelism)).

The data path stays cheap the whole way. Inputs and outputs move as Arrow
batches, so your `compute()` sees whole vectors and never pays a per-row
serialization cost.

## When to use it (and when not)

VGI is one of a few ways to extend DuckDB. The three approaches below are compared
on the same five dimensions.

### Load the data directly

Read it with `read_parquet`, `COPY`, or a plain table.

- **Speed & efficiency:** Fastest. The engine reads bytes with nothing in the way.
- **Technical complexity:** Lowest. It's just SQL.
- **Implementation languages:** None — there's no code to write.
- **Deployment & iteration:** Instant. Change a file, rerun the query.
- **Isolation & distribution:** Not applicable.

Right when the data is static and needs no custom logic. The moment you need
computation the engine doesn't have, you've outgrown it.

### A native DuckDB extension

Compile your logic into the engine itself.

- **Speed & efficiency:** Highest for custom logic. It runs in-process, with no hop.
- **Technical complexity:** High. You work against DuckDB's extension interfaces and memory model.
- **Implementation languages:** C++, or Rust/C through the C extension API. No JVM, .NET, or scripting languages.
- **Deployment & iteration:** Slow. Recompile against each engine version, then ship through DuckDB's distribution pipeline — a community channel, or an unsigned binary users opt into.
- **Isolation & distribution:** None. A crash takes the engine down, and you're limited to what DuckDB exports and how it lets you distribute.

Right when you need raw in-process speed and can live on DuckDB's release cadence.

### A VGI worker (this)

Run your logic as its own process the engine calls.

- **Speed & efficiency:** A short IPC hop per batch, reduced by the columnar wire and [shared memory](/advanced/shared-memory). Seldom the bottleneck in practice; the [benchmarks](/advanced/benchmarks) quantify it.
- **Technical complexity:** Moderate. You implement a method over an Arrow vector and the framework handles the protocol, but you do run and operate a separate process.
- **Implementation languages:** Anything that speaks the protocol. This guide is Java; there are Python and Go implementations too.
- **Deployment & iteration:** Fast. Build, version, and deploy on your own schedule, so a fix ships in minutes rather than a release cycle.
- **Isolation & distribution:** Strong. The worker is sandboxed in its own process, can stay closed-source, ships however you like, and scales independently of the engine (including behind HTTP).

Right when the logic belongs in another language, must stay isolated or
closed-source, or has to ship on your own schedule.

## A full catalog, not just functions

Functions are the building blocks. What SQL users expect is a catalog: a named
database with schemas, and tables inside them. VGI serves that whole shape. A
worker assembles its functions, tables, views, and macros into one or more
catalogs that attach and browse like any local database.

- **Tables** are named relations with declared columns, backed by your Java code
  (a scan that streams rows on demand). You can attach statistics, comments, and
  constraints, so the optimizer and `DESCRIBE` treat them as real tables.
- **Views** are SQL the engine expands at query time, so you can publish derived
  queries without computing anything yourself.
- **Macros** are parameterized SQL the engine inlines at the call site.

Everything a worker exposes lands in DuckDB's catalog views
(`information_schema`, `duckdb_tables()`, and the rest), so existing tools and
queries find it without changes. You register each kind through the same
`Worker.builder()` you use for functions (`registerCatalogTable`,
`registerView`, `registerMacro`, and a `registerExtraCatalog` for serving more
than one catalog). [Building a catalog](/guides/catalog) walks through all of it
with a runnable example.

## The five function kinds

| Kind | Shape | Example | Use it for |
|------|-------|---------|------------|
| [Scalar](/function-kinds/scalar) | <KindIcon kind="scalar" size="30" /><br>row → row | `upper_case(s)` | per-row transforms |
| [Table](/function-kinds/table) | <KindIcon kind="table" size="30" /><br>args → rows | `numbers(n)` | generators, external scans |
| [Table‑in‑out](/function-kinds/table-in-out) | <KindIcon kind="table-in-out" size="30" /><br>rows → rows (streamed) | `echo(t)` | streaming relation transforms |
| [Aggregate](/function-kinds/aggregate) | <KindIcon kind="aggregate" size="30" /><br>rows → one per group | `vgi_sum(v)` | parallel reductions |
| [Buffering](/function-kinds/buffering) | <KindIcon kind="buffering" size="30" /><br>all rows → rows | `collect(t)` | sort / top-k / whole-relation work |

The last three differ in *when* they can emit. A table-in-out function emits as
each input batch arrives. An aggregate folds rows into per-group state and
returns one row per group. A buffering function has to see every input row before
it emits anything, which is what makes a sort or a top-k possible.

## What this guide covers

This is an introduction, not the full reference. It's enough to build, run, and
understand each kind of function, plus the two performance topics people reach
for first: [parallelism](/advanced/parallelism) and the
[shared-memory transport](/advanced/shared-memory). Every code sample is pulled
straight from the [`examples/`](https://github.com/Query-farm/vgi-java) project,
which compiles and passes an end-to-end SQL test.

It stops there on purpose. The deeper topics — catalog versioning, time travel,
transactions, secrets, statistics, multi-branch scans, and writable catalogs —
already work today; they live in the [`vgi-example-worker`](https://github.com/Query-farm/vgi-java/tree/main/vgi-example-worker) module of the
[vgi-java repo](https://github.com/Query-farm/vgi-java), alongside ninety-some
worked fixtures.

Fuller documentation and more worked examples will arrive with the official
launch of VGI. Until then, this guide is the on-ramp and the code is the
reference — and **all of it is on [GitHub](https://github.com/Query-farm/vgi-java)
right now**, ready to read, run, and build against.

Next: [build and run your first worker.](/intro/quickstart)
