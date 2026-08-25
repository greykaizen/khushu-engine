plugins { kotlin("jvm") }

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":engine:core"))
    api(project(":engine:astronomy"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
