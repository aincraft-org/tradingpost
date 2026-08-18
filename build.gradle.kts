import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    base
    id("xyz.jpenilla.run-paper") version "3.0.2" apply false
    id("com.github.spotbugs") version "6.1.13" apply false
    id("com.diffplug.spotless") version "7.0.2" apply false
}

group = "dev.mintychochip"
version = providers.gradleProperty("releaseVersion")
    .orElse(providers.gradleProperty("tradingpost.version"))
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
        apply(plugin = "pmd")
        apply(plugin = "checkstyle")
        apply(plugin = "com.github.spotbugs")
        apply(plugin = "com.diffplug.spotless")

        val artifactBase = "tradingpost-$name"
        val isPaper = name == "paper"

        extensions.configure<BasePluginExtension> {
            archivesName.set(artifactBase)
        }
        extensions.configure<SpotlessExtension> {
            java {
                googleJavaFormat("1.27.0")
                target("src/**/*.java")
            }
        }
        extensions.configure<PmdExtension> {
            toolVersion = "7.10.0"
            isConsoleOutput = true
            isIgnoreFailures = false
            ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
            ruleSets = emptyList()
        }
        extensions.configure<CheckstyleExtension> {
            toolVersion = "10.21.1"
            isIgnoreFailures = false
            configFile = rootProject.file("config/checkstyle/checkstyle.xml")
            maxWarnings = 0
        }
        tasks.withType<Checkstyle>().configureEach {
            if (name.contains("Test", ignoreCase = true)) enabled = false
        }
        tasks.withType<Pmd>().configureEach {
            if (name.contains("Test", ignoreCase = true)) enabled = false
        }
        tasks.withType<SpotBugsTask>().configureEach {
            if (name.contains("Test", ignoreCase = true) || isPaper) {
                enabled = false
            } else {
                reports.create("xml") { required.set(true) }
                reports.create("html") { required.set(true) }
            }
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
