// Khushu Engine — pure Kotlin/JVM. No Android, ever (see AGENTS.md).
plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.0"
    `maven-publish`
}

apiValidation {
    // Tools are internal harnesses — never part of the published surface.
    ignoredProjects += listOf("cli")
}

subprojects {
    apply(plugin = "maven-publish")

    val module = path.removePrefix(":").replace(":", "-")
    afterEvaluate {
        if (!path.startsWith(":engine:") && path != ":store") {
            extensions.configure<PublishingExtension> { publications.clear() }
            return@afterEvaluate
        }
        configure<PublishingExtension> {
            repositories { mavenLocal() }
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    groupId = "com.khushu"
                    artifactId = module
                    version = "1.10.0"
                    pom {
                        name.set("Khushu Engine — ${project.name}")
                        description.set(
                            "Deterministic Islamic computational engine: prayer times, " +
                                "astronomy, Hijri calendar, qibla, zakat. Pure Kotlin/JVM.",
                        )
                        licenses {
                            license {
                                name.set("GNU General Public License v3.0")
                                url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
                            }
                        }
                        url.set("https://github.com/greykaizen/khushu-engine")
                    }
                }
            }
        }
    }
}
