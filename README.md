# TradingPost

[![CI](https://img.shields.io/github/actions/workflow/status/aincraft-org/tradingpost/ci.yml?branch=main&logo=github)](https://github.com/aincraft-org/tradingpost/actions/workflows/ci.yml)
[![Version](https://img.shields.io/badge/dynamic/yaml?url=https%3A%2F%2Fraw.githubusercontent.com%2Faincraft-org%2Ftradingpost%2Fmain%2Ftradingpost-paper%2Fsrc%2Fmain%2Fresources%2Fpaper-plugin.yml&query=%24.version&label=version&color=blue&logo=github)](https://github.com/aincraft-org/tradingpost/releases)
[![Packages](https://img.shields.io/badge/Packages-GitHub%20Packages-blue?logo=github)](https://github.com/aincraft-org/tradingpost/packages)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Paper](https://img.shields.io/badge/Paper-1.21-blue)](https://papermc.io/)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-lightgrey)](#license)

TradingPost is a [Paper](https://papermc.io/) 1.21+ plugin that adds a New World-style player market to Minecraft.

Admins create named markets and register post blocks. Players place sell orders, fund buy orders, and trade through a durable order book with listing fees, sales tax, expiry, and offline mailbox settlement.

## Features

- New World-style order book with price-time priority
- Sell orders and buy orders with exact template matching
- Listing fees and sales tax configured in basis points
- Upfront buy-order escrow with refunds on cancel/expiry
- Durable settlement state machine and crash recovery
- Per-market mailbox for returns and claims
- PostgreSQL, MySQL, MariaDB, and SQLite support
- Paper inventory GUIs with [Adventure](https://github.com/KyoriPowered/Adventure) components
- [Mint](https://github.com/aincraft-org/mint) economy integration

## Modules

| Module | Description | Artifact |
|--------|-------------|----------|
| `tradingpost-api` | Public integration contracts | `dev.mintychochip:tradingpost-api` |
| `tradingpost-common` | Bukkit-free engine | `dev.mintychochip:tradingpost-common` |
| `tradingpost-paper` | Paper plugin | `dev.mintychochip:tradingpost-paper` |

## Installation

1. Install the [Mint](https://github.com/aincraft-org/mint) Paper plugin.
2. Drop the `tradingpost-paper-*.jar` from the [latest release](https://github.com/aincraft-org/tradingpost/releases) into your server's `plugins/` folder.
3. Configure `plugins/TradingPost/config.yml` and restart.

## Building

```bash
./gradlew test assemble
```

The runnable Paper jar is written to `tradingpost-paper/build/libs/`.

To publish to the local Maven repository:

```bash
./gradlew publishAllPublicationsToLocalBuildRepository
```

To publish to GitHub Packages, set `GITHUB_ACTOR` and `GITHUB_TOKEN`, or pass `-Pgpr.user` and `-Pgpr.key`:

```bash
./gradlew publishAllPublicationsToGitHubPackagesRepository
```

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/post [market]` | Open the nearby villager Trading Post | `tradingpost.use` |
| `/postadmin <market\|post> ...` | Manage Trading Post markets and posts | `tradingpost.admin` |

## Permissions

- `tradingpost.use` — open a Trading Post (default: true)
- `tradingpost.admin` — manage markets and posts (default: op)

## License

All Rights Reserved.
