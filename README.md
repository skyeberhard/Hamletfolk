# Hamletfolk

A Paper server plugin that turns vanilla villages into communities with memory.

Villagers get persistent names, personalities and families. Each village keeps a ledger
of food and materials that its residents produce and consume every in-game day, and a
written history of what happened there: births, deaths, raids, famines, shortages, and
the players who helped. Residents talk about what's actually going on, and the village
plans what it needs next.

Everything is server-side. Players need no mods, and it works for Bedrock players
joining through Geyser, because all interaction uses vanilla tools: chat, books, signs,
and the villager's own trade screen.

> **Status.** The simulation is built and unit tested (about 360 tests). The Paper layer that
> connects it to the game has been playtested in part (the plugin loads, saves migrate, signs,
> donations, requests, trades, newcomers, beds and reputation work); a good share of the newest
> features are tested only in code so far. [docs/TESTING.md](docs/TESTING.md) lists every scenario and
> [PRODUCTION.md](PRODUCTION.md) what is live. Back up before trying it on a world you care about.

## What it can do today

### Villagers and villages

- **Persistent people.** Every villager has a name, gender, personality traits, an age and life
  stage (child, adult, elder), parents, and a job the simulation keeps track of. Children of
  residents inherit a family name and a blend of their parents' traits; cured zombie villagers
  come back as the same person.
- **A written history.** Births, arrivals, deaths, raids, famines, shortages, donations, buildings
  and milestones, kept per village, readable as a book.
- **Talk that means something.** Sneak + right-click a villager and they say what is actually going
  on: hunger, missing metal, a recent death, a request, their own wealth, and what the village has been
  deciding. Their greeting changes with how well they know you and how the village regards you.
- **Villages that keep going.** Settlements simulate while nobody is nearby, catch up after server
  downtime (up to 60 days), and can be abandoned and resettled.

### The economy

- **A ledger per village:** food, wood, stone, metal, tools and goods, produced and used every day.
  Farmers, fishers and butchers make food, masons stone, lumberjacks wood, miners stone and metal,
  smiths tools (from metal). Unemployed adults forage a little, so a small village does not starve at
  once. Tools wear out, and a village with a mine but no tools works slower.
- **Famine and shortages** are recorded and talked about. Storage has limits, and food spoils.
- **Jobs follow need.** The simulation hands unemployed residents the job the village is shortest of,
  food first, when there is somewhere to work. Children become apprentices. A villager's own vanilla job
  only fills in a resident who has none.
- **Prices follow supply.** A short resource costs more at a villager's trade screen and a plentiful one
  less. Selling to a villager adds to the village's stores; buying takes from them, and a village will not
  sell food it cannot spare.
- **Requests.** When a village is short it posts a request and pays emeralds for it from its treasury.
- **Reputation.** Each village keeps a standing for each player, from gifts, filled requests and harm.
- **Wealth.** Working residents earn from what they make and pay for their meals; it shows in how they talk.
- **Merchants** sell a village's surplus for emeralds, from a shop.

### Buildings by sign

Place a sign reading one of these inside a village and the village notices:

| Sign | What it does |
| --- | --- |
| `[Farm]` | Four places for farmers. |
| `[Mine]` | Four places for miners, who dig stone and metal (smiths need metal). |
| `[Smithy]` | Places for smiths when tools are short. |
| `[Shop]` | One place for a merchant. Without a shop nobody sells. |
| `[Treasury]` | Raises how many emeralds the village can bank (by 500 each, above a base of 200), and is where donations are made once one exists. |
| `[House]`, `[Guard Post]` | Registered, for the planner and later systems. |
| `[Exempt]` (or `[Ignore]`) | Works anywhere, in a village or not. Leaves the villagers within its radius alone: the number on the second line, 24 blocks if none, 4 to 64. For trading halls and shop rigs: they are never enrolled, named, re-priced or moved. It does not take a village's own residents out of the village unless an admin places it. Break the sign to end it. |

Beds set how many people a village can house. Nothing checks that a sign is on a real building yet.

### Growth, people and danger

- **Newcomers** arrive when there is surplus food and a free bed.
- **Migration.** Unemployed or unhappy adults leave for a better-off village nearby (at most one a day
  from a village), and a villager who stays three days in another village's area becomes a resident there.
- **Attacks make guards.** A village remembers the attacks on it (a resident killed by a monster, a zombie
  turning, a raid). Two in a week and it grows wary and calls up a guard; more, or high danger, and it
  calls up more, all at once under siege. Guards need tools in the stores, not food, and calm the village.
  **They do not fight yet.**

### Planning

- **The village plans.** Each day it works out what it needs next (food, shelter, safety, then trade and
  growth), follows a shortage back to its root (tools need a smithy, which needs metal, which needs a
  mine), and writes the decision down with its reason.
- **A layout for the village:** a main square, a main street, branches and reserved lots for houses,
  farms, a shop, a mine and more, avoiding steep and wet ground, growing a second stage at 25 residents.
- **Site scoring** for where a village should go, from the biomes around a spot.
- **Terrain grading** (as a plan only, nothing is placed yet): how to level a lot, fill under it down to
  solid ground and blend its edges.

## Playing

| Action | What happens |
| --- | --- |
| Look at a villager | Their name appears (e.g. *Harold Carter*). |
| **Sneak + right-click** a villager | They introduce themselves and tell you what's on their mind. A normal right-click still opens trading. |
| `/settlement` | Summary of the village you're standing in: population, housing, stores, treasury, danger and alert, requests, how it regards you. |
| `/settlement history` | Opens the village's history book. |
| `/settlement residents` | Who lives here, what they do and how well off they are. |
| `/settlement buildings` | The buildings registered by signs. |
| `/settlement beds` | The beds in the village, which are free and which a villager has claimed. |
| `/settlement plan` | What the village wants next and why, its layout, and its recent decisions. |
| `/settlement lots [on\|off]` | Outlines the village's planned lots, square and streets with particles, until you turn it off. |
| `/settlement survey` | Scores the ground where you stand as a village site: water, timber, farmland, grazing, stone, ore. Works anywhere. |
| `/settlement donate [amount\|all]` | With no amount, says what the stack in your hand is worth. With an amount (or `all`), gives that many to the village. Items count by value: an iron block is worth nine ingots, and a better tool is worth more. Food, wood, stone, metal, tools, wool, leather, paper, or emeralds (into the treasury). Once the village has a `[Treasury]` sign you must stand within 12 blocks of it. |
| `/settlement admin ...` | List, inspect, rename and save settlements (permission `hamletfolk.admin`). |

`/village` is an alias for `/settlement`.

### Settings that matter on a real server

Everything is in `plugins/Hamletfolk/config.yml`. Most systems have their own switch, so you can turn
off the ones you do not want:

- `worlds.allow` / `worlds.deny`: which worlds it looks at.
- `prices.follow-supply`, `economy.trades-need-stock`: villager trade prices and refusing sales the village
  cannot cover. Turn these off if players run shops with villagers and want vanilla trades.
- `economy.migration`, `membership.follows-residents`: villagers moving between villages.
- `economy.tool-penalty`, `economy.treasury-base`, `aging.*`, `appearance.*`, `show-names`, `backups.*`.

To leave particular villagers alone (a trading hall, a shop rig), put an `[Exempt]` sign near them, or look at one
and run `/settlement admin ignore`.

## Where it's going

**The goal:** a society that runs itself. Villages grow, specialize, trade and defend themselves because of
what they have and what they lack (food, housing, work, safety), not because a player assigned jobs or
clicked "upgrade". You can leave a village for a hundred days and come back to find it has grown, built,
struggled and remembered.

### Where each part stands

| Milestone | What it covers | Done |
| --- | --- | --- |
| M0 Foundation | The simulation, the Paper layer, CI, a local test server | 7 of 7 |
| M1 Playtest-ready | Safety and stability on a real server | 25 of 30 |
| M2 Buildings | Signs, beds, jobs and shops, treasury, bank counter | 6 of 7 |
| M3 Economy | Prices, requests, reputation, wealth, merchants, storage, trades | 14 of 14 |
| M4 Growth and migration | Newcomers, migration, jobs, apprentices, templates and construction | 10 of 18 |
| M5 Defense | Guards and their response; walls, lights and calls for help | 2 of 6 |
| M6 Society | Households, businesses, leadership, trade between villages | 0 of 6 |
| M7 Optional AI dialogue | A pluggable, off-by-default dialogue provider | 0 of 2 |
| M8 Plan-owned villages | Planning, roads, lots, grading, founding, placing buildings | 4 of 9 |
| M9 Smarter villagers | An isolated, switchable module for villager behaviour | 0 of 5 |

### Up next

1. **Smarter villagers (M9).** The game's villager brain cannot be changed through the public API, so
   guards cannot fight and villagers cannot visibly work. M9 adds an isolated module built against
   Paper 26.2 with a startup self-check, a live on/off switch for the whole module and for each kind of
   behaviour, and troubleshooting tools, then guards that fight, workers who walk to their jobs, builders,
   stewards and elders. Design: [docs/SMART_VILLAGERS.md](docs/SMART_VILLAGERS.md).
2. **Building (M4, M8).** A catalogue of building templates (the game's own village pieces plus generated
   ones for what it does not ship), a builder who places the planned buildings over time, and players
   founding villages. Design: [docs/VILLAGE_PLANNING.md](docs/VILLAGE_PLANNING.md) and
   [docs/BUILDING_LIBRARY.md](docs/BUILDING_LIBRARY.md).
3. **Defense and society.** Walls and lighting that reduce damage, calls for help, villages that prepare
   before danger peaks, then households, businesses, leadership and trade between villages.

The detailed plan, with acceptance criteria for every item, is in [ROADMAP.md](ROADMAP.md), and the order
work is picked up in [docs/WORKPLAN.md](docs/WORKPLAN.md).

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
`gradle.properties` to match your server's Minecraft version (currently 26.2).

## Developing

- `./gradlew runServer` starts a local test server with the plugin. See
  [docs/TESTING.md](docs/TESTING.md) for setup and playtest scenarios.
- Deploying to a real server follows [docs/DEPLOYING.md](docs/DEPLOYING.md); [PRODUCTION.md](PRODUCTION.md)
  tracks what's live.
- Work is tracked in [ROADMAP.md](ROADMAP.md). Commits and PRs start with the roadmap
  ID they deliver (e.g. `R1.2: ...`), and [CHANGELOG.md](CHANGELOG.md) lists IDs per release.

## Layout

- `core/` — the simulation: residents, settlements, ledger, history, dialogue, the planner, village
  plans and site scoring. Plain Java with no Minecraft dependencies, fully unit tested
  (`./gradlew :core:test`).
- `paper/` — the thin Paper layer that maps villager entities and game events onto the core, and
  holds the commands.

See [docs/DESIGN.md](docs/DESIGN.md) for the design.
