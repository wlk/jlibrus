plugins {
    java
    alias(libs.plugins.quarkus)
    id("jlibrus.pitest-conventions")
}

description = "Quarkus backend + PWA for browsing Librus data, iCal feeds and e-mail summaries"

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:${libs.versions.quarkus.get()}"))
    implementation(project(":librus-client"))

    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-security")
    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkus:quarkus-mailer")
    implementation("io.quarkus:quarkus-cache")
    implementation("io.quarkus:quarkus-smallrye-health")

    implementation(libs.hikari)
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly(libs.sqlite.jdbc)
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.quarkus:quarkus-jacoco")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation(project(":librus-mock"))
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.junit)
}

tasks.withType<Test>().configureEach {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

// @QuarkusTest classes are loaded by Quarkus; quarkus-jacoco records them in its own file, merged here.
tasks.withType<JacocoReportBase>().configureEach {
    executionData.setFrom(fileTree(layout.buildDirectory) { include("jacoco/test.exec", "jacoco-quarkus.exec") })
}

// Pure domain logic is mutation-tested; Quarkus wiring (resources, producers) is covered by @QuarkusTest suites.
pitest {
    targetClasses.set(listOf("io.github.wlk.jlibrus.server.domain.*"))
    targetTests.set(listOf("io.github.wlk.jlibrus.server.domain.*"))
    mutationThreshold.set(75)
}

tasks.named<io.github.wlk.jlibrus.gradle.CrapCheckTask>("crapCheck") {
    // Generated/framework glue is excluded; everything else must stay under CRAP 10.
    excludedClassPrefixes.set(listOf("io.github.wlk.jlibrus.server.Main"))
}
