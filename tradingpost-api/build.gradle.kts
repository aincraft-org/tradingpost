plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("ci.workflow", rootProject.file(".github/workflows/ci.yml").absolutePath)
    systemProperty("project.root", rootProject.projectDir.absolutePath)
    systemProperty(
        "ci.pom",
        layout.buildDirectory.file("publications/maven/pom-default.xml").get().asFile.absolutePath,
    )
}
