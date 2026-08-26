plugins { kotlin("jvm") }

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":engine:core"))
    implementation(project(":engine:calendar")) // hijri-day math for hawl periods (acyclic)
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
