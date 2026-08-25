plugins { kotlin("jvm") }

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":engine:core"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
