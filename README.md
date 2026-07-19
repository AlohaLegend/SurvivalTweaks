# SurvivalTweaks

SurvivalTweaks is a small Paper plugin for long-running survival servers. It combines a few simple quality-of-life and safety features that are too small to justify separate plugin jars, while keeping each feature configurable and easy to disable.

The plugin is built for modern Paper servers and currently targets the Paper `1.21.11` API.

## Features

- `/biome` shows a player the biome they are standing in.
- `/platform <player>` reports whether an online player is marked as Bedrock or Java by a permission from your Bedrock/Floodgate stack.
- Selective keep-inventory rules can keep inventory for PVP, mob, and world deaths independently.
- Spawner proximity protection can block players from placing spawners too close together.
- Rare player-head drops can occur from eligible PVP deaths.
- Player-head rolls include playtime, same-IP, killer cooldown, victim cooldown, and killer-victim pair cooldown checks to discourage friend or alt farming.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/biome` | `survivaltweaks.biome` | Shows the current biome as a readable name and Minecraft key. |
| `/platform <player>` | None by default | Shows whether an online player has the configured Bedrock marker permission. |
| `/survivaltweaks help` | `survivaltweaks.admin` | Shows the in-game admin help page. |
| `/survivaltweaks status` | `survivaltweaks.admin` | Shows enabled modules, keep-inventory counters, and head-drop data totals. |
| `/survivaltweaks reload` | `survivaltweaks.admin` | Reloads `config.yml` and `player-head-rolls.yml` from disk. |
| `/stw <help|status|reload>` | `survivaltweaks.admin` | Alias for `/survivaltweaks`. |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `survivaltweaks.biome` | `true` | Allows players to use `/biome`. |
| `survivaltweaks.admin` | `op` | Allows `/survivaltweaks help`, `/survivaltweaks status`, and `/survivaltweaks reload`. |
| `survivaltweaks.spawner.bypass` | `op` | Bypasses spawner proximity placement protection. |
| `spawnerslimit.bypass` | External/legacy | Also bypasses spawner proximity protection for compatibility with older setups. |

## Configuration

The default `config.yml` is fully commented. The major sections are:

| Section | Purpose |
| --- | --- |
| `biome` | Enables or disables `/biome`. |
| `platform-check` | Configures `/platform <player>` and the permission used to mark Bedrock players. |
| `keep-inventory` | Selectively keeps inventory for PVP, mob, and world deaths. |
| `spawner-proximity` | Prevents spawners from being placed within a configurable cube radius. |
| `player-head-drops` | Controls rare PVP player-head drops and anti-farming checks. |
| `messages` | User-facing text for command responses and rare head drops. |

### Player Head Drops

Head drops are intentionally rare for long survival seasons. With the production default:

```yml
chance: 0.00005
```

the plugin rolls a `0.005%` chance after all eligibility checks pass, or roughly one successful drop per 20,000 eligible rolls. Eligibility checks happen before the random roll:

- Killer and victim must both meet the minimum playtime requirement.
- Killer and victim cannot be the same player.
- Same-IP kills can be blocked.
- The killer, victim, and killer-victim pair must each be outside their configured cooldown windows.

Roll data is stored in `plugins/SurvivalTweaks/player-head-rolls.yml`.

### Keep Inventory

Keep-inventory rules are separated by death type:

- `pvp`: direct player kills and player-shot projectiles.
- `mobs`: entity-caused deaths that are not caused by a player.
- `world`: fall damage, lava, void, drowning, suffocation, and other non-entity deaths.

When the plugin keeps inventory, it clears normal drops, clears dropped XP, keeps the player inventory, and keeps player levels.

### Spawner Proximity

Spawner proximity uses a cube radius, not a spherical radius. With the default radius of `4`, another spawner within `+/-4` blocks on X, Y, and Z will block placement.

Players with `survivaltweaks.spawner.bypass` or the legacy `spawnerslimit.bypass` permission bypass this protection.

## Installation

1. Build the plugin with Maven:

   ```bash
   mvn clean package
   ```

2. Copy `target/SurvivalTweaks-<version>.jar` into the server `plugins` folder.
3. Restart the server.
4. Review `plugins/SurvivalTweaks/config.yml`.
5. Use `/survivaltweaks status` to confirm the active module settings.

## Reloading

Most settings can be reloaded with:

```text
/survivaltweaks reload
```

The reload command reloads both `config.yml` and `player-head-rolls.yml`. A restart is still recommended after replacing the jar.

## Development

Run tests with:

```bash
mvn test
```

The policy classes are intentionally small and covered by unit tests for keep-inventory rules, spawner-radius behavior, and player-head anti-farming eligibility.
