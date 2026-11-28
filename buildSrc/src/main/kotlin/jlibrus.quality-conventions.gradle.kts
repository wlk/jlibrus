import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.errorprone

// Standard Java verification tools; all of them run as part of `check`.
plugins {
    java
    checkstyle
    pmd
    jacoco
    id("com.github.spotbugs")
    id("net.ltgt.errorprone")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun version(name: String) = catalog.findVersion(name).get().requiredVersion
val configDir = rootProject.layout.projectDirectory.dir("config")

// --- Checkstyle: structure rules (method/file length, nesting, complexity), main sources only ---
checkstyle {
    toolVersion = version("checkstyle")
    configFile = configDir.file("checkstyle/checkstyle.xml").asFile
    isIgnoreFailures = false
    maxWarnings = 0
}
tasks.named("checkstyleTest") { enabled = false }
tasks.withType<Checkstyle>().configureEach {
    reports { sarif.required.set(true); html.required.set(true); xml.required.set(false) }
}

// --- PMD: bug patterns, security, multithreading, performance ---
pmd {
    toolVersion = version("pmd")
    ruleSets = emptyList()
    ruleSetFiles = files(configDir.file("pmd/ruleset.xml"))
    isConsoleOutput = true
    isIgnoreFailures = false
}
tasks.named("pmdTest") { enabled = false }

// --- CPD: copy-paste detection (ub-mode "Dryer" rule), fails on duplicated blocks of >= 100 tokens ---
val cpd: Configuration by configurations.creating
dependencies {
    cpd(catalog.findLibrary("pmd-cli").get())
    cpd(catalog.findLibrary("pmd-java").get())
}
val cpdCheck = tasks.register<JavaExec>("cpdCheck") {
    group = "verification"
    description = "Fails on duplicated code (PMD CPD, 100+ tokens)."
    classpath = cpd
    mainClass.set("net.sourceforge.pmd.cli.PmdCli")
    val sources = layout.projectDirectory.dir("src/main/java")
    val report = layout.buildDirectory.file("reports/cpd/cpd.txt")
    inputs.dir(sources)
    outputs.file(report)
    args("cpd", "--minimum-tokens", "100", "--language", "java", "--dir", sources.asFile.path,
        "--format", "text", "--report-file", report.get().asFile.path)
    doFirst { report.get().asFile.parentFile.mkdirs() }
}

// --- SpotBugs + FindSecBugs: bytecode bug patterns and security issues ---
dependencies {
    "spotbugsPlugins"(catalog.findLibrary("findsecbugs").get())
}
spotbugs {
    toolVersion.set(version("spotbugs"))
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.MEDIUM)
    excludeFilter.set(configDir.file("spotbugs/exclude.xml").asFile)
}
tasks.named<SpotBugsTask>("spotbugsTest") { enabled = false }
tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html") { required.set(true) }
    reports.create("sarif") { required.set(true) }
}

// --- Error Prone: compile-time bug detection ---
dependencies {
    "errorprone"(catalog.findLibrary("errorprone-core").get())
}
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        excludedPaths.set(".*/build/.*")
    }
}
tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.enabled.set(false)
}

// --- JaCoCo: line coverage must stay >= 80% (project testing rule) ---
val coverageVerification = tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("jacocoTestReport"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(cpdCheck, coverageVerification)
}
