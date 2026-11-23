buildscript {
    dependencies {
        // OWASP Dependency-Check 13.0.0 pulls jackson 2.22.1 and the Quarkus 3.40.1 Gradle plugin 2.22.2 onto
        // the build classpath; both have high-severity advisories GHSA-p6pp-m3f8-5c89 and GHSA-7hhh-6rmp-j9qf
        // (fixed in 2.22.3). Remove once both plugins ship a patched jackson.
        classpath(platform("com.fasterxml.jackson:jackson-bom:2.22.4"))
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
