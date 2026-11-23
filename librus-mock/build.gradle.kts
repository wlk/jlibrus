plugins {
    `java-library`
    application
    id("jlibrus.java-conventions")
}

description = "Offline mock of the Librus Synergia platform for tests and demo mode"

dependencies {
    implementation(libs.jackson.databind)
}

application {
    mainClass.set("io.github.wlk.jlibrus.mock.MockLibrusMain")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Werror")
}
