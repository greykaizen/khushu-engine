dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // io.github.cosinekitty:astronomy
    }
}

rootProject.name = "khushu-engine"

include(
    ":engine:core",
    ":engine:astronomy",
    ":engine:calendar",
    ":engine:prayer",
    ":engine:qibla",
    ":engine:zakat",
    ":tools:cli",
)
