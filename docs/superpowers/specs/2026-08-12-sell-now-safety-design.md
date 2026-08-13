# Sell Now Settlement Safety Design

**Date:** 2026-08-12  
**Status:** Proposed  
**Scope:** Prevent item duplication and stale aggregate restoration when Sell Now creates multiple fills whose Mint settlements complete independently.

## Problem

Sell Now reserves multiple fills in one Trading Post transaction, then submits separate Mint transfers. The current flow mails an unmatched remainder before all fills settle and compensates a failed fill by restoring shared sell-order state. If one fill settles before another fails, that compensation can overlap already-delivered item fragments. A failure can therefore duplicate or corrupt seller item state.

Mint transfers are separate transaction boundaries. Unless the Mint API gains a batch-transfer primitive, Trading Post MUST NOT claim that multiple transfers are Mint-atomic.

## Goals

- Keep every Sell Now item fragment durably attributable to one fill.
- Do not deliver or mailbox the unmatched remainder while the operation is only reserved.
- Prevent normal compensation from reconstructing a shared Sell Now stack after any fill has settled or delivered.
- Persist operation-level state and per-fill outcomes.
- Quarantine mixed or indeterminate outcomes for operator reconciliation instead of silently risking duplication.
- Preserve existing normal sell/buy behavior and settlement recovery.

## Non-goals

- Changing New World-style pricing, fees, taxes, or order matching.
- Adding a Mint batch API.
- Automatically reversing a Mint transfer that has already committed.
- Reworking ordinary one-fill sell-order compensation beyond the shared-operation guard.

## Design

### Durable Sell Now operation

Add a `sell_now_operations` table keyed by `operation_id`, with:

- `operation_id` UUID primary key;
- source `sell_order_id` UUID unique;
- seller, market, and original item blob/fingerprint;
- original quantity and reserved remainder quantity;
- operation state: `RESERVED`, `SETTLING`, `COMPLETED`, `FAILED`, `REVIEW`;
- created/updated timestamps;
- failure detail.

Add `operation_id` to `fills` and `settlements` for match settlements. All fills created by one Sell Now share the operation ID. A unique operation/fill relationship prevents accidental duplicate association.

### Reservation

Within the existing order transaction:

1. Insert the Sell Now operation with the immutable original item blob and quantity.
2. Reserve each fill and persist its immutable filled fragment and post-reservation remainder fragment.
3. Associate every match settlement with the operation.
4. Leave the source Sell Now order durable and non-deliverable until the operation completes.
5. Do not insert `SELL_NOW_REMAINDER` into the mailbox during reservation.

The source order must not be reconstructed from historical fill snapshots after any settlement outcome.

### Settlement execution

When a Sell Now settlement is submitted:

- Record the per-fill outcome through the existing settlement state.
- Mark the operation `SETTLING` once processing starts.
- Allow separate Mint transfers, but keep the operation incomplete until every fill reaches a terminal outcome.
- On all fills `DELIVERED`, finalize buyer mailbox deliveries, then create exactly one seller remainder mailbox item if quantity remains, and complete/cancel the source order in one Trading Post transaction.
- On any Mint rejection, stop submitting new fills for that operation and transition it to `FAILED` only when no money has committed; otherwise transition it to `REVIEW`.
- On `INDETERMINATE`, unresolved receipt, or any mixed committed/failed outcome, transition to `REVIEW` and never restore the aggregate source stack automatically.

### Compensation

Ordinary single-fill compensation remains valid when the fill is not part of a Sell Now operation. For operation fills:

- A failed fill may be voided only before any operation fill has committed/delivered.
- The entire operation may be restored only while all fills remain uncommitted and undelivered.
- Once any operation fill is committed or delivered, no operation fill may restore the shared source order blob automatically.
- Review records must include operation ID, source order ID, fill IDs, settlement IDs, per-fill states, quantities, and fingerprints.

### Delivery and idempotency

Buyer deliveries retain the existing unique settlement delivery key. Seller remainder delivery uses a unique operation-level key, for example `sell-now-remainder:<operation_id>`, so retries cannot duplicate the remainder. Finalization is idempotent and locks the operation and source order.

## Tests

Add tests before implementation for:

1. Sell Now with a partial unmatched remainder does not create a mailbox row while fills are only reserved.
2. All fills committed produces one remainder mailbox row and completes the source order.
3. A rejection before any fill commits restores the original source order exactly.
4. A first fill delivered followed by a later rejection transitions the operation to `REVIEW` and does not restore the aggregate source blob or create a duplicate remainder.
5. Recovery retries an incomplete operation without duplicate buyer or seller mailbox deliveries.
6. Persisted fill mapping keeps the pre-match source blob distinct from the post-match remainder blob.

Use real `ItemCodec` stacks for quantity/blob assertions. Existing normal match compensation tests must remain green.

## Acceptance criteria

- No Sell Now remainder is mailed before operation finalization.
- No mixed-success Sell Now path automatically restores shared aggregate item state.
- Every Sell Now fill and settlement is attributable to an operation.
- Repeated recovery/finalization is idempotent.
- Mixed or indeterminate outcomes are visible in `review_queue` with enough data for manual resolution.
- Existing focused and full Paper tests pass.
