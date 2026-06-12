---
description: "The JVM flags a VGI worker needs, and why — recommended settings for running Java workers under DuckDB."
---

# JVM flags

A VGI worker needs a couple of non-default JVM settings. The
[examples](/intro/quickstart) bake them into the Gradle build so you don't think
about them; this page explains what each is for and when you need it.

## Required

### `-parameters` (compile time)

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}
```

VGI derives **argument names** from your method parameter names — a `@Const long
factor` becomes the SQL argument `factor`, a `compute()` input vector named
`value` becomes the column `value`. Without `-parameters`, javac erases those
names to `arg0`, `arg1`, … and argument binding breaks. This is the one flag you
cannot skip.

### `--add-opens=java.base/java.nio=ALL-UNNAMED` (runtime)

Arrow's off-heap memory module reaches into `java.nio` internals (direct buffer
addresses) that are not exported by default. Without this open, the worker fails
at startup the first time it touches Arrow memory.

## Required for shared memory (JDK 22+)

### `--enable-native-access=ALL-UNNAMED` (runtime)

The [shared-memory transport](/advanced/shared-memory) calls POSIX `shm_open` /
`mmap` through the `java.lang.foreign` (FFM) API. On JDK 22+ those are native
downcalls; without this flag the JVM prints a warning (and future JDKs will deny
the call). On JDK 21 the worker uses the pipe transport and this flag is
harmless.

It's safe to always include it — there's no downside on a JDK that doesn't use
shm.

## Where they go

In a Gradle `application` build, the runtime flags ride on
`applicationDefaultJvmArgs` so the generated launch script applies them:

```kotlin
application {
    mainClass.set("…")
    applicationDefaultJvmArgs = listOf(
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
}
```

Running a worker by hand (no launch script)? Pass them to `java`:

```bash
java --add-opens=java.base/java.nio=ALL-UNNAMED \
     --enable-native-access=ALL-UNNAMED \
     -cp "lib/*" farm.query.vgi.examples.AllInOneWorker --unix /tmp/demo.sock
```

## Java version

The worker runs on **JDK 21, 22, and 25** (the versions the project tests against)
— and any JDK ≥ 21, since the published `vgi` artifact targets Java 21 bytecode.

| Version | What you get |
|---------|--------------|
| **21** | baseline — everything except shared memory; the worker uses the pipe transport |
| **22** | adds the [shared-memory transport](/advanced/shared-memory) (it activates when the client advertises a segment) |
| **25** | same as 22+, and the JDK the project builds and tests with |

So you don't have to chase a bleeding-edge JDK to adopt VGI: stay on 21 and you
lose only shared memory (the pipe transport still works). The examples build with
a JDK 25 toolchain targeting 21 bytecode, which is how the FFM shared-memory
overlay compiles while the artifact still runs on 21.

## Optional: surface Arrow leaks early

In tests, set `-Darrow.memory.debug.allocator=true`. Arrow then tracks every
allocation and **fails loudly** at shutdown if a buffer wasn't freed — turning a
silent leak (a missing `close()`, or a double-`emit`) into an immediate test
failure. Highly recommended while developing a function.
