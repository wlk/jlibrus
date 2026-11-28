import io.github.wlk.jlibrus.gradle.CrapCheckTask

plugins {
    java
    jacoco
    id("jlibrus.quality-conventions")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

// Builds run on any JDK >= 21; bytecode always targets Java 21 so the library stays widely reusable.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial", "-Xlint:-classfile", "-parameters"))
}

dependencies {
    "testImplementation"(platform(catalog.findLibrary("junit-bom").get()))
    "testImplementation"(catalog.findLibrary("junit-jupiter").get())
    "testImplementation"(catalog.findLibrary("assertj").get())
    "testRuntimeOnly"(catalog.findLibrary("junit-launcher").get())
}

// Javadoc must be well-formed (broken tags or HTML fail the build); missing comments are not an error.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:all,-missing", "-quiet")
        addBooleanOption("Werror", true)
        encoding = "UTF-8"
    }
}

jacoco {
    toolVersion = catalog.findVersion("jacoco").get().requiredVersion
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

val crapCheck = tasks.register<CrapCheckTask>("crapCheck") {
    group = "verification"
    description = "Fails when any method has CRAP score >= 10 (ub-mode Crapper rule)."
    dependsOn(tasks.named("jacocoTestReport"))
    jacocoXml.set(layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml"))
    threshold.set(10.0)
    excludedClassPrefixes.convention(emptyList())
}

tasks.named("check") {
    dependsOn(crapCheck)
}

// Docker dependency layer: downloads what compiling (incl. Error Prone) and packaging need while only the build
// scripts are present, so source changes reuse the layer instead of hitting Maven Central (see Dockerfile).
tasks.register("resolveDependencies") {
    group = "build setup"
    description = "Downloads the annotation processor, compile and runtime classpaths."
    val classpaths = files(configurations.named("annotationProcessor"), configurations.named("compileClasspath"),
        configurations.named("runtimeClasspath"))
    inputs.files(classpaths)
    doLast { logger.lifecycle("{} files resolved", classpaths.files.size) }
}
