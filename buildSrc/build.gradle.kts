plugins {
    `kotlin-dsl`
    java
}

dependencies {
    implementation("info.solidsoft.gradle.pitest:gradle-pitest-plugin:${libs.versions.pitestPlugin.get()}")
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:${libs.versions.spotbugsPlugin.get()}")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:${libs.versions.errorpronePlugin.get()}")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Gradle builds buildSrc only up to its jar and skips its tests, and the root build cannot depend on buildSrc
// tasks. The jar is finalized by the tests instead (test compilation needs the jar, so it cannot depend on them):
// a broken CRAP gate fails every build, and the tests stay up to date while buildSrc is unchanged.
tasks.jar {
    finalizedBy(tasks.test)
}
