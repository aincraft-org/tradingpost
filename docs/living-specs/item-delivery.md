# Item Delivery — Living Spec
> Status: active
> Last updated: 2026-08-26

## Intent
TradingPost decides *when* an item must leave the order book (purchase, cancel,
expiry, listing-fee reject, Sell Now remainder/fail). It does not store, display,
or claim those items. A required `ItemDeliveryHandler` registered at startup owns
delivery.

## Boundaries
### In scope
- Public `ItemDelivery` / `ItemDeliveryHandler` SPI on `tradingpost-api`
- Binding via Bukkit `ServicesManager` before `READY`
- Settlement, cancel, and expiry calling the handler
- Removing the Mailbox tab, claim flow, `MailboxService`, `MailboxRepository`, and `mailbox_items`

### Out of scope / non-goals
- Implementing a player mailbox in TradingPost
- Bukkit `Event` delivery (listeners can miss startup recovery)

## Invariants
- TradingPost does not go `READY` until an `ItemDeliveryHandler` is bound.
- Settlement is not `DELIVERED` until the handler succeeds for that delivery.
- Handler calls are idempotent on `deliveryId`. Recovery may retry.
- Match rejection restores the book; it never delivers `MATCH_REJECTED` items.
- Cancel/expiry take the order off the book first, then deliver remaining stacks.
  Canceled/expired sells with `quantity_remaining > 0` are retried until the
  handler succeeds, then remaining is cleared.

## Implementation guidance
- Inject `ItemDeliveryHandler` into `SettlementService`, `OrderService`, and
  `ExpiryWorker`. TradingPost has no mailbox table or repository.
- Call the handler *outside* a JDBC transaction; persist success afterwards.
- `deliveryId` is the settlement id for purchases and listing-fee rejects, the
  Sell Now operation id for remainder/fail, and the order id for cancel/expiry.
- Tests drive a recording handler.

## Current
- [x] `ItemDeliveryHandler` SPI and startup binding
- [x] Settlement purchase / listing-fee reject / Sell Now remainder-or-fail use the handler
- [x] Cancel and expiry use the handler and retry pending returns
- [x] Mailbox UI, claim, and `MailboxService` removed
- [x] Drop unused `mailbox_items` schema, repository, and SQL

## Next
- [ ] Optional in-process sample handler for local servers

## Future

## Decisions log
| Date | Decision | Why |
|---|---|---|
| 2026-08-26 | Required registered handler, not Bukkit events | Handler is known before workers start; events can miss recovery |
| 2026-08-26 | No internal mailbox claim/storage | TradingPost is not the mailbox; another plugin owns delivery |
| 2026-08-26 | Drop `mailbox_items` in schema v4 | Dead table after handler-owned delivery |

## Open questions
