---
description: "Build a VGI worker in Java, attach it from Haybarn, and call a function from SQL — in about five minutes."
---

# Quickstart

Build a worker, attach it from Haybarn, and call a function. About five minutes.

## Prerequisites

- **JDK 21+** (JDK 22+ to enable the [shared-memory transport](/advanced/shared-memory)).
- **Haybarn** (or any DuckDB engine with the `vgi` extension) — see the callout below.
- The [`examples/`](https://github.com/Query-farm/vgi-java) project from this repo.

<!--@include: ../_snippets/install-haybarn.md-->

## 1. Add the dependency

A worker needs exactly one dependency.

::: tip New to Gradle?
[Gradle](https://gradle.org) is the build tool most JVM projects use. You don't
install it — the `examples/` project ships a wrapper script (`./gradlew`) that
downloads the right version on first run. The `build.gradle.kts` file below
*declares* your project: where to fetch libraries (`mavenCentral()`), which
ones (`dependencies { … }`), and how to package it (`application`). The
coordinate `farm.query:vgi:0.1.0` is `group:artifact:version` — Gradle resolves
it from [Maven Central](https://central.sonatype.com/artifact/farm.query/vgi).
Running `./gradlew installDist` then produces a self-contained, runnable worker.
:::

```kotlin [build.gradle.kts]
plugins { application }

repositories { mavenCentral() }

dependencies {
    implementation("farm.query:vgi:0.1.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")   // any SLF4J binding
}

application {
    mainClass.set("farm.query.vgi.examples.AllInOneWorker")
    applicationDefaultJvmArgs = listOf(
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
}
```

Those two JVM flags are required — Arrow needs `java.nio` access and the
shared-memory transport makes native calls. The `-parameters` compiler flag is
also required; see [JVM flags](/reference/jvm-flags).

::: details Prefer Maven?
The dependency is the same coordinate; only the build file differs. In
`pom.xml`:

```xml
<dependency>
  <groupId>farm.query</groupId>
  <artifactId>vgi</artifactId>
  <version>0.1.0</version>
</dependency>
```

Pass the JVM flags via the `exec-maven-plugin` (or your run script) and the
`-parameters` flag through `maven-compiler-plugin`'s `<parameters>true</parameters>`.
The Gradle `examples/` project is the supported path; Maven works identically at
the library level.
:::

## 2. Write a worker

A worker is a `main` that registers functions and calls `runFromArgs`:

<<< @/../examples/src/main/java/farm/query/vgi/examples/ScalarExample.java{java}

## 3. Build it

```bash
cd examples
./gradlew installDist
```

That produces a launch script at
`build/install/vgi-java-examples/bin/vgi-java-examples`. (`./run.sh` does this
and prints the SQL for you.)

## 4. Attach from Haybarn

```sql
INSTALL vgi FROM community;
LOAD vgi;

-- Use the ABSOLUTE path to the launch script.
ATTACH 'demo' AS demo (TYPE vgi,
    LOCATION 'launch:/abs/path/build/install/vgi-java-examples/bin/vgi-java-examples');

SELECT demo.upper_case('hello');   -- HELLO
```

### Why `launch:`?

A cold JVM takes seconds to start. The `launch:` `LOCATION` scheme starts the
worker **once** behind a flock-coordinated Unix socket and reuses it across every
query — and across every engine process on the machine. Without it, each query
would pay the full JVM startup cost. You almost always want `launch:`.

Other `LOCATION` schemes exist (a bare path forks a subprocess per attach;
`http://host:port` talks to a long-running HTTP worker). See
[CLI & environment](/reference/cli-and-env).

## 5. Try all five kinds

The `AllInOneWorker` from the examples registers one function of each kind:

<<< @/../examples/sql/quickstart.sql{sql}

## What just happened

Three things, and together they're the whole protocol in miniature:

- `Worker.builder()...runFromArgs(args)` parsed `--unix`/`--idle-timeout` (added
  by `launch:`) and served the `AF_UNIX` [transport](/intro/anatomy-of-a-worker).
- The engine called the worker's `init`/`bind` RPCs to learn each function's
  schema, then streamed Arrow batches for execution.
- Your `compute()` saw whole Arrow vectors and wrote whole Arrow vectors back —
  no row-by-row marshalling anywhere in the path.

Next: [how a worker is wired together →](/intro/anatomy-of-a-worker)
