# Trading Post Command Rename Design

**Date:** 2026-08-08  
**Status:** Approved for implementation

## Goal

Rename the player command from `/ah` to `/post` and the administrator command from `/ahadmin` to `/postadmin`, matching the villager Trading Post feature.

## Design

This is a direct cutover with no compatibility aliases. `TradingPostCommands.register()` binds the executor to `post` and `postadmin`; `onCommand` explicitly dispatches those two command names and returns `false` for any other command. Existing player/admin permissions, arguments, market behavior, and villager-post workflow remain unchanged.

`paper-plugin.yml`, command usage messages, and command-facing documentation use only the new names. Focused tests verify the descriptor/registration surface and that unknown commands are not treated as administrator commands.
