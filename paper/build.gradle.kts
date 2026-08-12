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
        name = "craftuxGitHubPackages"
        url = uri("https://maven.pkg.github.com/aincraft-org/craftux")
        credentials {
            username = providers.gradleProperty("gpr.user")
                .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                .getOrElse("")
            password = providers.gradleProperty("gpr.key")
                .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                .getOrElse("")
        }
    }
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

val craftuxVersion = "1.0.2"
val craftuxApi = "dev.craftux:craftux-api:$craftuxVersion"
val craftuxCommon = "dev.craftux:craftux-common:$craftuxVersion"
val craftuxPaper = "dev.craftux:craftux-paper:$craftuxVersion"
val mintApi = "dev.jlo.mint:mint-api:1.0.0"
val mintPaper = "dev.jlo.mint:mint-paper:1.0.0"

val mintPaperRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val craftuxPaperRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    api(project(":api"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(craftuxApi)
    compileOnly(craftuxCommon)
    compileOnly(craftuxPaper)
    compileOnly(mintApi)
    compileOnly(mintPaper)
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("org.postgresql:postgresql:42.7.5")

    add(mintPaperRuntime.name, mintPaper)
    add(craftuxPaperRuntime.name, craftuxPaper)

    testImplementation(project(":api"))
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(craftuxApi)
    testImplementation(craftuxCommon)
    testImplementation(craftuxPaper)
    testImplementation(mintApi)
    testImplementation(mintPaper)
    testImplementation("com.zaxxer:HikariCP:6.2.1")
    testImplementation("org.postgresql:postgresql:42.7.5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

val prepareServerPlugins = tasks.register<Sync>("prepareServerPlugins") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    from(mintPaperRuntime.filter { file ->
        file.extension == "jar" && file.name.startsWith("mint-paper-")
    })
    from(craftuxPaperRuntime.filter { file ->
        file.extension == "jar" && file.name.startsWith("craftux-paper-")
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
}

tasks.jar {
    archiveBaseName.set("tradingpost-paper")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
