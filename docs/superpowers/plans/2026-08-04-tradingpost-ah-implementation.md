# TradingPost Auction House Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved New World-style TradingPost Paper plugin with durable PostgreSQL order books, Mint settlement, recovery, mailbox delivery, GUIs, and admin operations.

**Architecture:** One Paper plugin module owns market/order/mailbox state in a `tradingpost` PostgreSQL schema. Mint remains the sole money authority; TradingPost creates durable settlement intents before calling `LedgerService.transact`, then recovers `RESERVED -> MONEY_SETTLED -> DELIVERED` intents by deterministic idempotency key. Paper main-thread code only handles inventory and GUI operations; bounded executor threads handle JDBC and Mint.

**Tech Stack:** Java 21, Paper 1.21.11 API, Gradle Kotlin DSL, PostgreSQL JDBC, HikariCP, JUnit 5, Testcontainers PostgreSQL, Mint `dev.jlo.mint:mint-api:0.1.0-SNAPSHOT` and `dev.jlo.mint:mint-paper:1.0.0` resolved through the sibling composite build.

## Global Constraints

- The plugin descriptor dependency name is `Mint`, with `join-classpath: true`.
- Mint readiness is checked through `PaperMintAccess.mint().state() == MintState.READY`.
- TradingPost never writes Mint tables directly and never claims AH and Mint commits are atomic.
- System accounts are ensured through Mint `AccountService` under TradingPost's configured `ClientId`; player accounts must already exist.
- Every money operation has an AH settlement intent and deterministic unique Mint idempotency key.
- Match reservation decrements both order sides and inserts the fill plus settlement intent in one AH transaction.
- `fills.sell_order_id` and `fills.buy_order_id` are both non-null; Sell Now uses a durable fee-waived transient sell order.
- `REMOVED_UNPERSISTED` is an acknowledged inventory/JDBC crash window; it is not described as same-tick or atomic.
- No Paper thread blocks on JDBC, Mint futures, or executor shutdown.
- Rates use integer basis points; amounts use exact `BigDecimal` at the configured Mint currency scale.

---

## File map

Create the following focused units:

- `settings.gradle.kts` — root name, `../mint` composite build, and dependency substitution for published Mint coordinates.
- `build.gradle.kts` — Java 21, Paper, PostgreSQL, HikariCP, JUnit, Testcontainers.
- `src/main/resources/paper-plugin.yml` — plugin identity and required `Mint` dependency.
- `src/main/resources/config.yml` — database, Mint IDs, rates, durations, limits, intervals.
- `src/main/resources/db/migration/V1__tradingpost.sql` — schema, constraints, indexes.
- `src/main/java/dev/jlo/tradingpost/TradingPostPlugin.java` — Paper lifecycle coordinator.
- `src/main/java/dev/jlo/tradingpost/lifecycle/PluginState.java` — `STARTING`, `READY`, `DEGRADED`, `SHUTTING_DOWN`, `STOPPED`.
- `src/main/java/dev/jlo/tradingpost/lifecycle/AsyncExecutor.java` — bounded executor/admission and safe shutdown.
- `src/main/java/dev/jlo/tradingpost/config/TradingPostConfig.java` — immutable validated configuration.
- `src/main/java/dev/jlo/tradingpost/mint/MintGateway.java` — Mint service lookup, account/currency validation, transfer requests, receipt lookup.
- `src/main/java/dev/jlo/tradingpost/mint/MintTransfer.java` — immutable transfer legs and metadata.
- `src/main/java/dev/jlo/tradingpost/money/MoneyMath.java` — scale, basis-point, fee/tax, and surplus calculations.
- `src/main/java/dev/jlo/tradingpost/domain/OrderSide.java`, `OrderStatus.java`, `SellOrderMode.java`, `SettlementKind.java`, `SettlementState.java` — persisted enum contracts.
- `src/main/java/dev/jlo/tradingpost/domain/SellOrder.java`, `BuyOrder.java`, `Fill.java`, `Settlement.java`, `MailboxItem.java`, `Market.java`, `TradingPostBlock.java` — immutable records.
- `src/main/java/dev/jlo/tradingpost/items/ItemCodec.java` — Paper ItemStack bytes and SHA-256 fingerprints.
- `src/main/java/dev/jlo/tradingpost/db/Database.java`, `MigrationRunner.java`, `TransactionCallback.java` — JDBC pool, migrations, local AH transactions.
- `src/main/java/dev/jlo/tradingpost/db/OrderRepository.java`, `SettlementRepository.java`, `MailboxRepository.java`, `MarketRepository.java`, `ReviewRepository.java` — SQL authority.
- `src/main/java/dev/jlo/tradingpost/market/MatchingEngine.java` — pure price-time matching decisions.
- `src/main/java/dev/jlo/tradingpost/market/OrderService.java` — placement, cancellation, Sell Now transient orders, and matching reservation.
- `src/main/java/dev/jlo/tradingpost/settlement/SettlementService.java` — reserve, submit, advance, compensate, and deliver.
- `src/main/java/dev/jlo/tradingpost/settlement/SettlementRecoveryWorker.java` — leases and receipt-driven recovery.
- `src/main/java/dev/jlo/tradingpost/settlement/ExpiryWorker.java`, `ReconciliationWorker.java` — scheduled maintenance.
- `src/main/java/dev/jlo/tradingpost/mailbox/MailboxService.java` — item return and claim state transitions.
- `src/main/java/dev/jlo/tradingpost/post/TradingPostRegistry.java`, `TradingPostListener.java` — block registration and interaction gating.
- `src/main/java/dev/jlo/tradingpost/command/TradingPostCommands.java` — `/ah` and `/ahadmin`.
- `src/main/java/dev/jlo/tradingpost/ui/TradingPostHolder.java`, `TradingPostMenu.java`, `OrderDetailMenu.java`, `SellMenu.java`, `BuyOrderMenu.java`, `MyOrdersMenu.java`, `MailboxMenu.java` — inventory UI.
- `src/test/java/dev/jlo/tradingpost/money/MoneyMathTest.java`, `market/MatchingEngineTest.java`, `settlement/SettlementStateMachineTest.java`, `items/ItemCodecTest.java` — deterministic unit tests.
- `src/test/java/dev/jlo/tradingpost/db/PostgresRepositoryTest.java`, `settlement/RecoveryIntegrationTest.java` — Testcontainers integration tests.

---

### Task 1: Scaffold plugin and Mint lifecycle integration

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`.
- Create: `src/main/resources/paper-plugin.yml`, `src/main/resources/config.yml`.
- Create: `src/main/java/dev/jlo/tradingpost/TradingPostPlugin.java`.
- Create: `src/main/java/dev/jlo/tradingpost/lifecycle/PluginState.java`.
- Create: `src/main/java/dev/jlo/tradingpost/lifecycle/AsyncExecutor.java`.
- Create: `src/main/java/dev/jlo/tradingpost/config/TradingPostConfig.java`.
- Create: `src/main/java/dev/jlo/tradingpost/mint/MintGateway.java`.
- Create: `src/main/java/dev/jlo/tradingpost/mint/MintTransfer.java`.

**Interfaces:**
- `MintGateway.ready(): boolean` returns true only when the registered `PaperMintAccess` provider returns `MintState.READY`.
- `MintGateway.ensureSystemAccounts(): CompletionStage<Boolean>` calls `client.accounts().ensure` for escrow, fee, and tax accounts and fails on a foreign-owner result.
- `MintGateway.requirePlayerAccount(UUID): CompletionStage<Boolean>` calls `client.accounts().exists(AccountId.player(uuid))`.
- `MintGateway.transfer(IdempotencyKey, ActorId, List<Posting>, String, Map<String,String>): CompletionStage<OperationOutcome<TransactionReceipt>>` calls only `ledger().transact`.
- `MintGateway.receipt(IdempotencyKey): CompletionStage<Optional<TransactionReceipt>>` calls `ledger().receipt`.

**Steps:**

- [ ] Add the composite build and dependencies. Use `includeBuild("../mint")`, `implementation("dev.jlo.mint:mint-api:0.1.0-SNAPSHOT")`, `compileOnly("dev.jlo.mint:mint-paper:1.0.0")`, `compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")`, PostgreSQL JDBC, HikariCP, JUnit 5, and Testcontainers. Do not use `project(":mint-api")`: the AH build has no such project path.
- [ ] Run `./gradlew dependencies --configuration compileClasspath` and verify the composite build resolves `dev.jlo.mint:mint-api:0.1.0-SNAPSHOT` and `dev.jlo.mint:mint-paper:1.0.0` from `../mint` before writing integration imports.
- [ ] Add `paper-plugin.yml` with `name: TradingPost`, `main: dev.jlo.tradingpost.TradingPostPlugin`, `api-version: '1.21'`, `load: POSTWORLD`, and required server dependency `Mint` with `load: BEFORE` and `join-classpath: true`.
- [ ] Implement config parsing for JDBC URL/user/password/schema, Mint client/currency/account IDs, rates, limits, intervals, and durations. Reject negative rates, zero durations, invalid IDs, and a currency scale outside `0..18`.
- [ ] Implement a bounded executor with a semaphore admission limit and virtual-thread tasks. `submit(Callable<T>)` returns `CompletionStage<T>` and releases admission in `whenComplete`.
- [ ] Implement `TradingPostPlugin.onEnable` as: load config; initialize executor/database; obtain `PaperMintAccess`; poll readiness with bounded backoff; validate currency and ensure TradingPost-owned accounts; only then set `READY` and register listeners/commands.
- [ ] Implement shutdown as: set `SHUTTING_DOWN`; reject new mutations; stop workers; await executor drain for configured grace; close Hikari; set `STOPPED`.
- [ ] Run `./gradlew test` and `./gradlew jar`; expected result is a successful empty plugin jar build with no runtime work enabled yet.
- [ ] Commit: `feat: scaffold TradingPost and Mint integration`.

---

### Task 2: Add AH schema, migrations, and repositories

**Files:**
- Create: `src/main/resources/db/migration/V1__tradingpost.sql`.
- Create: `src/main/java/dev/jlo/tradingpost/db/Database.java`, `MigrationRunner.java`, `TransactionCallback.java`.
- Create: `src/main/java/dev/jlo/tradingpost/db/OrderRepository.java`, `SettlementRepository.java`, `MailboxRepository.java`, `MarketRepository.java`, `ReviewRepository.java`.
- Create: domain records and enums listed in the file map.

**Interfaces:**
- `Database.transaction(TransactionCallback<T>): T` owns one AH JDBC transaction and rolls back on any exception.
- `OrderRepository.reserveMatch(Connection, UUID sellId, UUID buyId, int maxQuantity): FillReservation` locks both orders, decrements both, and inserts the fill plus settlement intent in the same connection transaction.
- `SettlementRepository.insertReserved(Connection, SettlementDraft): void` rejects duplicate idempotency keys through a database unique constraint.
- `SettlementRepository.leaseNext(Connection, String nodeId, Instant now): Optional<Settlement>` uses `FOR UPDATE SKIP LOCKED` and a lease expiry.
- `MailboxRepository.insertDelivery(Connection, UUID settlementId, MailboxDelivery): void` uses unique `settlement_id` to make delivery idempotent.

**Steps:**

- [ ] Write `V1__tradingpost.sql` with schema creation, `markets`, `trading_posts`, `sell_orders`, `buy_orders`, `fills`, `settlements`, `mailbox_items`, and `review_queue`.
- [ ] Make `fills.sell_order_id` and `fills.buy_order_id` `NOT NULL` foreign keys. Add `sell_orders.mode` with `NORMAL` and `INSTANT`, so Sell Now has a durable source row.
- [ ] Add `UNIQUE (idempotency_key)` on settlements, `UNIQUE (settlement_id)` on mailbox delivery rows, and indexes for market/price/time order queries and settlement leases.
- [ ] Add CHECK constraints for valid status/state strings, nonnegative quantities, positive prices, and lease owner/expiry pairing.
- [ ] Implement migration locking and schema version validation. Migrations must run before repositories are exposed.
- [ ] Implement repository methods with prepared statements only. `reserveMatch` must lock orders in deterministic UUID order, validate market and active statuses, calculate `min(sell.qty_remaining,buy.qty_remaining)`, decrement both, insert fill and settlement, and commit through `Database.transaction`.
- [ ] Implement repository reads for best bids/asks, paginated browse, player orders, expired orders, stale settlements, and mailbox rows.
- [ ] Add repository tests against Testcontainers PostgreSQL for migrations, unique keys, partial fill decrement, concurrent lock behavior, and duplicate delivery prevention.
- [ ] Run `./gradlew test --tests 'dev.jlo.tradingpost.db.*'`; expected result is PASS.
- [ ] Commit: `feat: add TradingPost PostgreSQL schema and repositories`.

---

### Task 3: Implement money math and pure order matching

**Files:**
- Create: `src/main/java/dev/jlo/tradingpost/money/MoneyMath.java`.
- Create: `src/main/java/dev/jlo/tradingpost/market/MatchingEngine.java`.
- Create: `src/test/java/dev/jlo/tradingpost/money/MoneyMathTest.java`.
- Create: `src/test/java/dev/jlo/tradingpost/market/MatchingEngineTest.java`.

**Interfaces:**
- `MoneyMath.canonical(BigDecimal amount, int scale): BigDecimal` uses `RoundingMode.UNNECESSARY`.
- `MoneyMath.basisPoints(BigDecimal gross, int bps, int scale): BigDecimal` computes tax with deterministic downward rounding and returns canonical scale.
- `MoneyMath.sellerNet(BigDecimal gross, BigDecimal tax): BigDecimal` returns `gross.subtract(tax)`.
- `MatchingEngine.matchBuyAgainstSells(BuyOrder taker, List<SellOrder> asks): List<MatchDecision>` sorts asks by price ascending/time ascending and stops above max price.
- `MatchingEngine.matchSellNowAgainstBuys(SellOrder transientTaker, List<BuyOrder> bids): List<MatchDecision>` sorts bids by price descending/time ascending and consumes the transient quantity.

**Steps:**

- [ ] Write failing tests for scale rejection (`1.001` at scale 2), tax rounding, zero-tax balance, price surplus refund, exact template mismatch, material-only match, price priority, time tie-break, partial fills, and no-cross price boundaries.
- [ ] Implement `MoneyMath` with no floating-point operations.
- [ ] Implement `MatchingEngine` as a pure class with immutable decisions; it must never mutate repository rows or call Mint.
- [ ] Add tests that assert every decision includes source order, counter order, quantity, execution price, gross, tax, and seller net.
- [ ] Run `./gradlew test --tests 'dev.jlo.tradingpost.money.*' --tests 'dev.jlo.tradingpost.market.*'`; expected result is PASS.
- [ ] Commit: `feat: add TradingPost price matching and money math`.

---

### Task 4: Implement settlement intents, Mint transfers, and recovery

**Files:**
- Create: `src/main/java/dev/jlo/tradingpost/settlement/SettlementService.java`.
- Create: `src/main/java/dev/jlo/tradingpost/settlement/SettlementRecoveryWorker.java`.
- Create: `src/main/java/dev/jlo/tradingpost/settlement/ReconciliationWorker.java`.
- Modify: `src/main/java/dev/jlo/tradingpost/mint/MintGateway.java`.
- Create: `src/test/java/dev/jlo/tradingpost/settlement/SettlementStateMachineTest.java`.
- Create: `src/test/java/dev/jlo/tradingpost/settlement/RecoveryIntegrationTest.java`.

**Interfaces:**
- `SettlementService.submitReserved(UUID settlementId): CompletionStage<Void>` loads one reserved intent, builds one balanced Mint transfer, and advances based on the `OperationOutcome`.
- `SettlementService.deliver(UUID settlementId): CompletionStage<Void>` inserts mailbox/fill delivery with a unique settlement key and marks `DELIVERED`.
- `SettlementService.compensate(UUID settlementId, Rejection rejection): CompletionStage<Void>` applies operation-specific AH compensation without inventing money.
- `SettlementRecoveryWorker.runOnce(): CompletionStage<Integer>` leases stale rows, resolves receipts, retries absent receipts with the same key, and calls completion/compensation.
- `ReconciliationWorker.runOnce(): CompletionStage<ReconciliationReport>` compares `MintGateway.balance(escrow)` with open-order obligations and inserts review rows on divergence.

**Steps:**

- [ ] Implement transfer builders for `BUY_ESCROW`, `LISTING_FEE`, `MATCH_SETTLEMENT`, `REFUND`, and `FEE_REFUND`. Each builder produces balanced `Posting` values and uses an actor in the TradingPost namespace.
- [ ] Before every Mint call, require configured currency and all source/destination account existence. A missing player account is a rejected business outcome; do not call Mint with an unknown source.
- [ ] Implement the state transitions with guarded SQL updates: `RESERVED -> MONEY_SETTLED`, then `MONEY_SETTLED -> DELIVERED`; a compensation transaction moves `RESERVED -> FAILED` only after restoring AH state.
- [ ] Implement recovery receipt lookup. `Committed` advances; absent retries with the same key; `INDETERMINATE` remains unresolved until a later receipt lookup; business rejection invokes compensation.
- [ ] Implement match compensation to restore both order quantities and mark the fill `VOIDED` in one AH transaction.
- [ ] Implement operator review insertion for failed refunds, failed fee refunds, escrow divergence, and ambiguous inventory returns.
- [ ] Write unit tests for every transition and crash point: before Mint, after Mint commit before AH advance, after money settlement before delivery, rejection, duplicate retry, and duplicate delivery.
- [ ] Run `./gradlew test --tests 'dev.jlo.tradingpost.settlement.*'`; expected result is PASS.
- [ ] Commit: `feat: add durable Mint settlement recovery`.

---

### Task 5: Implement orders, transient Sell Now, mailbox, and expiry

**Files:**
- Create: `src/main/java/dev/jlo/tradingpost/items/ItemCodec.java`.
- Create: `src/main/java/dev/jlo/tradingpost/market/OrderService.java`.
- Create: `src/main/java/dev/jlo/tradingpost/settlement/ExpiryWorker.java`.
- Create: `src/main/java/dev/jlo/tradingpost/mailbox/MailboxService.java`.
- Create: `src/test/java/dev/jlo/tradingpost/items/ItemCodecTest.java`.

**Interfaces:**
- `ItemCodec.encode(ItemStack): byte[]` uses Paper's item-byte serialization.
- `ItemCodec.fingerprint(byte[]): String` returns lowercase SHA-256.
- `OrderService.placeSell(Player, SellDraft): CompletionStage<OrderResult>` removes the selected stack on the main thread, enters `REMOVED_UNPERSISTED`, persists `CREATING`, retries insert failures on the executor, charges listing fee only after durability, and returns the order result.
- `OrderService.placeSellNow(Player, SellDraft): CompletionStage<OrderResult>` follows the same durable `CREATING` path with `mode=INSTANT`, no listing fee, then reserves matches against bids.
- `OrderService.placeBuy(Player, BuyDraft): CompletionStage<OrderResult>` persists `CREATING` plus `BUY_ESCROW`, then opens and immediately crosses asks after escrow commit.
- `OrderService.cancel(UUID player, UUID orderId): CompletionStage<Void>` locks ownership, reserves refund if buy-side, or inserts seller mailbox return if sell-side.
- `MailboxService.claim(Player, UUID mailboxId): CompletionStage<ClaimResult>` uses `CLAIMING` state, main-thread inventory insertion, reconnect fingerprint reconciliation, and never deletes the row before successful claim confirmation.

**Steps:**

- [ ] Implement ItemStack encoding/decoding and fingerprints; tests must preserve NBT, enchantments, durability, custom name, and stack amount.
- [ ] Implement normal sell placement with the exact `REMOVED_UNPERSISTED -> CREATING -> ACTIVE` flow. While the process is alive, restore the captured stack on bounded persistence failure. Do not describe the crash window as atomic or microsecond-scale.
- [ ] Implement Sell Now as a durable transient sell order before any fill row or Mint settlement. It is `mode=INSTANT`, has no listing-fee intent, and uses the same fill/recovery/mailbox path as normal sells. Unmatched quantity is mailbox-returned from the durable order.
- [ ] Implement buy escrow placement and immediate ask matching. For price surplus, leave the surplus in `escrow_reserved` and refund it when the order closes.
- [ ] Implement cancel and expiry transitions with settlement intents created in AH transactions before Mint calls.
- [ ] Add a crash-after-removal regression test: a transient Sell Now order must be durable before matching; recovery must have a non-null sell-order source and either deliver a split item or return the remainder.
- [ ] Add mailbox claim/reconnect tests for full inventory, crash after `CLAIMING`, and matching fingerprint present/absent.
- [ ] Run `./gradlew test --tests 'dev.jlo.tradingpost.items.*' --tests 'dev.jlo.tradingpost.market.OrderService*'`; expected result is PASS.
- [ ] Commit: `feat: add TradingPost order and mailbox lifecycle`.

---

### Task 6: Add markets, posts, commands, and permissions

**Files:**
- Create: `src/main/java/dev/jlo/tradingpost/post/TradingPostRegistry.java`.
- Create: `src/main/java/dev/jlo/tradingpost/post/TradingPostListener.java`.
- Create: `src/main/java/dev/jlo/tradingpost/command/TradingPostCommands.java`.
- Modify: `src/main/java/dev/jlo/tradingpost/TradingPostPlugin.java`.

**Interfaces:**
- `TradingPostRegistry.marketAt(Location): Optional<MarketContext>` uses exact registered block coordinates.
- `TradingPostRegistry.registerPost(String market, Location): CompletionStage<Void>` validates an existing market and unique coordinates.
- `TradingPostCommands.register(LifecycleEvent): void` installs `/ah` and `/ahadmin` with permission checks.

**Steps:**

- [ ] Implement market and post repositories/registry, including market-specific fee/tax overrides.
- [ ] Cancel ordinary break/place events at registered post blocks unless the player has `tradingpost.admin`.
- [ ] Implement right-click gating: require `tradingpost.use`, locate market by block/radius, and open the UI only when the plugin is `READY`.
- [ ] Implement `/ah`, `/ahadmin post set/remove`, `/ahadmin market create/setfee/settax`, `/ahadmin reload`, `/ahadmin review`, and `/ahadmin cancel`.
- [ ] Ensure recovery commands are auditable and cannot force a refund without creating the required `FEE_REFUND` or `REFUND` intent.
- [ ] Run command/registry tests with mocked locations and permissions; expected result is PASS.
- [ ] Commit: `feat: add TradingPost markets and admin commands`.

---

### Task 7: Implement asynchronous inventory GUI

**Files:**
- Create: `src/main/java/dev/jlo/tradingpost/ui/TradingPostHolder.java`.
- Create: `src/main/java/dev/jlo/tradingpost/ui/TradingPostMenu.java`.
- Create: `src/main/java/dev/jlo/tradingpost/ui/OrderDetailMenu.java`.
- Create: `src/main/java/dev/jlo/tradingpost/ui/SellMenu.java`.
- Create: `src/main/java/dev/jlo/tradingpost/ui/BuyOrderMenu.java`.
- Create: `src/main/java/dev/jlo/tradingpost/ui/MyOrdersMenu.java`.
- Create: `src/main/java/dev/jlo/tradingpost/ui/MailboxMenu.java`.

**Interfaces:**
- Every menu uses an `InventoryHolder` subtype carrying `marketName`, `playerUuid`, `page`, and current screen.
- `TradingPostMenu.open(Player, String market): void` opens a loading inventory and asynchronously fetches the first page.
- `OrderDetailMenu.confirmBuy(Player, UUID orderId, int quantity): CompletionStage<Void>` calls `OrderService` only after reloading/locking current price and quantity.

**Steps:**

- [ ] Build the five tabs: Browse & Buy, Sell, Buy Orders, My Orders, Mailbox.
- [ ] Render exact sell item blobs with price, quantity, seller, and expiry lore; never trust stale GUI values during confirmation.
- [ ] Add pagination, material-name search, quantity buttons, duration controls, price increments, chat numeric input, and confirmation summaries.
- [ ] Add Sell Now using best bid and show the fee-waived result; normal sell preview shows listing fee and tax estimate.
- [ ] Ensure every asynchronous continuation returns to the Paper main thread before touching inventories or players.
- [ ] Cancel/reopen stale menus safely when a player disconnects or the market is no longer available.
- [ ] Run a Paper plugin smoke test that opens every tab and confirms a stale-order click is rejected instead of overfilling.
- [ ] Commit: `feat: add TradingPost inventory interface`.

---

### Task 8: Wire workers, lifecycle, and full verification

**Files:**
- Modify: `src/main/java/dev/jlo/tradingpost/TradingPostPlugin.java`.
- Modify: `src/main/resources/config.yml`.
- Create: `docs/superpowers/verification/2026-08-04-tradingpost-smoke.md` only if the smoke procedure needs durable operator documentation.

**Steps:**

- [ ] Register settlement recovery every 15 seconds, expiry every 60 seconds, and escrow reconciliation every 10 minutes using executor-owned tasks.
- [ ] Stop workers before closing the pool and make each worker idempotent across plugin restarts.
- [ ] Run the unit suite: `./gradlew test`; expected result is PASS.
- [ ] Run the jar build: `./gradlew clean jar`; expected result is PASS and a TradingPost jar is produced.
- [ ] Run PostgreSQL integration tests with Testcontainers; expected result is PASS with unique settlement/delivery/refund constraints exercised.
- [ ] Launch a Paper test server with Mint installed and exercise: post registration; sell order; buy order; partial match; Mint balance changes; mailbox claim; cancel; expiry; stale-settlement restart recovery; and Sell Now transient-order recovery.
- [ ] Confirm no server log contains a Paper-thread blocking warning or an AH/Mint atomicity claim.
- [ ] Commit: `test: verify TradingPost end-to-end recovery`.

---

## Plan self-review

- **Spec coverage:** Tasks 1–2 cover Paper/Mint/PostgreSQL setup and schema; Task 3 covers matching/math; Tasks 4–5 cover every settlement, inventory, Sell Now, mailbox, expiry, and reconciliation rule; Task 6 covers markets/posts/commands; Task 7 covers all UI screens; Task 8 covers workers and end-to-end verification.
- **Crash coverage:** Task 4 tests Mint commit before AH advance; Task 5 explicitly tests crash after inventory removal and requires durable transient Sell Now source rows.
- **Mint ownership coverage:** Task 1 ensures only TradingPost-owned system accounts; player account existence is checked without attempting foreign ownership.
- **Placeholder scan:** No task uses `TODO`, `TBD`, or an undefined follow-up. Every named interface is assigned to a file and every later consumer is stated.
- **Cross-task type consistency:** `SellOrderMode.INSTANT`, `SettlementKind`, `SettlementState`, `SettlementService`, `OrderService`, and repository method names are defined before their consumers. `fills.sell_order_id` is non-null everywhere, including Sell Now.
