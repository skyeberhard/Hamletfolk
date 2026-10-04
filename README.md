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
| `/settlement donate [amount\|all]` | With no amount, tells you what the stack in your hand is worth. With an amount (or `all`), gives that many to the village. Items count by value: an iron block is worth nine ingots, and a better tool is worth more. Food, wood, stone, metal, tools, wool/leather/paper, or emeralds (into the treasury). |

`/village` is an alias for `/settlement`.

### What the simulation does today

- Once per in-game day, each working adult produces for the village: farmers, fishers and
  butchers make food, masons stone, fletchers wood, smiths tools. Output scales with the
  resident's work ethic.
- **Unemployed adults forage.** A resident with no job gathers about 1 food a day from the
  edges of the village, so a small village short of farmers does not starve outright while it
  waits for work. It is deliberate, not a loophole: it is why a village in famine keeps its
  foragers foraging instead of sending them elsewhere, and unemployed residents say so when
  you talk to them. It is far less than a farmer makes (about 4 a day).
- **Smiths need metal**, and no vanilla profession produces it. Place a sign reading `[Mine]` inside
  the village and unemployed residents become miners who dig stone and metal; without a mine or player
  donations the forges go cold, the village records a shortage, and the smith will tell you so.
- **Merchants need a shop.** Place a sign reading `[Shop]` inside the village; each shop gives one
  merchant a place to sell the village's surplus for emeralds. Without one nobody sells.
- Everyone eats. When the stores run out, famine starts and goes into the history, and
  residents talk about it until it ends.
- Deaths from monsters, zombie infections and raids raise the village's sense of danger,
  which fades over days.
- A villager infected by zombies and later cured comes back as the same person.
- Villagers bred in a village inherit a parent's family name and a blend of their traits.
- Settlements keep simulating while nobody is nearby. If the server is behind (e.g.
  after downtime), it catches up to 60 in-game days.

## Where it's going

**The goal:** a society that runs itself. Villages grow, specialize, trade and defend
themselves because of what they have and what they lack (food, housing, work, safety),
not because a player assigned jobs or clicked "upgrade". You can leave a village for a
hundred days and come back to find it has grown, built, struggled and remembered.

### v1.0: a village that lives on its own

v1.0 is milestones M1 to M5. When they're done, a village's size, jobs, prices and
defenses all follow from its food, housing and danger:

- **Playtest-ready (M1).** Stable and safe to run on a real server.
- **Buildings (M2).** What players build shapes what the village can do. Beds set how many
  people can live there; farms, mines and smithies change what it produces.
- **Economy (M3).** Villager prices follow supply. The village posts requests for what it's
  short of and pays for them, and players earn a reputation.
- **Growth (M4).** Newcomers arrive when there's surplus food and free beds. People leave
  when there's no work, and the jobless take up the work the village needs most.
- **Defense (M5).** Sustained danger creates guards, who need food and tools. Walls,
  lighting and towers make attacks less damaging.

### After v1.0: from hamlets to a society

- **Households, businesses and leadership (M6).** Families, resident-owned businesses that
  pay wages, player investment, and an elder who sets village policy.
- **Trade and roads.** Settlements trade surplus for shortage. Paths wear in between homes,
  work and neighboring villages, and become roads as traffic grows.
- **Hamlets become towns become cities.** With districts, wealthier and poorer
  neighborhoods, and people migrating from small villages to the cities that have work.
- **Optional AI dialogue (M7).** Only on top of the simulation, and never required.

The detailed plan, with acceptance criteria for every item, is in [ROADMAP.md](ROADMAP.md).

## Installing

1. Build (below) or download the jar from the latest GitHub Actions run.
2. Put `Hamletfolk-<version>.jar` in the server's `plugins/` folder and restart.
3. Optional: edit `plugins/Hamletfolk/config.yml`.

Data is stored in `plugins/Hamletfolk/settlements.json`. Removing the plugin leaves the
world untouched, except that villagers keep the name they were given (set
`show-names: false` before first run if you don't want that).

**Back it up.** The plugin keeps its own rolling backups in `plugins/Hamletfolk/backups/`
(the last 5, made on every startup), but that's on the same disk as everything else — it
won't survive a lost drive or a bad `rm -rf`. Include `plugins/Hamletfolk/` (both
`settlements.json` and `backups/`) in whatever off-machine backup already covers the rest
of the server's world data.

## Building

Requires Java 25 (Minecraft 26.x requires it).

```
./gradlew build
```

The plugin jar is written to `paper/build/libs/`. Set `minecraftVersion` in
`gradle.properties` to match your server's Minecraft version.

## Developing

- `./gradlew runServer` starts a local test server with the plugin. See
  [docs/TESTING.md](docs/TESTING.md) for setup and playtest scenarios.
- Deploying to a real server follows [docs/DEPLOYING.md](docs/DEPLOYING.md); [PRODUCTION.md](PRODUCTION.md)
  tracks what's live.
- Work is tracked in [ROADMAP.md](ROADMAP.md). Commits and PRs start with the roadmap
  ID they deliver (e.g. `R1.2: ...`), and [CHANGELOG.md](CHANGELOG.md) lists IDs per release.

## Layout

- `core/` — the simulation: residents, settlements, ledger, history, dialogue. Plain
  Java with no Minecraft dependencies, fully unit tested (`./gradlew :core:test`).
- `paper/` — the thin Paper layer that maps villager entities and game events onto the core.

See [docs/DESIGN.md](docs/DESIGN.md) for the design.
