plugins { kotlin("jvm") }

kotlin { jvmToolchain(21) }

dependencies {
    implementation("com.batoulapps.adhan:adhan2-jvm:0.0.6")
    implementation(project(":engine:core"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
