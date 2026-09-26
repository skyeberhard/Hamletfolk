# Hamletfolk

A Paper server plugin that turns vanilla villages into communities with memory.

Villagers get persistent names, personalities and families. Each village keeps a ledger
of food and materials that its residents produce and consume every in-game day, and a
written history of what happened there: births, deaths, raids, famines, shortages, and
the players who helped. Residents talk about what's actually going on.

Everything is server-side. Players need no mods, and it works for Bedrock players
joining through Geyser, because all interaction uses vanilla tools: chat, books, and
the villager's own trade screen.

## Playing

| Action | What happens |
| --- | --- |
| Look at a villager | Their name appears (e.g. *Harold Carter*). |
| **Sneak + right-click** a villager | They introduce themselves and tell you what's on their mind. A normal right-click still opens trading. |
| `/settlement` | Summary of the village you're standing in: population, stores, treasury, danger, troubles. |
| `/settlement history` | Opens the village's history book. |
| `/settlement residents` | Who lives here and what they do. |
| `/settlement donate` | Gives the stack in your hand to the village. Food, wood, stone, metal, tools, wool/leather/paper, or emeralds (into the treasury). |

`/village` is an alias for `/settlement`.

### What the simulation does (v0.1)

- Once per in-game day, each working adult produces for the village: farmers, fishers and
  butchers make food, masons stone, fletchers wood, smiths tools. Output scales with the
  resident's work ethic.
- **Smiths need metal**, and no vanilla profession produces it. Without player donations
  the forges go cold, the village records a shortage, and the smith will tell you so.
- Everyone eats. When the stores run out, famine starts and goes into the history, and
  residents talk about it until it ends.
- Deaths from monsters, zombie infections and raids raise the village's sense of danger,
  which fades over days.
- Villagers bred in a village inherit a parent's family name and a blend of their traits.
- Settlements keep simulating while nobody is nearby. If the server is behind (e.g.
  after downtime), it catches up to 60 in-game days.

## Installing

1. Build (below) or download the jar from the latest GitHub Actions run.
2. Put `Hamletfolk-<version>.jar` in the server's `plugins/` folder and restart.
3. Optional: edit `plugins/Hamletfolk/config.yml`.

Data is stored in `plugins/Hamletfolk/settlements.json`. Removing the plugin leaves the
world untouched, except that villagers keep the name they were given (set
`show-names: false` before first run if you don't want that).

## Building

Requires Java 21.

```
./gradlew build
```

The plugin jar is written to `paper/build/libs/`. Set `minecraftVersion` in
`gradle.properties` to match your server's Minecraft version.

## Developing

- `./gradlew runServer` starts a local test server with the plugin. See
  [docs/TESTING.md](docs/TESTING.md) for setup and playtest scenarios.
- Work is tracked in [ROADMAP.md](ROADMAP.md). Commits and PRs start with the roadmap
  ID they deliver (e.g. `R1.2: ...`), and [CHANGELOG.md](CHANGELOG.md) lists IDs per release.

## Layout

- `core/` — the simulation: residents, settlements, ledger, history, dialogue. Plain
  Java with no Minecraft dependencies, fully unit tested (`./gradlew :core:test`).
- `paper/` — the thin Paper layer that maps villager entities and game events onto the core.

See [docs/DESIGN.md](docs/DESIGN.md) for the design.
