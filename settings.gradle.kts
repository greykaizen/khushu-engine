dependencyResolutionManagement {
    repositories {
        mavenCentral()
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
