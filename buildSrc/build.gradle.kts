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
