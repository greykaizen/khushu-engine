plugins {
    kotlin("jvm")
    kotlin("plugin.allopen") version "2.4.10"
    kotlin("kapt")
    application
}

kotlin { jvmToolchain(21) }

allOpen { annotation("org.openjdk.jmh.annotations.State") }

dependencies {
    implementation(project(":engine:facade"))
    implementation(project(":engine:core"))
    implementation(project(":engine:astronomy"))
    implementation("org.openjdk.jmh:jmh-core:1.37")
    kapt("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

/** Run: ./gradlew :tools:bench:jmh */
tasks.register<JavaExec>("jmh") {
    group = "benchmark"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    args("-f", "1", "-wi", "2", "-i", "3", "-r", "1s", "AstronomyBenchmark")
}
