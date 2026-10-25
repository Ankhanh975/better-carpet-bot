<img src="https://github.com/matejstastny/better-carpet-bots/blob/main/src/main/resources/icon.png?raw=true" alt="Modpack icon" width="25%" align="right">

# Better Carpet Bots

A [Carpet mod](https://github.com/gnembon/fabric-carpet) extension that adds better bot management commands to your Minecraft server.

### Requirements

- Minecraft 26.3
- [Fabric Loader](https://fabricmc.net/) ≥ 0.18.4
- [Carpet Mod](https://modrinth.com/mod/carpet)

## Commands

### `/bot spawn [<name> [<flags>]]`

Spawns a bot at your position in survival mode. If no name is given, one is auto-generated (`Bot1`, `Bot2`, …). Names must be 1–16 characters, letters/digits/underscores only.

The optional flags are one or two binary digits: the first controls continuous attacking, and the second controls moving forward. Each defaults to `0`. For example, `/bot spawn Alex 11` spawns Alex attacking continuously and moving forward; `/bot spawn Alex 1` enables attacking only.

Names that belong to real players who have ever joined the server cannot be used.

### `/bot <name> <action>`

Controls a bot (or yourself - targeting your own name works too).
If the named bot is offline, it is spawned at your position and the command runs once the bot is ready.

| Action | Description |
|---|---|
| `attack` | Attack continuously |
| `attack continuous` | Same as above |
| `attack interval <ticks>` | Attack every N ticks |
| `attack stop` | Stop attacking |
| `use` | Right-click / use continuously |
| `use continuous` | Same as above |
| `use interval <ticks>` | Use every N ticks |
| `use stop` | Stop using |
| `jump` / `jump stop` | Start / stop jumping |
| `forward` / `forward stop` | Walk forward / stop walking |
| `backward` / `backward stop` | Walk backward / stop walking |
| `sneak` / `sneak stop` | Start / stop sneaking |
| `sprint` / `sprint stop` | Start / stop sprinting |
| `stop` | Stop all actions |
| `look <north\|south\|east\|west\|up\|down>` | Look in a cardinal direction |
| `look at` | Look in the direction you are looking |
| `look <yaw> <pitch>` | Look at an exact angle |
| `drop` | Drop current hotbar item (single) |
| `dropstack` | Drop current hotbar item (full stack) |
| `swaphands` | Swap main hand and off hand |
| `inventory` | Open bot inventory (must be within 10 blocks) |
| `kill` | Remove the bot from the server |

### `/bots <action>`

Requires game-master permission (level 2).

| Action | Description |
|---|---|
| `list` | List all active bots |
| `stop` | Stop all bots |
| `kill` | Kill all bots |
| `permissionLevel <0–4>` | Set who can use `/bot` (0 = everyone, 4 = operators only) |
| `skin all <url>` | Apply a skin to every bot (persists across respawns) |
| `skin <name> <url>` | Apply a skin to one specific bot |

## Bot Inventory

`/bot <name> inventory` opens a 5-row screen showing the bot's full inventory:

- Rows 0–3: main inventory and hotbar
- Row 4: armor slots (only accept the correct armor type), off-hand, and locked filler slots
- Empty armor/off-hand slots show colored glass pane labels

The screen stays live - items the bot picks up appear immediately. The screen closes automatically if you move more than 10 blocks away.

## World Rejoining

Bots that are online when you leave a world are saved and restored the next time you join. Their saved state includes their position, dimension, orientation, and ongoing actions:

- Attack and use actions resume, including their configured intervals.
- Jumping, forward/backward movement, sneaking, and sprinting resume.
- Bot positions are saved periodically while the world is running and again when the server closes.

State is stored separately for each world in `<world save folder>/better-carpet-bots/bots.json`. For example, a Prism Launcher world named `My World` might store it at `minecraft/saves/My World/better-carpet-bots/bots.json`.

If a saved position is missing or shared by multiple bots, the bots are restored in distinct positions near the saved location (or near the joining player when no location was saved), with different orientations. Bots removed with `/bot <name> kill` or `/bots kill`, or bots that die, have their saved state deleted and are not restored.

## Real Player Protection

When a player authenticated via Mojang joins for the first time, their name is permanently locked. `/bot spawn <thatName>` will be rejected from that point on. Any existing offline-UUID bot data for that name is backed up to `world/bot-backup/<name>.bak` before being cleaned up, so nothing is lost.
