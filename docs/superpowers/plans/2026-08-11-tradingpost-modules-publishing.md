# TradingPost Modules and GitHub Packages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split TradingPost into publishable `api` and `paper` Gradle modules and configure GitHub Packages publication for `aincraft-org/tradingpost`.

**Architecture:** The root project becomes an aggregate/base project. `api` is a Java library exposing `dev.jlo.tradingpost.api.TradingPostApi`; `paper` is the Java library containing the existing Paper plugin implementation and depends on `api`. Root publishing conventions create `tradingpost-api` and `tradingpost-paper` Maven artifacts and publish to the repository selected by `gpr.owner`/`gpr.repo`, defaulting to `aincraft-org/tradingpost`.

**Tech Stack:** Gradle Kotlin DSL, Java 25, Paper API, Maven Publish, GitHub Actions.

## Global Constraints

- Preserve the existing `dev.jlo.tradingpost.*` implementation namespaces.
- Publish artifacts as `dev.jlo.tradingpost:tradingpost-api` and `dev.jlo.tradingpost:tradingpost-paper`.
- GitHub Packages URL must default to `https://maven.pkg.github.com/aincraft-org/tradingpost`.
- Credentials must come from `GITHUB_ACTOR`/`GITHUB_TOKEN` or `gpr.user`/`gpr.key`; no secrets in the repository.
- Keep the existing Paper plugin buildable and its tests runnable.
- Keep local Maven publication available for artifact inspection without credentials.

---

### Task 1: Create the Gradle module structure

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Create: `api/build.gradle.kts`
- Create: `paper/build.gradle.kts`

**Interfaces:**
- `api` produces the Java library component consumed by `paper`.
- `paper` produces the runnable plugin jar and depends on `project(":api")`.

- [ ] Add `include("api")` and `include("paper")`; retain existing repositories and composite build.
- [ ] Convert root plugin application to `base`; set group/version centrally and add artifact naming/publishing conventions.
- [ ] Configure `api` with `java-library`, Java 21 toolchain, sources/javadoc jars, and JUnit.
- [ ] Configure `paper` with `java-library`, Java 25 toolchain, existing dependencies, `api(project(":api"))`, and plugin jar settings.
- [ ] Ensure the Paper run task remains available from `paper` or root as appropriate.

### Task 2: Move implementation and tests into `paper`

**Files:**
- Move: `src/main/java/**` -> `paper/src/main/java/**`
- Move: `src/main/resources/**` -> `paper/src/main/resources/**`
- Move: `src/test/java/**` -> `paper/src/test/java/**`

**Interfaces:**
- No Java package names change.
- Existing tests remain under the `paper` test source set and continue to exercise plugin behavior.

- [ ] Move all current implementation/resource/test files without changing their package declarations.
- [ ] Update resource paths only if the move exposes hard-coded root-relative assumptions.
- [ ] Remove the obsolete root `src` tree after verifying all files are represented in `paper`.

### Task 3: Add the public API contract

**Files:**
- Create: `api/src/main/java/dev/jlo/tradingpost/api/TradingPostApi.java`
- Create: `api/src/test/java/dev/jlo/tradingpost/api/TradingPostApiTest.java`

**Interfaces:**
```java
package dev.jlo.tradingpost.api;

public final class TradingPostApi {
    public static final String API_VERSION = "1.0.0";
    private TradingPostApi() {}
}
```

- [ ] Add the non-instantiable API identity class with the exact public constant.
- [ ] Add a focused test asserting the API version is present and stable.

### Task 4: Configure Maven publication

**Files:**
- Modify: `build.gradle.kts`
- Modify: `api/build.gradle.kts`
- Modify: `paper/build.gradle.kts`

**Interfaces:**
- `publishAllPublicationsToLocalBuildRepository` writes to `build/maven-repo`.
- `publishAllPublicationsToGitHubPackagesRepository` targets `https://maven.pkg.github.com/aincraft-org/tradingpost` by default.

- [ ] Apply `maven-publish` to both subprojects.
- [ ] Set artifact IDs `tradingpost-api` and `tradingpost-paper`.
- [ ] Add sources and javadoc jars and a minimal POM with project URL/SCM metadata.
- [ ] Configure local and GitHub Packages repositories with environment/property credentials.
- [ ] Ensure signing is optional for local/GitHub publication and never requires committed keys.

### Task 5: Add GitHub Actions publication workflow

**Files:**
- Create: `.github/workflows/publish.yml`

**Interfaces:**
- Workflow runs tests/build on pushes and publishes both Maven artifacts on version tags.

- [ ] Set `packages: write` permission and use `GITHUB_TOKEN` credentials.
- [ ] Set up JDK 25 and run `./gradlew test build`.
- [ ] On tags matching `v*`, run `./gradlew publishAllPublicationsToGitHubPackagesRepository`.

### Task 6: Set repository remote

**Files:**
- Git metadata only: `.git/config`

- [ ] Set `origin` fetch and push URLs to `https://github.com/aincraft-org/tradingpost.git`.
- [ ] Do not commit credentials or alter global Git configuration.

### Task 7: Verify module builds and artifacts

**Files:**
- No source changes unless verification exposes a defect.

- [ ] Run `./gradlew test build`.
- [ ] Run local publication and inspect both generated POMs/jars for correct coordinates and API class.
- [ ] Verify the Paper jar remains produced and contains the plugin descriptor.
- [ ] Confirm `git remote -v` reports the requested remote.
