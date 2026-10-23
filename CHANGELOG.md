# Changelog

## [Current]

## [1.3.6] - 2026-10-23

- Spread bots with overlapping or missing saved positions around the saved location
- Give recovered bots distinct orientations

## [1.3.5] - 2026-10-23

- Keep active bot positions saved while the world is running
- Restore bots with missing position data beside the joining player

## [1.3.4] - 2026-10-23

- Delay bot restoration until the player has finished joining the world

## [1.3.3] - 2026-10-23

- Restore bots and their ongoing actions when rejoining a world

## [1.3.2] - 2026-10-23

- Updated mod to MC 26.3

## [1.3.1] - 2026-09-19

- Commited code changes I lowk forgot to commit in broken 1.3.0

## [1.3.0] - 2026-09-19

- Updated mod to MC 26.2

## [1.2.0] - 2026-09-06

- Updated mod to MC 26.1

## [1.1.0] - 2026-07-12
### CI
- Added automatic chanelog managment

### Refactor
- Changed mod from being dedicated server only also working on survival servers
- Added a GUI modmenu config replacing the `/bots skin all` command for singleplayer servers

## [1.0.1] - 2026-07-12
### Fixed
- Removed hardcoded local Java path from `gradle.properties` that broke CI builds
- Fixed release workflow tag glob pattern (`+` is a quantifier in GitHub Actions globs)
- `/bot <name>` commands work on the calling player themselves, not only bots

## [1.0.0] - 2026-07-12
### Added
- `/bot spawn [<name>]` - spawn a bot at your position
- `/bot <name>` actions: attack, use, jump, sneak, sprint, stop, look, drop, dropstack, swaphands, inventory, kill
- `attack`/`use` support `continuous` and `interval <ticks>` subcommands
- `/bots` management commands: list, stop, kill, permissionLevel, skin
- Bot inventory screen with armor slot filtering and glass pane placeholder labels
- Bot inventory closes automatically when opener moves more than 10 blocks away
- Real player protection - names of players who have joined are permanently locked from bot use
- Offline player data for protected names is backed up to `world/bot-backup/<name>.bak`
