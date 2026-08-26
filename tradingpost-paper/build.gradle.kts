plugins {
    `java-library`
    id("xyz.jpenilla.run-paper")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
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
val mintPaper = "dev.mintychochip.mint:mint-paper:$mintVersion"

val mintPaperRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    api(project(":tradingpost-api"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(mintApi)
    compileOnly(mintPaper)
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("org.postgresql:postgresql:42.7.5")
    compileOnly("com.mysql:mysql-connector-j:9.3.0")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.5.3")
    compileOnly("org.xerial:sqlite-jdbc:3.49.1.0")

    add(mintPaperRuntime.name, mintPaper)

    testImplementation(project(":tradingpost-api"))
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(mintApi)
    testImplementation(mintPaper)
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

val prepareServerPlugins = tasks.register<Sync>("prepareServerPlugins") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    from(mintPaperRuntime.filter { file ->
        file.extension == "jar" && file.name.startsWith("mint-paper-")
    })
    into(rootProject.layout.projectDirectory.dir("run/plugins"))
}

tasks.named<xyz.jpenilla.runpaper.task.RunServer>("runServer") {
    minecraftVersion("1.21.11")
    dependsOn(prepareServerPlugins)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    reports.junitXml.required.set(false)
}

tasks.jar {
    archiveBaseName.set("tradingpost-paper")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
