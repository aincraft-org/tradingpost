# Sell Now Settlement Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make multi-fill Sell Now settlement safe under separate Mint outcomes without duplicating or silently restoring shared item state.

**Architecture:** Add a durable `sell_now_operations` aggregate linked to every Sell Now fill and match settlement. Reserve fragments without mailing the remainder; finalize only after all fills are terminal. Since Mint transfers remain separate, all-failure operations may be compensated automatically, while any committed or indeterminate mixed outcome becomes `REVIEW` and never mutates the aggregate source order.

**Tech Stack:** Java 21, Paper, PostgreSQL migrations, JDBC repositories, JUnit 5, Testcontainers.

## Global Constraints

- Mint transfers remain separate transaction boundaries; do not claim cross-transfer atomicity.
- `FAILED` is valid only when every operation fill is known uncommitted and undelivered.
- Any committed, delivered, or indeterminate fill outcome transitions the operation to `REVIEW` on failure.
- Once an operation has a committed/delivered fill, compensation MUST NOT mutate the aggregate Sell Now source order.
- Do not alter ordinary one-fill order matching, fees, taxes, or New World-style pricing.
- Preserve existing user modifications in the original checkout; all implementation occurs in `fix/sell-now-safety` worktree.

---

### Task 1: Add durable Sell Now operation schema and repository

**Files:**
- Create: `paper/src/main/resources/db/migration/V3__sell_now_operations.sql`
- Create: `paper/src/main/java/dev/mintychochip/tradingpost/domain/SellNowOperation.java`
- Create: `paper/src/main/java/dev/mintychochip/tradingpost/db/SellNowOperationRepository.java`
- Modify: `paper/src/main/java/dev/mintychochip/tradingpost/db/OrderRepository.java`
- Test: `paper/src/test/java/dev/mintychochip/tradingpost/db/PostgresRepositoryTest.java`

**Interfaces:**
- `SellNowOperationRepository.insert(Connection, SellNowOperation)` persists `RESERVED` operations.
- `SellNowOperationRepository.find(Connection, UUID, boolean)` loads and optionally locks an operation.
- `SellNowOperationRepository.updateState(Connection, UUID, OperationState, String)` performs guarded state transitions.
- `SellNowOperationRepository.summarizeFills(Connection, UUID)` returns fill IDs and settlement states grouped by operation.
- `OrderRepository.reserveMatch(...)` accepts an optional `UUID operationId` and persists it on the fill/settlement association.

- [ ] **Step 1: Write the failing migration/repository test**

Add a Testcontainers test that creates a Sell Now operation, reserves two fills linked to it, reloads it, and asserts the operation ID is present on both fill rows. Assert the operation state starts as `RESERVED` and the original item blob/quantity are unchanged.

```java
@Test
void sellNowOperationPersistsSourceAndFillAssociation() {
  // insert operation and two operation-linked fills in one database transaction
  // reload operation and query fills by operation_id
  // assert source quantity/blob and exactly two fill IDs
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew :paper:test --tests '*PostgresRepositoryTest.sellNowOperationPersistsSourceAndFillAssociation'
```

Expected: FAIL because migration, operation repository, and operation linkage do not exist.

- [ ] **Step 3: Add migration and domain/repository code**

Create `V3__sell_now_operations.sql` with `operation_id uuid primary key`, unique `sell_order_id`, seller/market/source blob/fingerprint/original and remaining quantities, state constrained to `RESERVED`, `SETTLING`, `COMPLETED`, `FAILED`, `REVIEW`, failure detail, and timestamps. Add `operation_id uuid` to `fills` and `settlements`, with indexes and foreign keys. Add matching Java enum/record and guarded repository methods. Keep `remaining_item_blob` semantics unchanged: it remains the persisted pre-match source blob used by ordinary compensation.

- [ ] **Step 4: Run the focused test**

Run the command above. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add paper/src/main/resources/db/migration/V3__sell_now_operations.sql paper/src/main/java/dev/mintychochip/tradingpost/domain/SellNowOperation.java paper/src/main/java/dev/mintychochip/tradingpost/db/SellNowOperationRepository.java paper/src/main/java/dev/mintychochip/tradingpost/db/OrderRepository.java paper/src/test/java/dev/mintychochip/tradingpost/db/PostgresRepositoryTest.java
git commit -m "feat: persist Sell Now operations"
```

---

### Task 2: Make Sell Now reservation operation-aware

**Files:**
- Modify: `paper/src/main/java/dev/mintychochip/tradingpost/market/OrderService.java`
- Modify: `paper/src/main/java/dev/mintychochip/tradingpost/db/OrderRepository.java`
- Test: `paper/src/test/java/dev/mintychochip/tradingpost/settlement/SettlementServiceIntegrationTest.java`

**Interfaces:**
- `OrderService.reserveSellNowMatches(...)` creates one operation before reserving fills and returns `PersistResult` containing `operationId`.
- `OrderRepository.reserveMatch(...)` persists `operation_id` for operation fills.
- No `MailboxRepository.insert(..., "SELL_NOW_REMAINDER", ...)` occurs during reservation.

- [ ] **Step 1: Write the failing test**

Add a real `ItemCodec` integration test that reserves a Sell Now stack with one matched and one unmatched fragment, then asserts the source order remains durable and no `SELL_NOW_REMAINDER` mailbox row exists before settlements complete.

```java
@Test
void sellNowReservationDefersRemainderMailboxUntilFinalization() {
  // reserve operation/fill using encoded stack
  // assert mailbox count for SELL_NOW_REMAINDER is zero
  // assert operation is RESERVED and source order remains present
}
```

- [ ] **Step 2: Run test to verify failure**

```bash
./gradlew :paper:test --tests '*SettlementServiceIntegrationTest.sellNowReservationDefersRemainderMailboxUntilFinalization'
```

Expected: FAIL because current reservation inserts the remainder immediately.

- [ ] **Step 3: Implement operation-aware reservation**

Create and persist the operation with the original encoded stack before reserving fills. Pass the operation ID into each `reserveMatch` call. Remove the immediate remainder mailbox insertion and source-order cancellation. Store the current remainder quantity only as operation metadata; do not reconstruct source state from individual fill snapshots.

- [ ] **Step 4: Run focused tests**

Run the new test plus existing matching and settlement tests. Expected: PASS except any finalization assertions not yet implemented; keep this task limited to reservation behavior and adjust the test to assert only reservation invariants.

- [ ] **Step 5: Commit**

```bash
git add paper/src/main/java/dev/mintychochip/tradingpost/market/OrderService.java paper/src/main/java/dev/mintychochip/tradingpost/db/OrderRepository.java paper/src/test/java/dev/mintychochip/tradingpost/settlement/SettlementServiceIntegrationTest.java
git commit -m "fix: defer Sell Now remainder delivery"
```

---

### Task 3: Add operation settlement coordinator and safe finalization

**Files:**
- Create: `paper/src/main/java/dev/mintychochip/tradingpost/settlement/SellNowSettlementCoordinator.java`
- Modify: `paper/src/main/java/dev/mintychochip/tradingpost/settlement/SettlementService.java`
- Modify: `paper/src/main/java/dev/mintychochip/tradingpost/db/MailboxRepository.java`
- Modify: `paper/src/main/java/dev/mintychochip/tradingpost/db/SellNowOperationRepository.java`
- Test: `paper/src/test/java/dev/mintychochip/tradingpost/settlement/SettlementServiceIntegrationTest.java`

**Interfaces:**
- `SellNowSettlementCoordinator.onFillTerminal(UUID operationId)` evaluates all operation fills under a database lock.
- `SellNowSettlementCoordinator.finalizeCompleted(...)` inserts buyer deliveries idempotently, inserts one seller remainder mailbox row using `sell-now-remainder:<operationId>`, cancels the source order, and marks the operation `COMPLETED`.
- `SellNowSettlementCoordinator.markReview(...)` stores a review item with operation/fill/settlement states and marks the operation `REVIEW`.

- [ ] **Step 1: Write failing all-success finalization test**

Add a test with two committed fills and a remainder. Assert exactly one remainder mailbox row, source order cancellation, operation `COMPLETED`, and repeated finalization does not create another row.

- [ ] **Step 2: Run and verify failure**

```bash
./gradlew :paper:test --tests '*SettlementServiceIntegrationTest.sellNowAllCommittedFinalizesOnce'
```

Expected: FAIL because no coordinator or operation finalization exists.

- [ ] **Step 3: Implement guarded finalization**

Load and lock the operation. If already `COMPLETED`, return. Require every associated fill settlement to be `DELIVERED`. Insert the remainder with a deterministic operation-level settlement/delivery key using `ON CONFLICT DO NOTHING`; only then cancel the source Sell Now order and advance the operation to `COMPLETED`. Do not mutate the source order from fill-local historical blobs.

- [ ] **Step 4: Run focused test**

Expected: PASS, including repeated finalization.

- [ ] **Step 5: Commit**

```bash
git add paper/src/main/java/dev/mintychochip/tradingpost/settlement/SellNowSettlementCoordinator.java paper/src/main/java/dev/mintychochip/tradingpost/settlement/SettlementService.java paper/src/main/java/dev/mintychochip/tradingpost/db/MailboxRepository.java paper/src/main/java/dev/mintychochip/tradingpost/db/SellNowOperationRepository.java paper/src/test/java/dev/mintychochip/tradingpost/settlement/SettlementServiceIntegrationTest.java
git commit -m "feat: finalize Sell Now operations atomically"
```

---

### Task 4: Guard compensation and quarantine mixed outcomes

**Files:**
- Modify: `paper/src/main/java/dev/mintychochip/tradingpost/settlement/SettlementService.java`
- Modify: `paper/src/main/java/dev/mintychochip/tradingpost/db/SellNowOperationRepository.java`
- Modify: `paper/src/main/java/dev/mintychochip/tradingpost/db/ReviewRepository.java`
- Test: `paper/src/test/java/dev/mintychochip/tradingpost/settlement/SettlementServiceIntegrationTest.java`

**Interfaces:**
- Operation fills never call aggregate `orders.compensateFill` after any operation fill is `MONEY_SETTLED` or `DELIVERED`.
- `markReview` records operation ID, source order ID, fill IDs, settlement IDs, states, quantities, and fingerprints.
- Pre-commit all-failure operations may restore the source order exactly once and become `FAILED`.

- [ ] **Step 1: Write failing mixed-outcome test**

Create two fills in one Sell Now operation. Configure fake Mint so the first transfer commits and the second rejects. Assert the operation becomes `REVIEW`, no aggregate source restoration occurs, no `SELL_NOW_REMAINDER` row is created, and a review record contains both fill/settlement IDs and states.

```java
@Test
void mixedSellNowOutcomeQuarantinesWithoutRestoringSourceAggregate() {
  // first fill committed, second rejected
  // assert REVIEW and review_queue detail
  // assert source order was not rebuilt from a fill blob
}
```

- [ ] **Step 2: Run and verify failure**

```bash
./gradlew :paper:test --tests '*SettlementServiceIntegrationTest.mixedSellNowOutcomeQuarantinesWithoutRestoringSourceAggregate'
```

Expected: FAIL because current compensation restores each match directly and has no operation review state.

- [ ] **Step 3: Implement outcome guard**

In `SettlementService.compensate`, load the operation for `MATCH_SETTLEMENT`. Lock and summarize all operation fills. If every fill is still reserved/uncommitted and undelivered, compensate the operation once and mark `FAILED`. If any fill is committed, delivered, or indeterminate, do not call aggregate compensation; write review details and mark `REVIEW`. Ensure repeated recovery sees terminal `REVIEW` and does not retry money or delivery automatically.

- [ ] **Step 4: Run focused tests**

Run the mixed-outcome test plus existing rejection/recovery tests. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add paper/src/main/java/dev/mintychochip/tradingpost/settlement/SettlementService.java paper/src/main/java/dev/mintychochip/tradingpost/db/SellNowOperationRepository.java paper/src/main/java/dev/mintychochip/tradingpost/db/ReviewRepository.java paper/src/test/java/dev/mintychochip/tradingpost/settlement/SettlementServiceIntegrationTest.java
git commit -m "fix: quarantine mixed Sell Now outcomes"
```

---

### Task 5: Add recovery and persisted blob mapping safeguards

**Files:**
- Modify: `paper/src/main/java/dev/mintychochip/tradingpost/settlement/SettlementRecoveryWorker.java`
- Modify: `paper/src/main/java/dev/mintychochip/tradingpost/settlement/SellNowSettlementCoordinator.java`
- Test: `paper/src/test/java/dev/mintychochip/tradingpost/settlement/SettlementServiceIntegrationTest.java`
- Test: `paper/src/test/java/dev/mintychochip/tradingpost/db/PostgresRepositoryTest.java`

**Interfaces:**
- Recovery leases operation-linked settlements and invokes operation coordination after each terminal transition.
- A completed operation is idempotent on restart.
- A review operation is not automatically mutated by recovery.

- [ ] **Step 1: Write failing recovery tests**

Add tests for:

```text
recovery after all fills committed creates one remainder row
recovery of REVIEW operation performs no duplicate transfer or mailbox insertion
persisted remaining_item_blob maps to the pre-match source blob
```

Use real encoded item stacks and assert decoded amounts as well as database quantities.

- [ ] **Step 2: Run tests to verify failure**

```bash
./gradlew :paper:test --tests '*SettlementServiceIntegrationTest.*SellNow*' --tests '*PostgresRepositoryTest.*SellNow*'
```

Expected: new recovery tests fail before coordinator integration.

- [ ] **Step 3: Integrate recovery**

After recovery processes a terminal operation settlement, invoke the coordinator. Treat `COMPLETED`, `FAILED`, and `REVIEW` as terminal. Ensure deterministic remainder delivery and existing fill delivery uniqueness are used on every retry.

- [ ] **Step 4: Run focused and full Paper tests**

```bash
./gradlew :paper:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add paper/src/main/java/dev/mintychochip/tradingpost/settlement/SettlementRecoveryWorker.java paper/src/main/java/dev/mintychochip/tradingpost/settlement/SellNowSettlementCoordinator.java paper/src/test/java/dev/mintychochip/tradingpost/settlement/SettlementServiceIntegrationTest.java paper/src/test/java/dev/mintychochip/tradingpost/db/PostgresRepositoryTest.java
git commit -m "test: cover Sell Now recovery invariants"
```

---

### Task 6: Full verification and review

**Files:**
- Modify: no production files unless verification exposes a defect.

- [ ] **Step 1: Run all Paper tests**

```bash
./gradlew :paper:test
```

Expected: BUILD SUCCESSFUL with zero failed tests.

- [ ] **Step 2: Run the project build**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Inspect the final diff and status**

```bash
git status --short
git diff HEAD~5..HEAD --stat
git log --oneline -6
```

Confirm each commit contains one logical concern and no unrelated files.

- [ ] **Step 4: Commit only required follow-up fixes**

If verification exposes a defect, add a focused test first, implement only that fix, rerun the affected tests, and create a separate atomic commit with a specific message.
