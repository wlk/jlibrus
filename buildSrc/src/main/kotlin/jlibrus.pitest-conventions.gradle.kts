plugins {
    id("jlibrus.java-conventions")
    id("info.solidsoft.pitest")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

pitest {
    pitestVersion.set(catalog.findVersion("pitest").get().requiredVersion)
    junit5PluginVersion.set(catalog.findVersion("pitestJunit5").get().requiredVersion)
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    exportLineCoverage.set(true)
    mutators.set(listOf("STRONGER"))
    timeoutConstInMillis.set(10_000)
}
