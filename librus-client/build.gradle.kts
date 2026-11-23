plugins {
    `java-library`
    `maven-publish`
    id("jlibrus.pitest-conventions")
}

description = "Pure Java client for the Librus Synergia school register (unofficial)"

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api(libs.jackson.databind)

    testImplementation(project(":librus-mock"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Werror")
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

pitest {
    targetClasses.set(listOf("io.github.wlk.jlibrus.client.*"))
    mutationThreshold.set(75)
    coverageThreshold.set(85)
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            pom {
                name.set("librus-client")
                description.set(project.description)
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY") ?: "owner/jlibrus"}")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
