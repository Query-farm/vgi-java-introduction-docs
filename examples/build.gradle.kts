// VGI-Java examples — a minimal, self-contained worker for each function kind.
//
// The whole dependency on VGI is the single `implementation(...)` line below.
// Everything else here is standard `application`-plugin boilerplate so that
// `./gradlew installDist` produces a launchable worker script.
plugins {
    application
}

repositories {
    // The published coordinate lives on Maven Central. mavenLocal() is listed
    // first so this repo also builds against a locally-published snapshot
    // (./gradlew publishToMavenLocal in the vgi-java tree) without waiting for
    // a Central release.
    mavenLocal()
    mavenCentral()
}

dependencies {
    // ──────────────────────────────────────────────────────────────────────
    // This is the only line you need to serve VGI functions from Java.
    implementation("farm.query:vgi:0.26.1")
    // ──────────────────────────────────────────────────────────────────────

    // A logging backend. VGI uses SLF4J; pick any binding you like.
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

java {
    // Build with JDK 25, target Java 21 bytecode so the worker runs on any
    // JDK >= 21. The shared-memory transport (java.lang.foreign) activates
    // only on JDK >= 22; on 21 the worker transparently uses the pipe path.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    // REQUIRED: VGI reads compute()/method parameter NAMES to bind keyword
    // arguments and settings. Without -parameters those names are erased and
    // argument binding fails.
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
}

application {
    // The combined demo worker registers all five example functions. It is the
    // artifact the quickstart and the integration .test attach to.
    mainClass.set("farm.query.vgi.examples.AllInOneWorker")
    applicationDefaultJvmArgs = listOf(
        // Arrow's off-heap memory module needs access to java.nio internals.
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        // The shared-memory transport makes FFM (mmap/shm_open) downcalls.
        "--enable-native-access=ALL-UNNAMED",
    )
}

// Convenience tasks so each single-kind worker is independently runnable:
//   ./gradlew runScalar --args="--unix /tmp/s.sock --idle-timeout 30"
val jvmArgs = listOf(
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--enable-native-access=ALL-UNNAMED",
)
fun registerWorker(task: String, mainCls: String) {
    tasks.register<JavaExec>(task) {
        group = "vgi-examples"
        description = "Run the $task worker (pass --args=\"--unix <path> --idle-timeout <s>\")."
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set(mainCls)
        jvmArgs(jvmArgs)
    }
}
registerWorker("runScalar", "farm.query.vgi.examples.ScalarExample")
registerWorker("runTable", "farm.query.vgi.examples.TableExample")
registerWorker("runTableInOut", "farm.query.vgi.examples.TableInOutExample")
registerWorker("runAggregate", "farm.query.vgi.examples.AggregateExample")
registerWorker("runBuffering", "farm.query.vgi.examples.BufferingExample")
registerWorker("runCatalog", "farm.query.vgi.examples.CatalogExample")
