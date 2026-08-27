plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(project(":tradingpost-api"))
    compileOnly(project(":tradingpost-common"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.111-stable")
    testImplementation(project(":tradingpost-api"))
    testImplementation(project(":tradingpost-common"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("tradingpost-test")
    archiveVersion.set("")
}
