# Roadmap

## How to use this

- **Every item has a permanent ID** (`R<milestone>.<n>`, e.g. `R1.2`). IDs are never
  renumbered or reused. Dropped items stay in the table, marked *Dropped*.
- **Every change references an ID.** Commit messages and PR titles start with it
  (`R1.2: Cured villagers keep their identity`). Work that fits no item gets an item first.
- **"Done when" is the acceptance test.** An item is *Done* when that holds, and it is
  checked by a unit test or a scenario in [docs/TESTING.md](docs/TESTING.md) that names the ID.
- **Releases are milestones.** [CHANGELOG.md](CHANGELOG.md) lists the IDs each release shipped.

Status: **Done** · **In progress** · **Planned** · **Dropped**

---

## M0: Foundation (v0.1.0)

Goal: the simulation exists, and the plugin builds and loads.

| ID | Item | Done when | Status |
|---|---|---|---|
| R0.1 | Simulation model: residents, settlements, ledger | Identities are stable per villager UUID and survive save/load (unit tests) | Done |
| R0.2 | Daily simulation: work, eating, famine, shortages, milestones | Each condition is recorded once on entry and once on exit; results are deterministic (unit tests) | Done |
| R0.3 | Settlement history | Births, deaths, raids, famines, shortages and donations appear in `/settlement history` | Done |
| R0.4 | Dialogue driven by simulation state | An idle smith names the missing resource; a recent death is mentioned (unit tests) | Done |
| R0.5 | Paper layer: tracking, events, `/settlement` | Plugin compiles against the Paper API and registers everything on enable | Done |
| R0.6 | CI build | Every push runs `./gradlew build` and publishes the plugin jar | Done |
| R0.7 | Local test server | `./gradlew runServer` starts Paper with the plugin installed | Done |

## M1: Playtest-ready (v0.2.0)

Goal: safe to run on a copy of the real server.

| ID | Item | Done when | Status |
|---|---|---|---|
| R1.1 | First local playtest | Every scenario in docs/TESTING.md passes on `runServer`; bugs found are filed as items | Planned |
| R1.2 | Cured zombie villagers keep their identity | An infected-then-cured villager has the same name, family and traits, and history records the cure | Planned |
| R1.3 | Save backups | On startup, the previous `settlements.json` is copied to `backups/`, keeping the last 5 | Planned |
| R1.4 | Admin commands | `/settlement admin` supports `inspect`, `rename`, `save`; gated by `mcsocieties.admin` | Planned |
| R1.5 | Abandoned settlements | A settlement with no residents for 10 days is marked abandoned, stops simulating, and keeps its history | Planned |
| R1.6 | Performance budget | A benchmark shows 50 settlements × 50 residents simulate one day in under 5 ms | Planned |
| R1.7 | Match the server's version | `minecraftVersion` matches the server; the plugin runs on a copy of the server world for one session with no errors | Planned |
| R1.8 | Membership follows residents | A villager that settles in another settlement's area for 3 days moves to that settlement, recorded in both histories | Planned |

## M2: Buildings (v0.3.0)

Goal: what players build shapes what the village can do.

| ID | Item | Done when | Status |
|---|---|---|---|
| R2.1 | Sign registration | A `[Farm]`, `[Smithy]`, `[Mine]`, `[House]` or `[Guard Post]` sign inside a settlement registers a building; breaking it removes it; `/settlement buildings` lists them | Planned |
| R2.2 | Housing capacity | Beds within the settlement set a housing capacity shown in `/settlement` | Planned |
| R2.3 | Buildings affect output | A registered mine lets an unemployed resident become a miner who produces stone and metal, so smiths can work without donations | Planned |
| R2.4 | Buildings inferred from blocks | Placing a workstation, bed and roof is recognized without a sign; analysis runs only when blocks in the area change | Planned |

## M3: Economy (v0.4.0)

Goal: trading with villagers is part of the village economy.

| ID | Item | Done when | Status |
|---|---|---|---|
| R3.1 | Prices follow supply | Villager trade prices rise when the ledger is short of that resource and fall when it has a surplus | Planned |
| R3.2 | Trades feed the ledger | Selling food to a farmer adds it to the village's stores | Planned |
| R3.3 | Village requests | When a resource runs short, the village posts a request (e.g. "32 iron"); fulfilling it pays emeralds from the treasury | Planned |
| R3.4 | Player reputation | Each settlement tracks reputation per player from donations, requests and harm; dialogue and prices reflect it | Planned |
| R3.5 | Resident wealth and wages | Residents earn from work and spend on food; wealth shows in dialogue | Planned |

## M4: Growth and migration (v0.5.0)

Goal: villages grow or shrink because of their circumstances.

| ID | Item | Done when | Status |
|---|---|---|---|
| R4.1 | Newcomers | With a food surplus and free beds, a new villager arrives and is recorded in history | Planned |
| R4.2 | Migration | Unemployed or unhappy residents leave for a better-off settlement nearby, recorded in both histories | Planned |
| R4.3 | Jobs follow need | Unemployed residents take the occupation the village is shortest of, if a workstation is free | Planned |

## M5: Defense (v0.6.0)

Goal: danger creates demand, and the village responds.

| ID | Item | Done when | Status |
|---|---|---|---|
| R5.1 | Guards | Sustained high threat turns a resident into a guard who consumes tools and food | Planned |
| R5.2 | Defenses reduce threat | Recognized walls, lighting and towers lower how much threat each attack adds | Planned |
| R5.3 | Calls for help | When threat is high, residents ask nearby players for help, and defending the village raises reputation | Planned |

## M6: Society (v0.7.0+)

| ID | Item | Done when | Status |
|---|---|---|---|
| R6.1 | Households and marriage | Residents form households; children live with parents; history records marriages | Planned |
| R6.2 | Businesses | A building can be owned by a resident or player, employ residents and pay wages | Planned |
| R6.3 | Player investment | Players can fund a business or building and receive a share of its output | Planned |
| R6.4 | Village leadership | Each settlement has an elder chosen from its residents, who sets one policy (e.g. tax rate) | Planned |
| R6.5 | Trade between settlements | Settlements exchange surplus for shortage along recorded trade routes | Planned |

## M7: Optional AI dialogue

| ID | Item | Done when | Status |
|---|---|---|---|
| R7.1 | Pluggable dialogue provider | Dialogue goes through an interface; the template provider remains the default | Planned |
| R7.2 | Local model provider | An optional provider generates lines from simulation state using a local model; off by default, falls back to templates | Planned |

## Unscheduled ideas

Not committed to. Promote an idea to a milestone (with a new ID) before working on it.

- Cultures per region: naming, architecture and values vary by biome.
- Villagers physically working (walking to the farm, carrying goods) when players are near.
- Crime, disputes over property.
- Disasters: drought, fire, disease.
