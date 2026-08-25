rootProject.name = "khushu-engine"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // io.github.cosinekitty:astronomy
    }
}

include(
    ":engine:core",
    ":engine:astronomy",
    ":engine:calendar",
    ":engine:prayer",
    ":engine:qibla",
    ":engine:zakat",
    ":engine:facade",
    ":tools:cli",
)
