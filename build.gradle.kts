import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    base
    id("xyz.jpenilla.run-paper") version "3.0.2" apply false
}

group = "dev.jlo.tradingpost"
version = providers.gradleProperty("tradingpost.version")
    .orElse(providers.environmentVariable("TRADINGPOST_VERSION"))
    .getOrElse("1.0.0-SNAPSHOT")

val gprOwner = providers.gradleProperty("gpr.owner")
    .orElse(providers.environmentVariable("GITHUB_REPOSITORY_OWNER"))
    .getOrElse("aincraft-org")
val gprRepo = providers.gradleProperty("gpr.repo")
    .orElse(providers.environmentVariable("GITHUB_REPOSITORY"))
    .map { it.substringAfter('/') }
    .getOrElse("tradingpost")
val githubRepoUrl = "https://github.com/$gprOwner/$gprRepo"

subprojects {
    group = rootProject.group
    version = rootProject.version

    plugins.withId("java-library") {
        apply(plugin = "maven-publish")

        val artifactBase = "tradingpost-$name"
        extensions.configure<BasePluginExtension> {
            archivesName.set(artifactBase)
        }

        tasks.withType<Javadoc>().configureEach {
            (options as StandardJavadocDocletOptions).apply {
                encoding = "UTF-8"
                addBooleanOption("Xdoclint:none", true)
                quiet()
            }
        }

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    artifactId = artifactBase
                    pom {
                        name.set(artifactBase)
                        description.set(
                            when (project.name) {
                                "api" -> "TradingPost public integration contracts"
                                "paper" -> "TradingPost Paper plugin"
                                else -> "TradingPost $artifactBase module"
                            },
                        )
                        url.set(githubRepoUrl)
                        licenses {
                            license {
                                name.set("All Rights Reserved")
                                url.set(githubRepoUrl)
                            }
                        }
                        scm {
                            connection.set("scm:git:https://github.com/$gprOwner/$gprRepo.git")
                            developerConnection.set("scm:git:ssh://git@github.com/$gprOwner/$gprRepo.git")
                            url.set(githubRepoUrl)
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "localBuild"
                    url = uri(rootProject.layout.buildDirectory.dir("maven-repo"))
                }
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/$gprOwner/$gprRepo")
                    credentials {
                        username = providers.gradleProperty("gpr.user")
                            .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                            .getOrElse("")
                        password = providers.gradleProperty("gpr.key")
                            .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                            .getOrElse("")
                    }
                }
            }
        }
    }
}
