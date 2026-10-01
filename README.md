# CobbleBet

CobbleBet connects a Paper server economy to browser-based games and physical game boards inside Minecraft. Players open the game page with `/gamble`; their balance stays tied to the server they are playing on.

[Website](https://cobblebet.com/) · [Documentation](https://cobblebet.com/docs) · [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/cobblebet) · [Discord](https://discord.gg/r6Xfyedn4j)

## Features

- Mines, Blackjack, Roulette, Coinflip, Plinko, Dice, and Crash
- Browser games connected to the player's live server balance
- Physical Coinflip, Mines, Blackjack, Plinko, and Roulette games that can be placed in the world
- Vault economies and item-based economies
- In-game menus for players and server owners
- Per-game availability, limits, and extra house edge settings
- Website themes, custom backgrounds, maintenance mode, and server messages
- Optional CobbleBet account linking for players who want to use one profile across servers
- Big-win broadcasts, optional `/gamble` reminders, and configurable player animations
- Automatic plugin updates that can be enabled or disabled by the server owner

Players do not need a CobbleBet account to play through a Minecraft server. Account linking is optional.

## Requirements

- Paper 1.21.x
- Java 21
- An internet connection to `cobblebet.com`
- For Vault mode: Vault and a Vault-compatible economy plugin

The plugin can also use a Minecraft item as its currency, so Vault is not required for item economy servers.

## Installation

1. Download the latest JAR from [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/cobblebet).
2. Put it in the server's `plugins` folder.
3. Restart the server.
4. Run `/cobblebet` as an operator to open the owner menu.
5. Choose Vault or item economy and review the game, permission, broadcast, appearance, and update settings.
6. Players can now use `/gamble` to open the game page.

Configuration is stored in `plugins/CobbleBet/`. Settings changed through the owner menu or web panel are saved and restored after a restart.

## Commands

| Command | Description |
| --- | --- |
| `/gamble` | Sends the player their private game link. |
| `/wallet` | Opens the player's wallet menu. |
| `/wallet link` | Creates a short code for linking the current Minecraft identity to a CobbleBet account. |
| `/coinflip` or `/cf` | Opens the Coinflip lobby. |
| `/mines` | Opens the in-game Mines interface. |
| `/blackjack` or `/bj` | Opens the in-game Blackjack interface. |
| `/plinko` | Explains how to use a physical Plinko board. |
| `/roulette` | Opens the in-game Roulette betting board. |
| `/dice`, `/crash` | Directs the player to `/gamble`. |
| `/cobblebet` | Opens the server owner menu and shows the installed plugin version. |
| `/cobblebet stats` | Opens server and game statistics. |
| `/cobblebet panel` | Sends the owner a private link to the server panel. |
| `/cobblebet game` | Opens the physical game management menu. |
| `/cobblebet reload` | Reloads the plugin configuration. |

The physical game menu can create, move, rotate, rename, restyle, and remove world boards. Command arguments are also available for administrators who prefer them; tab completion shows the valid game IDs, styles, modes, and actions.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `cobblebet.gamble` | Operator | Allows access to player gambling commands when the permission requirement is enabled. |
| `cobblebet.admin` | Operator | Opens owner settings, manages physical games, and receives update notices. |
| `cobblebet.wallet.admin` | Operator | Allows administrative wallet balance changes. |

Owners can disable a command's permission requirement from the settings menu. A confirmation is shown before access is opened to all players.

## Economy modes

### Vault

CobbleBet reads and updates balances through Vault. A compatible economy provider must be installed and active.

### Item economy

CobbleBet uses a configured Minecraft material, such as diamonds. Deposits can take items from a player's inventory or Ender Chest, and withdrawals return the configured item.

## Physical games

Server owners can place Coinflip boards, Mines fields, Blackjack tables, Plinko boards, and Roulette tables in the world. Each model has placement and rotation controls, multiple styles, and persistent storage.

- Mines supports horizontal and vertical layouts, with GUI or world play modes.
- Blackjack supports GUI and world play modes.
- Plinko is played directly from its world board.
- Coinflip boards show open matches from the current server.
- Roulette uses a square table with color and number bets, an animated wheel, and server-authoritative payouts.

Use `/cobblebet game` to manage them without memorizing the full command syntax.

## Building from source

Clone the repository, install Java 21, and run:

```text
./gradlew shadowJar
```

On Windows:

```text
gradlew.bat shadowJar
```

The compiled plugin is written to `build/libs/`.

## Support

- Setup and configuration: [cobblebet.com/docs](https://cobblebet.com/docs)
- Questions and bug reports: [CobbleBet Discord](https://discord.gg/r6Xfyedn4j)
- Source code: [github.com/LiveAlexandre/cobblebetplugin](https://github.com/LiveAlexandre/cobblebetplugin/)
