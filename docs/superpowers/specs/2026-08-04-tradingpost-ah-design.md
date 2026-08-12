# TradingPost Auction House Design

**Date:** 2026-08-04  
**Status:** Approved for implementation  
**Reference:** New World (Amazon Games) Trading Post

## 1. Goal

Build a Paper 1.21.x plugin that provides a New World-style Trading Post: named, location-bound markets; sell and buy orders; price-time matching; upfront buy-order funding; listing fees; sales tax; expiry; offline settlement; and a durable per-market mailbox.

The plugin is named **TradingPost**, uses Java 21, and lives in the `ah/` project directory. It integrates with the sibling `../mint` economy through Mint's public API.

## 2. Platform and dependencies

- Paper 1.21.x, Java 21, Gradle Kotlin DSL.
- Mint's Paper plugin is the required dependency. Its descriptor name is exactly `Mint` and its API is exposed through `PaperMintAccess`.
- The AH descriptor must declare a required Paper dependency on `Mint` with `join-classpath: true`.
- The build follows Mint's convention: `includeBuild("../mint")`, Java 21 toolchain, and `compileOnly(project(":mint-api"))` / `compileOnly(project(":mint-paper"))` through dependency substitution.
- PostgreSQL is the AH persistence target. AH owns a separate `tradingpost` schema and never writes Mint tables directly.

At startup, TradingPost obtains `PaperMintAccess` from Bukkit's `ServicesManager`, then gates money operations on `access.mint().state() == MintState.READY`. `STARTING` is retried with bounded backoff; `DEGRADED` is read-only; `SHUTTING_DOWN` and `STOPPED` reject new mutations. No generic readiness assumption is used.

## 3. Mint integration and account contract

TradingPost uses a stable configured `ClientId` in its own namespace, defaulting to `tradingpost:plugin`. It calls `client.accounts().ensure(...)` for system accounts that it owns:

- `tradingpost:escrow`;
- `tradingpost:fees`;
- `tradingpost:tax`.

The account namespace and IDs are configurable, but system accounts must be owned by TradingPost's client. Mint's `AccountService.ensure` is caller-owned: it may return an existing account for the same owner and rejects ownership conflicts. TradingPost never attempts to take ownership of a foreign account.

Player accounts use Mint's canonical `AccountId.player(UUID)`. TradingPost checks `client.accounts().exists(playerAccount)` before submitting a mutation. A missing or foreign-owned player account is a closed business failure with an operator-visible message; the plugin does not assume it can provision accounts owned by another Mint client. Server operators may provision player accounts through their chosen account/economy integration.

Mint's public `Posting(AccountId, Money)` and `LedgerService.transact(TransactionRequest)` support the multi-leg ordinary-transfer shape required here. Ordinary transfers are conserved ledger operations; issue/burn authorization is separate and is not used by TradingPost. Every transfer uses a configured currency and a deterministic idempotency key.

The AH and Mint are separate transaction boundaries even when they share a PostgreSQL server. TradingPost never describes order mutation plus Mint settlement as one atomic transaction. It uses the durable settlement state machine in Section 6.

## 4. Market model

A market is a named order book associated with one or more physical trading-post blocks. Orders are scoped to a market, not globally.

- Admins create markets and register post blocks.
- A player must right-click a registered post, or use `/ah` within the configured radius, to trade there.
- Posts in the same named market share one order book.
- Markets may override global listing-fee and sales-tax rates.
- Post coordinates are unique by `(world, x, y, z)` and are protected from ordinary block breaking.

This maps New World's settlement-local Trading Posts to Minecraft without requiring a separate town-management plugin.

## 5. Orders and matching

### 5.1 Sell orders

A sell order stores one exact `ItemStack` blob, including NBT, enchantments, durability, custom data, and quantity. The item is removed from the seller's inventory before the order becomes durable. The order holds the serialized item until it is sold, canceled, or expires.

Normal sell orders do not cross the book on placement. A seller may use **Sell Now** to create a durable transient sell order with its item blob persisted before matching. The transient order is fee-waived, immediately consumes compatible buy orders, and is then marked `FILLED` or returns any unmatched remainder through the normal mailbox path. Because it is a real sell-order row, every fill has a durable source and recovery can reconstruct the item after a crash.

### 5.2 Buy orders

A buy order specifies:

- material;
- quantity;
- maximum unit price;
- optional exact-match template fingerprint;
- duration and market.

A buy order's complete maximum cost is funded before it becomes open. Funds move to `tradingpost:escrow` through Mint. A buy order without a template matches any sell order of its material; a templated buy order requires an identical item fingerprint.

Buy-order placement immediately crosses compatible sell orders at or below the buyer's price. Any unfilled quantity remains open.

### 5.3 Price-time priority

- Resting buy orders: unit price descending, then creation time ascending.
- Resting sell orders: unit price ascending, then creation time ascending.
- A fill executes at the resting order's price.
- Fills are partial: both `qty_remaining` values are decremented, and orders remain open until fully filled, canceled, or expired.
- A new match locks both orders with `SELECT ... FOR UPDATE`, computes the fill, decrements both sides, inserts a fill row, and inserts its settlement intent in one AH transaction.

Sell Now consumes compatible buy orders from highest price down. The transient sell order is persisted before any match or Mint call. If a stack exceeds available demand, each buyer receives a split stack and the remainder stays on the transient order until it is returned to the seller's market mailbox. It is never represented by a fill with a missing sell-order reference.

Buy Now is the normal buy-order flow with immediate crossing against the lowest asks.

## 6. Fees, tax, and money

All money uses one configured Mint currency and its exact `BigDecimal` scale. Currency existence and scale are validated through Mint at startup. No `double` or `float` is used for prices or rates.

### 6.1 Listing fee

A normal sell order pays a non-refundable listing fee calculated from `(unit price × quantity)` and duration. Rates are integer basis points; duration multipliers are configurable. The fee is charged only after the sell-order intent and item blob are durably recorded.

Sell Now does not pay a listing fee, but its transient sell order still follows the durable item and settlement state machine.

### 6.2 Sales tax

Each completed fill calculates tax from the execution proceeds. One Mint transaction credits the seller with net proceeds and the tax sink with the tax amount while debiting `tradingpost:escrow`.

For a fill of `q` units at price `p` and tax rate `t` basis points:

- gross = `p × q`;
- tax = `gross × t / 10_000`, canonicalized to currency scale;
- seller net = `gross - tax`.

Rounding is deterministic and leaves the Mint transaction exactly balanced.

### 6.3 Buy-order escrow and refunds

At placement, the buyer transfers `unit_price × quantity` to `tradingpost:escrow`. Each fill debits the actual resting execution price from escrow. Any price surplus and all unfilled quantity remain refundable. Cancellation and expiry create a refund intent that transfers the remaining obligation back to the buyer, including while offline.

## 7. Durable settlement state machine

AH PostgreSQL and Mint transactions are separate boundaries. The plugin never describes them as one atomic transaction and never writes Mint tables directly.

Every money movement has a durable AH intent committed before calling Mint. Settlement states are:

```text
RESERVED -> MONEY_SETTLED -> DELIVERED
    \\                         /
     ------> FAILED <---------
```

`DELIVERED` means all AH-side item/mailbox effects are committed. `FAILED` is terminal only after the operation-specific compensation is committed.

Intent kinds and deterministic keys:

| Kind | Key | Mint legs |
|---|---|---|
| `BUY_ESCROW` | `ah:escrow:<orderId>` | buyer -> TradingPost escrow |
| `LISTING_FEE` | `ah:fee:<orderId>` | seller -> fee sink |
| `MATCH_SETTLEMENT` | `ah:match:<fillId>` | escrow -> seller net + tax sink |
| `REFUND` | `ah:refund:<orderId>` | escrow -> buyer |
| `FEE_REFUND` | `ah:fee-refund:<orderId>` | fee sink -> seller |

`settlements.idempotency_key` is unique. `fills.fill_id` is the primary key, and at most one settlement references a fill. Delivery rows and refund rows use unique settlement/order keys, preventing duplicate item delivery or refund creation.

### 7.1 Match sequence

1. AH transaction locks both orders, validates remaining quantities, decrements both sides, inserts the fill, and inserts a `MATCH_SETTLEMENT` row in `RESERVED` state. The transaction commits.
2. The settlement worker calls Mint `ledger().transact()` with the deterministic key.
3. A committed receipt advances the settlement to `MONEY_SETTLED`; an AH transaction inserts the buyer's mailbox item and marks the fill/settlement `DELIVERED`.
4. A business rejection restores both order remainders in one AH transaction, marks the fill `VOIDED`, and marks the settlement `FAILED`.

The book cannot be decremented without a matching intent, and an intent cannot move money twice.

### 7.2 Recovery and reconciliation

A bounded worker lease-locks stale non-terminal settlements using `lease_owner`, `lease_until`, and `FOR UPDATE SKIP LOCKED`.

- Committed receipt: advance and finish delivery.
- No receipt: retry `transact()` with the same key; Mint idempotency makes this safe.
- `INDETERMINATE`: resolve through receipt lookup; never guess whether money moved.
- Business rejection: apply the operation-specific compensation.

Compensation rules:

- Failed `BUY_ESCROW`: cancel the creating buy order; no refund is needed because no escrow entered Mint.
- Failed `LISTING_FEE`: cancel the creating sell order and return its durable blob to mailbox.
- Failed `MATCH_SETTLEMENT`: restore both decremented order quantities and void the fill.
- Failed `REFUND` or `FEE_REFUND`: leave an operator-visible review item; never silently discard a player's money.
- Fee receipt committed while sell order is still `CREATING`: advance the order to `ACTIVE`. A fee-paid-but-not-active order is never automatically treated as unpaid.

A periodic reconciler compares Mint's `tradingpost:escrow` balance against AH's open buy-order escrow obligations plus outstanding escrow-touching intents. Divergence creates an operator review item and an error log.

## 8. Inventory handoff and exact placement states

Paper inventory and AH PostgreSQL cannot share a transaction. The placement flow states this explicitly:

- `REMOVED_UNPERSISTED`: main-thread inventory removal completed, but no AH row is durable. This state exists only in memory. The window lasts from executor queue latency through the JDBC round trip; it is typically milliseconds and can be longer under contention. A process crash here can lose the item. The plugin does not claim this risk is atomic or microsecond-scale.
- `CREATING`: sell-order row and item blob are durable. The fee intent is then inserted and processed.
- `ACTIVE`: fee settled and order visible.

While the process remains alive, failed JDBC attempts are retried on the AH executor; if all bounded retries fail, the captured stack is returned to the main thread and restored. After a durable `CREATING` row exists, recovery owns the blob.

Fee charging never occurs before the durable `CREATING` row. If Mint commits the fee and the process crashes before `ACTIVE`, receipt lookup completes the order. If the operator force-cancels a fee-paid creating order, `FEE_REFUND` is required before the item is released.

Returns to a player's mailbox are checked against the stored item fingerprint when the player is online. Ambiguous offline/reload cases are held in `review_queue` for `/ahadmin review`; no automatic return path claims duplication is impossible.

## 9. Expiry and mailbox

A periodic expiry worker transitions expired active orders:

- sell order: `EXPIRED`, item blob inserted into the seller's market mailbox;
- buy order: `EXPIRED`, remaining escrow refunded through `REFUND`.

Canceled sell items follow the same mailbox path. Listing fees are not refunded by ordinary cancel/expiry. Buyers claim mailbox items at a post. Full inventories leave the item unclaimed. Claim operations use a claim-in-progress state and inventory fingerprint reconciliation on reconnect so a crash cannot silently duplicate a claimed item.

## 10. User interface

The plugin uses Paper inventory GUI screens with Adventure components:

1. **Browse & Buy:** searchable, paginated sell orders; icons show item, unit price, quantity, seller, and time remaining.
2. **Sell:** inventory stacks, price and duration controls, fee preview, confirmation, and Sell Now using the best bid.
3. **Buy Orders:** material/template picker, quantity, maximum price, duration, and immediate-cross preview.
4. **My Orders:** active orders, remaining quantity, time remaining, cancel confirmation, and fill history.
5. **Mailbox:** per-market returned items, one-item claim, and claim-all when inventory space allows.

Price entry uses preset increments and a numeric chat prompt. Database and Mint calls are asynchronous; inventory rendering and inventory mutations return to the Paper main thread.

## 11. Persistence schema

The AH owns schema `tradingpost` and applies versioned migrations.

```text
markets(name PK, display_name, fee_bps, tax_bps, created_at)
trading_posts(id PK, market_name FK, world, x, y, z, UNIQUE(world,x,y,z))
sell_orders(id UUID PK, market_name FK, seller UUID, item_blob BYTEA,
            fingerprint, qty, qty_remaining, unit_price NUMERIC,
            mode, status, expires_at, created_at)
buy_orders(id UUID PK, market_name FK, buyer UUID, material,
           template_fingerprint NULL, qty, qty_remaining, unit_price NUMERIC,
           escrow_reserved NUMERIC, status, expires_at, created_at)
fills(fill_id UUID PK, market_name FK, sell_order_id UUID NOT NULL FK,
      buy_order_id UUID NOT NULL FK, qty, unit_price NUMERIC, status, created_at)
settlements(id UUID PK, kind, idempotency_key UNIQUE, fill_id NULL FK,
            order_id NULL, state, attempts, last_error, lease_owner,
            lease_until, created_at, updated_at)
mailbox_items(id UUID PK, market_name FK, owner UUID, item_blob BYTEA,
              fingerprint, reason, state, settlement_id NULL UNIQUE,
              created_at, claimed_at)
review_queue(id UUID PK, player UUID, fingerprint, detail JSONB,
             resolved_by NULL, resolved_at NULL, created_at)
```

Indexes support `(market_name, status, unit_price, created_at)` on both order tables, `(owner, state, created_at)` on mailbox, and stale settlement leases. Rates are integers; monetary columns use PostgreSQL `NUMERIC` with exact currency-scale validation in Java.

## 12. Commands, permissions, and configuration

Commands:

- `/ah` — open the current market when near a registered post;
- `/ahadmin post set <market>` / `post remove`;
- `/ahadmin market create <name>` / `setfee` / `settax`;
- `/ahadmin reload`;
- `/ahadmin review` and `/ahadmin review resolve <id>`;
- `/ahadmin cancel <orderId>` for controlled recovery.

Permissions:

- `tradingpost.use`;
- `tradingpost.admin`;
- optional `tradingpost.bypass` for remote/admin access.

Configuration contains the Mint currency ID, client ID, system account IDs, global fee/tax rates, duration menu, duration multipliers, price bounds, per-player order limits, maximum buy quantity, post radius, expiry/recovery/reconcile intervals, and item blacklist.

Suggested defaults are 1% listing fee, 5% sales tax, 1h/6h/12h/24h/72h durations, 30 active orders per side, 576 maximum units per buy order, four-block post radius, 60-second expiry sweep, 15-second settlement recovery, and ten-minute escrow reconciliation. All are configurable and not hard-coded behavior.

## 13. Threading and lifecycle

- JDBC and Mint operations run on a bounded AH executor; no Paper thread blocks on either.
- GUI events validate state on the main thread, submit work, and render results through scheduled main-thread continuations.
- Startup validates configuration, connects to PostgreSQL, applies migrations, obtains `PaperMintAccess`, waits for `MintState.READY`, ensures TradingPost-owned system accounts, verifies the configured currency and player-account contract, loads markets/posts, starts recovery/expiry/reconciliation workers, and only then enters its ready state.
- Shutdown stops new mutations, unregisters integrations, drains recovery work with a bounded grace period, and closes the executor and pool.
- Multiple Paper nodes may share the schema. Lease locking and Postgres row locks prevent duplicate recovery work; all authoritative market state remains in PostgreSQL.

## 14. Verification strategy

- Unit tests cover price-time priority, exact-template matching, partial fills, price-surplus refunds, basis-point fee/tax math, and every settlement transition including receipt lookup and compensation.
- A crash-after-removal regression test must prove that a Sell Now transient order is durable before matching/Mint settlement and that recovery can deliver or mailbox-return its stored item blob; no fill may exist with a missing sell-order source.
- PostgreSQL Testcontainers tests cover migrations, row locks, unique idempotency/delivery/refund keys, expiry, recovery, and reconciliation.
- Paper smoke testing uses a test server with Mint: register a post, place sell/buy orders, complete a partial match, verify Mint balances, claim mailbox items, cancel/expire orders, and restart with stale settlement rows to verify recovery.

- Auction bidding mode; New World's Trading Post is an order book, not an auction.
- Cross-market remote orders or automatic cross-market transfer.
- Folia support.
- Vault as the primary economy path; Mint is required.
- Automatic town/faction/territory integration; tax and fee rates are per-market configuration.
