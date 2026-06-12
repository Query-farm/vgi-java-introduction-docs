// Standalone, copy-paste-runnable VGI-Java examples.
//
// This project depends ONLY on the published `farm.query:vgi` artifact — there
// is no composite build, no checkout of the vgi-java source tree required. A
// real engineer adds the same one line to their own build and is off to the
// races. See build.gradle.kts.
plugins {
    // Auto-provision the JDK toolchain when one isn't installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "vgi-java-examples"
