# Trading Post Command Rename Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans (recommended). Steps use checkbox (`- [ ]`) syntax.

**Goal:** Rename `/ah` and `/ahadmin` to `/post` and `/postadmin` without changing Trading Post behavior.

**Architecture:** Keep the existing `TradingPostCommands` executor and all player/admin handlers. Change only command registration, descriptor metadata, command-facing usage text, and dispatch hardening: `post` calls the player handler, `postadmin` calls the admin handler, and any other command returns `false`.

**Tech Stack:** Java 21, Paper 1.21.11 API, JUnit 5, Gradle Kotlin DSL.

## Global Constraints

- `/post` is the only documented player command.
- `/postadmin` is the only documented administrator command.
- `/ah` and `/ahadmin` are removed; no aliases are retained.
- Existing `tradingpost.use` and `tradingpost.admin` permissions remain unchanged.
- Unknown command names must return `false` and must not enter the admin handler.
- Preserve the existing villager, market, and settlement behavior.

---

### Task 1: Rename command registrations and dispatch

**Files:**
- Modify: `src/main/java/dev/jlo/tradingpost/command/TradingPostCommands.java:34-43`
- Modify: `src/main/resources/paper-plugin.yml:19-27`
- Test: `src/test/java/dev/jlo/tradingpost/command/TradingPostCommandsTest.java` if the project test fixtures support command mocks; otherwise add a focused pure dispatch test beside the existing command tests.

**Interfaces:**
- `register()` binds `post` and `postadmin`.
- `onCommand()` dispatches `post` to `handleAh`, `postadmin` to `handleAdmin`, and returns `false` for every other command name.

- [ ] Add a failing dispatch test covering `post`, `postadmin`, and an unrelated command name.
- [ ] Replace `plugin.getCommand("ah")`/`getCommand("ahadmin")` with `post`/`postadmin` and implement explicit command-name branching.
- [ ] Rename descriptor command keys and descriptions while preserving permissions and argument structure.
- [ ] Run the focused command test or compile check and verify PASS.
- [ ] Commit the implementation and test as `feat: rename Trading Post commands`.

---

### Task 2: Update usage text and references

**Files:**
- Modify: `src/main/java/dev/jlo/tradingpost/command/TradingPostCommands.java:119-128`
- Modify: `src/main/resources/paper-plugin.yml:21-26`
- Modify: command-facing sections in `docs/superpowers/specs/2026-08-08-tradingpost-villager-design.md` and `docs/superpowers/plans/2026-08-08-tradingpost-villager.md` if they mention the old names.

- [ ] Search source, resources, and current TradingPost docs for `/ah` and `/ahadmin`.
- [ ] Replace every command-facing occurrence with `/post` and `/postadmin`; do not alter historical unrelated documents.
- [ ] Search again and verify no current command surface exposes the old names.
- [ ] Commit documentation/copy updates separately only if they are not part of Task 1's staged feature commit.

---

### Task 3: Verify the renamed command surface

**Files:**
- Modify: only files required by failing checks.

- [ ] Run `./gradlew test` and verify PASS.
- [ ] Run `./gradlew jar` and verify PASS.
- [ ] Run `./gradlew prepareServerPlugins` and verify the refreshed jar contains `paper-plugin.yml` with `post` and `postadmin`.
- [ ] Review the final command references and confirm `/post` opens the villager-local market while `/postadmin` retains administrative operations.
