// Compiler warnings (javac -Xlint:all from jlibrus.java-conventions) fail the build in modules that apply this.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Werror")
}
