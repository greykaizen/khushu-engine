rootProject.name = "khushu-engine"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google() // androidx.datastore (store module)
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
    ":engine:mushaf",
    ":engine:tasbih",
    ":engine:observance",
    ":engine:facade",
    ":store",
    ":tools:cli",
)
