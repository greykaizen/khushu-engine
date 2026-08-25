plugins { kotlin("jvm") }

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":engine:core"))
    // Test-only: the facade wires calendar into ZakatRules.hawlAnniversary in
    // production; tests replicate that wiring directly.
    testImplementation(project(":engine:calendar"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
