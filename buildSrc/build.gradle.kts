plugins {
    `kotlin-dsl`
    java
}

dependencies {
    implementation("info.solidsoft.gradle.pitest:gradle-pitest-plugin:${libs.versions.pitestPlugin.get()}")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
}
