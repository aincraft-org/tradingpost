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
    maven("https://repo.papermc.io/repository/maven-public/")
    maven {
        name = "mintGitHubPackages"
        url = uri("https://maven.pkg.github.com/aincraft-org/mint")
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

val mintVersion = "26.8.12.10"
val mintApi = "dev.mintychochip.mint:mint-api:$mintVersion"

dependencies {
    api(project(":tradingpost-api"))
    compileOnly(mintApi)
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("org.postgresql:postgresql:42.7.5")
    compileOnly("com.mysql:mysql-connector-j:9.3.0")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.5.3")
    compileOnly("org.xerial:sqlite-jdbc:3.49.1.0")

    testImplementation(mintApi)
    testImplementation("com.zaxxer:HikariCP:6.2.1")
    testImplementation("org.postgresql:postgresql:42.7.5")
    testImplementation("com.mysql:mysql-connector-j:9.3.0")
    testImplementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.testcontainers:mysql:1.21.4")
    testImplementation("org.testcontainers:mariadb:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
}
