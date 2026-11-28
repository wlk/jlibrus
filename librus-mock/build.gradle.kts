plugins {
    `java-library`
    application
    id("jlibrus.pitest-conventions")
    id("jlibrus.strict-compile")
}

description = "Offline mock of the Librus Synergia platform for tests and demo mode"

dependencies {
    implementation(libs.jackson.databind)
}

application {
    mainClass.set("io.github.wlk.jlibrus.mock.MockLibrusMain")
}

pitest {
    targetClasses.set(listOf("io.github.wlk.jlibrus.mock.*"))
    // fixtures are sample data, not logic: mutating a sample grade value tells nothing
    excludedClasses.set(listOf("io.github.wlk.jlibrus.mock.MockLibrusMain", "io.github.wlk.jlibrus.mock.fixtures.*"))
    mutationThreshold.set(80)
}
