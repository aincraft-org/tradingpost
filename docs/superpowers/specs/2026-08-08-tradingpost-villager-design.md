# Villager Trading Post Design

**Date:** 2026-08-08  
**Status:** Approved for implementation  
**Reference:** New World settlement-local Trading Post

## Goal

Make each Trading Post a persistent vanilla Villager NPC. Each NPC represents one local market/order book, so listings and buy orders at one guild/settlement do not match against another NPC's market.

## Scope and boundaries

- TradingPost remains self-contained; no Guilds/Towny or Citizens dependency is added.
- A guild/settlement is represented by the villager NPC and its configured market name.
- Existing order, matching, settlement, mailbox, and UI code remains market-scoped and is reused unchanged outside the post-registration/access boundary.
- Registered block posts are replaced by villager posts for new registrations. The schema migration keeps the existing coordinate columns for stored location/radius access and adds the villager UUID identity.

## Registration and persistence

`/postadmin post set <market>` targets the villager the admin is looking at. Registration validates that the market exists and is not already assigned to another post, then stores the villager UUID, world, block coordinates, and market. `/postadmin post remove` targets the registered villager and deletes its post mapping. The database enforces one post per market and one post per villager UUID.

The registry loads posts asynchronously, indexes them by villager UUID, and retains their saved location for nearest-post lookup. On registration and reload it disables villager AI and makes the villager invulnerable so the trading post remains stationary and cannot be destroyed through ordinary gameplay.

## Player interaction

- Right-clicking a registered villager while the plugin is ready and the player has `tradingpost.use` opens that villager's market.
- `/post` with no market argument opens the nearest registered villager within the configured radius.
- `/post <market>` retains the existing local-access check; admin permission remains the explicit remote-access bypass.
- Registered villagers are protected from damage. Administrators use `/postadmin post remove` rather than killing the NPC.
- Block-place and block-break protection is removed because blocks are no longer Trading Post identities.

## Data flow

1. Admin looks at a vanilla Villager and assigns an existing unique market.
2. The villager UUID and location are persisted before the in-memory registry is updated.
3. Player interaction resolves the villager UUID to a `MarketContext`.
4. Existing UI/order services receive only the resolved market name; all order and settlement rows remain scoped by that market.
5. A second villager cannot reuse the market because the database unique constraint rejects the registration.

## Compatibility and errors

The migration is additive and leaves existing order rows intact. Legacy block mappings without a villager UUID are not registered for entity interaction and remain inert; operators must resolve any legacy mappings before reusing their market names for villager posts. Registration failures are reported on the main thread with the existing command callback pattern.

## Verification

Focused tests cover one-market-per-post uniqueness at the schema/repository boundary, villager UUID lookup, nearest-radius access, and rejection of a second villager using an existing market. The existing test suite and jar build must remain green.
