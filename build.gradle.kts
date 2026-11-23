plugins {
    base
    alias(libs.plugins.dependency.check)
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
