buildscript {
    dependencies {
        // OWASP Dependency-Check 13.0.0 and the Quarkus 3.40.1 Gradle plugin pull vulnerable jackson 2.22.1/2.22.2
        // (GHSA-p6pp-m3f8-5c89, GHSA-7hhh-6rmp-j9qf) and guava 33.6.0 (GHSA-xxph-c9ww-hj94) onto the build
        // classpath. Versions live in the catalog; remove once both plugins ship patched versions.
        classpath(platform(libs.jackson.bom.patched))
        constraints {
            classpath(libs.guava.patched)
        }
    }
}

plugins {
    base
    alias(libs.plugins.dependency.check)
    // Resolved here, under the jackson pin above; subprojects inherit this classpath and only apply it.
    alias(libs.plugins.quarkus) apply false
}

// OWASP Dependency-Check (weekly workflow): fails on any dependency with a CVSS >= 7 vulnerability.
dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "SARIF")
    suppressionFile = "config/dependency-check/suppressions.xml"
    scanConfigurations = listOf("runtimeClasspath")
    nvd.apiKey = System.getenv("NVD_API_KEY")
    nvd.delay = 2000
}

description = "jlibrus - Java client, server and PWA for the Librus Synergia school register"
