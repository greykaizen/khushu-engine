plugins { kotlin("jvm") }

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":engine:core")) // typed error model (InvalidParameterException)
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
