plugins {
    kotlin("jvm") version "2.4.10"
    application
}

// Standalone donor replica: ummalqura-calendar exactly as Osprey used it.
repositories { mavenCentral() }
kotlin { jvmToolchain(21) }
dependencies { implementation("com.github.msarhan:ummalqura-calendar:2.0.2") }
application { mainClass.set("MainKt") }
