# Roadmap

## How to use this

- **Every item has a permanent ID** (`R<milestone>.<n>`, e.g. `R1.2`). IDs are never
  renumbered or reused. Dropped items stay in the table, marked *Dropped*.
- **Every item has a GitHub issue**, listed in the Issue column, titled with its ID.
  Each milestone has a parent issue (linked in its heading), and its items are that
  issue's sub-issues, so GitHub shows progress per milestone.
- **Every change references an ID.** Commit messages and PR titles start with it
  (`R1.2: Cured villagers keep their identity`), and the PR body says `Closes #<issue>`
  so the issue closes when it merges. Work that fits no item gets an item (and an issue) first.
- **This file is the source of truth.** When an item's scope or status changes, update the
  table here and the issue in the same PR. New items get the next free ID in their milestone.
- **"Done when" is the acceptance test.** An item is *Done* when that holds, and it is
  checked by a unit test or a scenario in [docs/TESTING.md](docs/TESTING.md) that names the ID.
- **Releases are milestones.** [CHANGELOG.md](CHANGELOG.md) lists the IDs each release shipped.
- **v1.0 is M1 to M5**: a village whose size, jobs, prices and defenses follow from its food,
  housing and danger. M6 onward is the path from hamlets to a full society.

Status: **Done** · **In progress** · **Planned** · **Dropped**

---

## M0: Foundation (v0.1.0) · [#1](https://github.com/skyeberhard/Hamletfolk/issues/1)

Goal: the simulation exists, and the plugin builds and loads.

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R0.1 | [#9](https://github.com/skyeberhard/Hamletfolk/issues/9) | Simulation model: residents, settlements, ledger | Identities are stable per villager UUID and survive save/load (unit tests) | Done |
| R0.2 | [#10](https://github.com/skyeberhard/Hamletfolk/issues/10) | Daily simulation: work, eating, famine, shortages, milestones | Each condition is recorded once on entry and once on exit; results are deterministic (unit tests) | Done |
| R0.3 | [#11](https://github.com/skyeberhard/Hamletfolk/issues/11) | Settlement history | Births, deaths, raids, famines, shortages and donations appear in `/settlement history` | Done |
| R0.4 | [#12](https://github.com/skyeberhard/Hamletfolk/issues/12) | Dialogue driven by simulation state | An idle smith names the missing resource; a recent death is mentioned (unit tests) | Done |
| R0.5 | [#13](https://github.com/skyeberhard/Hamletfolk/issues/13) | Paper layer: tracking, events, `/settlement` | Plugin compiles against the Paper API and registers everything on enable | Done |
| R0.6 | [#14](https://github.com/skyeberhard/Hamletfolk/issues/14) | CI build | Every push runs `./gradlew build` and publishes the plugin jar | Done |
| R0.7 | [#15](https://github.com/skyeberhard/Hamletfolk/issues/15) | Local test server | `./gradlew runServer` starts Paper with the plugin installed | Done |

## M1: Playtest-ready (v0.2.0) · [#2](https://github.com/skyeberhard/Hamletfolk/issues/2)

Goal: safe to run on a copy of the real server.

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R1.1 | [#16](https://github.com/skyeberhard/Hamletfolk/issues/16) | First local playtest | Every scenario in docs/TESTING.md passes on `runServer`; bugs found are filed as items | Planned |
| R1.2 | [#17](https://github.com/skyeberhard/Hamletfolk/issues/17) | Cured zombie villagers keep their identity | An infected-then-cured villager has the same name, family and traits, and history records the cure | Done |
| R1.3 | [#18](https://github.com/skyeberhard/Hamletfolk/issues/18) | Save backups | On startup, the previous `settlements.json` is copied to `backups/`, keeping the last 5 | Done |
| R1.4 | [#19](https://github.com/skyeberhard/Hamletfolk/issues/19) | Admin commands | `/settlement admin` supports `inspect`, `rename`, `save`; gated by `hamletfolk.admin` | Done |
| R1.5 | [#20](https://github.com/skyeberhard/Hamletfolk/issues/20) | Abandoned settlements | A settlement with no residents for 10 days is marked abandoned, stops simulating, and keeps its history | Done |
| R1.6 | [#21](https://github.com/skyeberhard/Hamletfolk/issues/21) | Performance budget | A benchmark shows 50 settlements × 50 residents simulate one day in under 5 ms | Done |
| R1.7 | [#22](https://github.com/skyeberhard/Hamletfolk/issues/22) | Match the server's version | `minecraftVersion` matches the server; the plugin runs on a copy of the server world for one session with no errors | In progress |
| R1.8 | [#23](https://github.com/skyeberhard/Hamletfolk/issues/23) | Membership follows residents | A villager that settles in another settlement's area for 3 days moves to that settlement, recorded in both histories | Planned |
| R1.9 | [#46](https://github.com/skyeberhard/Hamletfolk/issues/46) | Rename project to Hamletfolk | Plugin, jar, data folder, permissions, packages and docs use the new name, and CI builds | Done |
| R1.10 | [#47](https://github.com/skyeberhard/Hamletfolk/issues/47) | World allow/deny list | Settlements are only tracked and simulated in worlds matching the config's allow-list (or not matching its deny-list); an out-of-scope world (e.g. a void-generated or pasted-structure dimension) is ignored with no errors | Done |
| R1.11 | [#48](https://github.com/skyeberhard/Hamletfolk/issues/48) | Bedrock (Geyser) playtest | T2-T10 pass for a Bedrock/Geyser player; any Java-only interaction (e.g. sneak + right-click) has a documented alternate path (command or plain interaction) that also works | Planned |
| R1.12 | [#49](https://github.com/skyeberhard/Hamletfolk/issues/49) | Paper-layer performance measurement | An admin command (e.g. `/settlement perf`) reports Paper-layer tick cost with real villagers loaded, distinct from the core-only benchmark in R1.6 | Planned |
| R1.13 | [#50](https://github.com/skyeberhard/Hamletfolk/issues/50) | Save schema version and migration | `settlements.json` carries a schema version; loading an older version migrates without data loss (unit test), and the save path is confirmed to be covered by off-machine backup | Done |
| R1.14 | [#51](https://github.com/skyeberhard/Hamletfolk/issues/51) | Correct plugin.yml api-version | `api-version` in plugin.yml matches what Paper 26.x expects, confirmed against Paper's docs, not just "compiles and loads" | Done |
| R1.15 | [#52](https://github.com/skyeberhard/Hamletfolk/issues/52) | Admin and abandonment land before M2 | R1.4 and R1.5 are merged before any M2 work begins | Planned |
| R1.16 | [#53](https://github.com/skyeberhard/Hamletfolk/issues/53) | Pin-and-verify workflow | A documented process records which commit is live in production versus which was last verified, and is checked before each deploy | Done |
| R1.17 | [#59](https://github.com/skyeberhard/Hamletfolk/issues/59) | Stale autosave can't overwrite a newer save | Each save snapshot carries a sequence number; a write older than the last one written is skipped, so an in-flight autosave can't overwrite the shutdown save | Done |
| R1.18 | [#60](https://github.com/skyeberhard/Hamletfolk/issues/60) | Save soon after a donation | A donation schedules a debounced save within a few seconds instead of waiting up to 5 minutes for the next autosave | Done |
| R1.19 | [#61](https://github.com/skyeberhard/Hamletfolk/issues/61) | Periodic backups while running | Besides the startup backup, a backup is taken on a configurable interval (default daily) using the same rotation | Done |
| R1.20 | [#62](https://github.com/skyeberhard/Hamletfolk/issues/62) | Fair allocation of scarce inputs | When an input runs short, which workers go without rotates day to day, so no resident is permanently idle while others always work | Done |
| R1.21 | [#63](https://github.com/skyeberhard/Hamletfolk/issues/63) | Bounded history | Repeated minor events are merged and stored entries are capped, keeping major events (founding, deaths, raids, famines) over minor ones | Done |
| R1.22 | [#64](https://github.com/skyeberhard/Hamletfolk/issues/64) | Record skipped catch-up days | When catch-up skips days beyond `max-catch-up-days`, history records how many were skipped | Done |
| R1.23 | [#65](https://github.com/skyeberhard/Hamletfolk/issues/65) | Handle world time moving backwards | If the world's day is earlier than a settlement's last simulated day, new residents and history use the settlement's day, and nothing is recorded out of order | Done |
| R1.24 | [#66](https://github.com/skyeberhard/Hamletfolk/issues/66) | Decide on unemployed foraging | UNEMPLOYED producing 1 food/day is either documented as intended or removed | Planned |
| R1.25 | [#74](https://github.com/skyeberhard/Hamletfolk/issues/74) | Shared Claude Code permissions for unattended runs | `.claude/settings.json` is checked in with an allowlist for Gradle, `git add`/`git commit` and `git push` to the working branch only, so an unattended local run doesn't stall at permission prompts; personal overrides go in the git-ignored `.claude/settings.local.json` | Done |
| R1.26 | [#75](https://github.com/skyeberhard/Hamletfolk/issues/75) | Playtest scenario T6 accounts for famine | T6 in docs/TESTING.md no longer promises the "no metal" dialogue while the village is starving, and a new scenario T6b checks that line once food has been donated. | Done |
| R1.27 | [#76](https://github.com/skyeberhard/Hamletfolk/issues/76) | Settlements anchored to a village's real area | A villager tracked inside a vanilla-generated village belongs to one settlement anchored to that village's whole area, however far it sits from the first villager seen, so a village spread over uneven terrain is a single settlement; outside any generated village the radius rule still applies; settlements whose areas overlap or lie within a few blocks are merged (residents, ledger and history combined, recorded in history) | Planned |
| R1.28 | [#80](https://github.com/skyeberhard/Hamletfolk/issues/80) | Reviewer subagent for risky changes | `.claude/agents/reviewer.md` defines a read-only reviewer subagent that runs on Opus and checks a diff against this repo's known traps (core vs paper placement, single-day output tests that flake, save-format migrations, occupation overwrites, determinism, roadmap bookkeeping, UTF-8), reporting each finding as verified or unverified; `CLAUDE.md` says when to run it, so a session on a cheaper model can have its risky changes reviewed by Opus without switching models by hand | Done |

## M2: Buildings (v0.3.0) · [#3](https://github.com/skyeberhard/Hamletfolk/issues/3)

Goal: what players build shapes what the village can do.

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R2.1 | [#24](https://github.com/skyeberhard/Hamletfolk/issues/24) | Sign registration | A `[Farm]`, `[Smithy]`, `[Mine]`, `[House]` or `[Guard Post]` sign inside a settlement registers a building; breaking it removes it; `/settlement buildings` lists them | Planned |
| R2.2 | [#25](https://github.com/skyeberhard/Hamletfolk/issues/25) | Housing capacity | Beds within the settlement set a housing capacity shown in `/settlement` | Planned |
| R2.3 | [#26](https://github.com/skyeberhard/Hamletfolk/issues/26) | Buildings affect output | A registered mine lets an unemployed resident become a miner who produces stone and metal, so smiths can work without donations | Planned |
| R2.4 | [#27](https://github.com/skyeberhard/Hamletfolk/issues/27) | Buildings inferred from blocks | Placing a workstation, bed and roof is recognized without a sign; analysis runs only when blocks in the area change | Planned |

## M3: Economy (v0.4.0) · [#4](https://github.com/skyeberhard/Hamletfolk/issues/4)

Goal: trading with villagers is part of the village economy.

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R3.1 | [#28](https://github.com/skyeberhard/Hamletfolk/issues/28) | Prices follow supply | Villager trade prices rise when the ledger is short of that resource and fall when it has a surplus | Planned |
| R3.2 | [#29](https://github.com/skyeberhard/Hamletfolk/issues/29) | Trades feed the ledger | Selling food to a farmer adds it to the village's stores | Planned |
| R3.3 | [#30](https://github.com/skyeberhard/Hamletfolk/issues/30) | Village requests | When a resource runs short, the village posts a request (e.g. "32 iron"); fulfilling it pays emeralds from the treasury | Planned |
| R3.4 | [#31](https://github.com/skyeberhard/Hamletfolk/issues/31) | Player reputation | Each settlement tracks reputation per player from donations, requests and harm; dialogue and prices reflect it | Planned |
| R3.5 | [#32](https://github.com/skyeberhard/Hamletfolk/issues/32) | Resident wealth and wages | Residents earn from work and spend on food; wealth shows in dialogue | Planned |
| R3.6 | [#67](https://github.com/skyeberhard/Hamletfolk/issues/67) | Tool wear | Gathering occupations occasionally consume TOOLS and produce less while the village has none; a tool shortage is recorded like any other | In progress |
| R3.7 | [#68](https://github.com/skyeberhard/Hamletfolk/issues/68) | Resource flow tracking | Each settlement keeps a rolling 7-day produced/consumed total per resource, shown in `/settlement` | Done |
| R3.8 | [#69](https://github.com/skyeberhard/Hamletfolk/issues/69) | Donations valued by what they're worth | Storage blocks count as their contents and tools by material tier; donating confirms (or takes a quantity) instead of silently taking the whole stack | Done |
| R3.9 | [#70](https://github.com/skyeberhard/Hamletfolk/issues/70) | Merchant occupation | A MERCHANT sells surplus goods, stone and excess stock for emeralds into the treasury | Planned |
| R3.10 | [#71](https://github.com/skyeberhard/Hamletfolk/issues/71) | Storage capacity and food spoilage | Each resource has a storage limit (a base amount, raised later by storage buildings from M2), production beyond it is wasted, and food slowly spoils. A surplus can end, and the "granaries full" milestone can clear and recur. | Done |
| R3.11 | [#81](https://github.com/skyeberhard/Hamletfolk/issues/81) | Donations respect storage limits | `/settlement donate` tells a player how much room the settlement has for what they are holding, and credits only what fits, so a donation to a full store is never accepted and then silently wasted. | Planned |

## M4: Growth and migration (v0.5.0) · [#5](https://github.com/skyeberhard/Hamletfolk/issues/5)

Goal: villages grow or shrink because of their circumstances.

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R4.1 | [#33](https://github.com/skyeberhard/Hamletfolk/issues/33) | Newcomers | With a food surplus and free beds, a new villager arrives and is recorded in history | Planned |
| R4.2 | [#34](https://github.com/skyeberhard/Hamletfolk/issues/34) | Migration | Unemployed or unhappy residents leave for a better-off settlement nearby, recorded in both histories | Planned |
| R4.3 | [#35](https://github.com/skyeberhard/Hamletfolk/issues/35) | Jobs follow need | Unemployed residents take the occupation the village is shortest of, if a workstation is free. The simulation owns a resident's occupation: the villager's vanilla profession only seeds it: it fills in an occupation while the resident is still `UNEMPLOYED` (a newborn, or someone who claims a workstation later) and never replaces one. Both places that currently overwrite it stop doing so: `SettlementService.track()` (it unconditionally calls `resident.setOccupation(occupationOf(villager))` on every tracking pass) and the career-change handler in `VillagerListener`. Otherwise an assigned `LUMBERJACK`/`MINER` is stomped back to the villager's vanilla profession (often `UNEMPLOYED`) the moment the entity re-enters a loaded chunk or claims a nearby workstation | Done |
| R4.4 | [#54](https://github.com/skyeberhard/Hamletfolk/issues/54) | Lumberjack occupation | A LUMBERJACK occupation produces WOOD with no vanilla profession backing it and no building required, replacing FLETCHER as the de facto wood source | Done |
| R4.5 | [#55](https://github.com/skyeberhard/Hamletfolk/issues/55) | Fletcher consumes wood, not produces it | FLETCHER consumes WOOD and produces GOODS (arrows), matching its actual trade, once R4.4 gives wood a real source | Done |
| R4.6 | [#56](https://github.com/skyeberhard/Hamletfolk/issues/56) | Building templates with tiers | A building type has ordered tiers, each a hand-authored NBT structure; given a building's tier and the settlement's materials, the system picks the best affordable tier and computes the block diff to reach it | Planned |
| R4.7 | [#57](https://github.com/skyeberhard/Hamletfolk/issues/57) | Terrain-triggered construction and upgrade projects | A settlement lacking a needed building type queues a project on suitable terrain (R4.6); existing buildings are checked for affordable upgrades; player-built buildings are never auto-replaced | Planned |
| R4.8 | [#58](https://github.com/skyeberhard/Hamletfolk/issues/58) | Builder occupation and work queue | A BUILDER occupation claims one queued project at a time, consumes materials from the ledger as it executes the block diff over real time, and the remaining work survives a restart | Planned |
| R4.9 | [#72](https://github.com/skyeberhard/Hamletfolk/issues/72) | Unmet needs reduce output | A resident's food, safety and purpose affect how much they produce: a starving or terrified worker produces noticeably less. Combined with R4.2 (migration), low needs have real consequences. | Done |
| R4.10 | [#73](https://github.com/skyeberhard/Hamletfolk/issues/73) | Children become apprentices in needed trades | When a child grows up, they take the occupation the village is shortest of (using R4.3's logic), preferring a parent's trade when it's also needed, and history records the apprenticeship. | Done |
| R4.11 | [#77](https://github.com/skyeberhard/Hamletfolk/issues/77) | Personality drives visible behavior | A resident's sociability, bravery, work ethic and ambition change what players can see them do, not just what they say, using vanilla mechanisms that work with villager AI (e.g. steering via job-site, home and meeting-point memories); at least two traits produce a noticeable, observable difference between residents | Planned |
| R4.12 | [#78](https://github.com/skyeberhard/Hamletfolk/issues/78) | Overheard lines and mood display | Villagers near a player occasionally produce short ambient reactions driven by simulation state: an overheard line shown to nearby players, particles or a head-shake around a struggling worker, and a mood color or marker in the display name; rate-limited and switchable in config | Planned |
| R4.13 | [#79](https://github.com/skyeberhard/Hamletfolk/issues/79) | Pair sim-only occupations with a vanilla workstation | Each occupation that has no vanilla profession (`LUMBERJACK`, future `MINER`) is paired with an existing vanilla workstation block, chosen and documented per occupation (for example the stonecutter, whose saw blade suits a lumberjack), so a resident holding it visibly works at a job block during work hours; the pairing is cosmetic and doesn't affect the simulation's occupation | Planned |

## M5: Defense (v0.6.0) · [#6](https://github.com/skyeberhard/Hamletfolk/issues/6)

Goal: danger creates demand, and the village responds.

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R5.1 | [#36](https://github.com/skyeberhard/Hamletfolk/issues/36) | Guards | Sustained high threat turns a resident into a guard who consumes tools and food | Planned |
| R5.2 | [#37](https://github.com/skyeberhard/Hamletfolk/issues/37) | Defenses reduce threat | Recognized walls, lighting and towers lower how much threat each attack adds | Planned |
| R5.3 | [#38](https://github.com/skyeberhard/Hamletfolk/issues/38) | Calls for help | When threat is high, residents ask nearby players for help, and defending the village raises reputation | Planned |

## M6: Society (v0.7.0+) · [#7](https://github.com/skyeberhard/Hamletfolk/issues/7)

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R6.1 | [#39](https://github.com/skyeberhard/Hamletfolk/issues/39) | Households and marriage | Residents form households; children live with parents; history records marriages | Planned |
| R6.2 | [#40](https://github.com/skyeberhard/Hamletfolk/issues/40) | Businesses | A building can be owned by a resident or player, employ residents and pay wages | Planned |
| R6.3 | [#41](https://github.com/skyeberhard/Hamletfolk/issues/41) | Player investment | Players can fund a business or building and receive a share of its output | Planned |
| R6.4 | [#42](https://github.com/skyeberhard/Hamletfolk/issues/42) | Village leadership | Each settlement has an elder chosen from its residents, who sets one policy (e.g. tax rate) | Planned |
| R6.5 | [#43](https://github.com/skyeberhard/Hamletfolk/issues/43) | Trade between settlements | Settlements exchange surplus for shortage along recorded trade routes | Planned |

## M7: Optional AI dialogue · [#8](https://github.com/skyeberhard/Hamletfolk/issues/8)

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R7.1 | [#44](https://github.com/skyeberhard/Hamletfolk/issues/44) | Pluggable dialogue provider | Dialogue goes through an interface; the template provider remains the default | Planned |
| R7.2 | [#45](https://github.com/skyeberhard/Hamletfolk/issues/45) | Local model provider | An optional provider generates lines from simulation state using a local model; off by default, falls back to templates | Planned |

## Unscheduled ideas

Not committed to. Promote an idea to a milestone (with a new ID) before working on it.

- Roads: paths wear in between homes, work and neighboring settlements, and become roads as traffic grows.
- Settlement tiers: hamlets grow into towns and cities, with districts and richer and poorer neighborhoods.
- Cultures per region: naming, architecture and values vary by biome.
- Villagers physically working (walking to the farm, carrying goods) when players are near.
- Crime, disputes over property.
- Disasters: drought, fire, disease.
- Smelting and fuel: miners produce ore, smiths turn ore plus fuel (wood or coal) into metal. Needs ORE and FUEL resource types; keep a single METAL until it matters.
- Distinct purposes for cleric (healing, slower need loss), librarian (faster skill learning) and cartographer (finding trade routes), instead of generic GOODS.
- Animal husbandry chain: a rancher-type occupation (SHEPHERD, extended) raises livestock that BUTCHER consumes, instead of butcher producing food for free. Same raw-producer/craft-consumer pattern as R2.3 (miner to smith) and R4.4/R4.5 (lumberjack to fletcher).
