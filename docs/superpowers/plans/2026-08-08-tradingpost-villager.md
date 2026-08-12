# Villager Trading Posts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans (recommended). Steps use checkbox (`- [ ]`) syntax.

**Goal:** Replace block-identified Trading Posts with persistent vanilla Villager NPCs, giving each NPC one isolated New World-style market.

**Architecture:** Add a persisted villager UUID to each trading-post row and index posts by entity UUID in `TradingPostRegistry`. Registration and removal target the villager an administrator is looking at; player entity interaction resolves that UUID to the existing market-scoped UI. A unique database constraint prevents two NPCs from sharing one market, while saved world coordinates continue to support `/post` radius access.

**Tech Stack:** Java 21, Paper 1.21.11 API, PostgreSQL migrations, JUnit 5, Gradle Kotlin DSL.

## Global Constraints

- No Guilds/Towny or Citizens dependency.
- Each registered villager has exactly one market; each market is assigned to at most one villager.
- Existing order, settlement, mailbox, and UI services remain market-scoped.
- JDBC remains on `AsyncExecutor`; Bukkit entity mutation and event handling remain on the main thread.
- Legacy rows are preserved by an additive migration; rows with no villager UUID are not exposed as new NPC posts.
- Do not modify unrelated dirty baseline files copied from the user's checkout.

---

### Task 1: Persist villager identity and enforce one market per post

**Files:**
- Create: `src/main/resources/db/migration/V2__villager_trading_posts.sql`
- Modify: `src/main/java/dev/jlo/tradingpost/db/MigrationRunner.java`
- Modify: `src/main/java/dev/jlo/tradingpost/db/MarketRepository.java`
- Modify: `src/main/java/dev/jlo/tradingpost/domain/TradingPostBlock.java`
- Modify: `src/main/resources/db/migration/V1__tradingpost.sql` only if needed to keep fresh installs equivalent
- Test: `src/test/java/dev/jlo/tradingpost/db/PostgresRepositoryTest.java`

**Interfaces:**
- `TradingPostBlock` gains `UUID entityId` while retaining `id`, `marketName`, `world`, and block coordinates.
- `MarketRepository.insertPost(Connection, TradingPostBlock)` inserts `entity_uuid` and rejects duplicate market/entity assignments through database constraints.
- `MarketRepository.listPosts(Connection)` selects `entity_uuid` and reconstructs the record.
- `MarketRepository.findPostByEntity(Connection, UUID)` and `deletePostByEntity(Connection, UUID)` provide entity-targeted persistence.
- `MigrationRunner.migrate` applies V1 then V2 exactly once using `schema_version`.

- [ ] Add a failing PostgreSQL migration assertion that `trading_posts.entity_uuid` exists and that duplicate `market_name`/`entity_uuid` assignments fail.
- [ ] Add V2 with `ALTER TABLE ... ADD COLUMN IF NOT EXISTS entity_uuid uuid`, unique partial indexes for non-null entity UUIDs and NPC market names, and a repository precheck that rejects reuse of legacy market names; retain world/coordinate columns for saved radius locations.
- [ ] Update migration loading/version bookkeeping so a fresh or existing schema applies V2 without rerunning it.
- [ ] Update repository SQL and `TradingPostBlock` construction to round-trip entity UUIDs, including nullable legacy rows if the database still contains old block posts.
- [ ] Run `./gradlew test --tests 'dev.jlo.tradingpost.db.PostgresRepositoryTest'` and verify PASS.
- [ ] Commit the persistence change as `feat: persist villager trading posts`.

---

### Task 2: Make registry access entity-based

**Files:**
- Modify: `src/main/java/dev/jlo/tradingpost/post/TradingPostRegistry.java`
- Modify: `src/main/java/dev/jlo/tradingpost/post/PostAccess.java`
- Modify: `src/main/java/dev/jlo/tradingpost/domain/TradingPostBlock.java` if naming/accessors need alignment
- Test: `src/test/java/dev/jlo/tradingpost/post/TradingPostAccessTest.java`

**Interfaces:**
- `TradingPostRegistry.marketAt(Entity)` returns `Optional<MarketContext>` only for registered villager UUIDs.
- `TradingPostRegistry.nearestWithin(Location, int)` ignores legacy rows whose entity UUID is null.
- `TradingPostRegistry.canAccess(Location, String, int)` remains the local market/radius check but only considers NPC-backed posts.
- `TradingPostRegistry.registerPost(String, Villager)` persists a unique NPC mapping and returns `CompletionStage<Void>`.
- `TradingPostRegistry.removePost(Entity)` removes the registered NPC mapping and returns `CompletionStage<Void>`.
- `PostAccess` adds entity-aware filtering helpers without Bukkit dependencies by accepting `TradingPostBlock` records with non-null entity IDs.

- [ ] Add failing unit coverage for entity UUID lookup, ignoring legacy rows, nearest NPC selection, and duplicate-market rejection at the registry contract boundary.
- [ ] Replace block-key indexing with a concurrent entity-UUID index plus the existing post collection for radius scans.
- [ ] Validate villager type, world, and unique mapping before asynchronous persistence; update the in-memory index only after the transaction succeeds.
- [ ] On registration and load, schedule/apply `setAI(false)` and `setInvulnerable(true)` on the main thread; do not mutate Bukkit entities from JDBC workers.
- [ ] Run the post access tests and compile checks.
- [ ] Commit the registry behavior with its tests as `feat: resolve trading posts by villager`.

---

### Task 3: Wire villager commands and events

**Files:**
- Modify: `src/main/java/dev/jlo/tradingpost/command/TradingPostCommands.java`
- Modify: `src/main/java/dev/jlo/tradingpost/post/TradingPostListener.java`
- Modify: `src/main/java/dev/jlo/tradingpost/TradingPostPlugin.java`
- Modify: `src/main/resources/paper-plugin.yml`
- Modify: `src/main/resources/config.yml` only for user-facing post wording
- Test: `src/test/java/dev/jlo/tradingpost/post/TradingPostAccessTest.java` or a focused command/listener test if existing mocks support entities

**Interfaces:**
- `/postadmin post set <market>` targets the player's looked-at `Villager` and calls `registry.registerPost`.
- `/postadmin post remove` targets the player's looked-at registered villager and calls `registry.removePost`.
- `TradingPostListener` handles `PlayerInteractEntityEvent` and `EntityDamageEvent`; block break/place handlers are removed.
- The plugin registers the listener with the same ready/use permission gates and opens `context.marketName()` through `TradingPostMenu`.

- [ ] Add failing command/listener coverage for non-villager targets, registered villager right-click opening, permission rejection, and damage cancellation.
- [ ] Replace target-block lookup with a bounded target-entity lookup and emit precise usage/errors for non-villager or unregistered entities.
- [ ] Add the entity interaction handler, cancel the interaction, and open the market only when `READY` and `tradingpost.use` are true.
- [ ] Cancel damage to registered trading villagers and keep admin removal command as the supported deletion path.
- [ ] Update `/post` and `/postadmin` usage text and command descriptions from block/market wording to villager/NPC wording.
- [ ] Run focused tests and `./gradlew jar`.
- [ ] Commit the command/event cutover as `feat: expose villager trading post commands`.

---

### Task 4: Full verification and review

**Files:**
- Modify: only files required by failing checks from Tasks 1–3
- Test: existing targeted test classes and full suite

- [ ] Run `./gradlew test` and record the exact result.
- [ ] Run `./gradlew jar` and verify the TradingPost jar is produced.
- [ ] Exercise the NPC path with a Paper smoke scenario: register two villagers with different markets, right-click each, verify each UI receives its market, reject a second NPC using an occupied market, and reject damage to both NPCs.
- [ ] Review the final diff for accidental edits to the user's unrelated baseline changes.
- [ ] Commit only any necessary verification fix as its own atomic commit.
