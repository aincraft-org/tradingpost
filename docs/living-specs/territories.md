# Territories — Living Spec
> Status: active
> Last updated: 2026-08-26

## Intent
TradingPost does not import Guilds. Territory ownership is supplied by a
lifecycle-registered `TerritoryRegistry` so another plugin (or a test host)
can bind cuboids before READY.

## Boundaries
### In scope
- Public `Territory` / `TerritoryRegistry` on `tradingpost-api`
- In-memory registry in `tradingpost-common`
- Paper waits for a bound registry; `/post` prefers the territory covering the player
- `tradingpost-test` registers demo territories at STARTUP

### Out of scope / non-goals
- Importing `aincraft-org/guilds`
- Generating world geometry or claiming land

## Invariants
- READY requires a `TerritoryRegistry` on Bukkit `ServicesManager`.
- `findAt` returns a territory whose inclusive cuboid contains the block.
- Registering the same territory id replaces the previous cuboid.

## Implementation guidance
- Keep territory types Bukkit-free.
- Test host registers the registry (and a delivery handler) before TradingPost becomes READY.

## Current
- [x] Territory SPI and in-memory registry
- [x] Paper binds the registry at lifecycle and uses it for `/post` access
- [x] `tradingpost-test` registers spawn and riverside cuboids

## Next
## Future

## Decisions log
| Date | Decision | Why |
|---|---|---|
| 2026-08-26 | ServicesManager registry, not Guilds API | Same startup-bind pattern as item delivery |
