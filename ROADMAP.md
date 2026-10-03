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
| R1.15 | [#52](https://github.com/skyeberhard/Hamletfolk/issues/52) | Admin and abandonment land before M2 | R1.4 (admin commands) and R1.5 (abandoned settlements) are merged before any M2 item begins. | Done |
| R1.16 | [#53](https://github.com/skyeberhard/Hamletfolk/issues/53) | Pin-and-verify workflow | A documented process records which commit is live in production versus which was last verified, and is checked before each deploy | Done |
| R1.17 | [#59](https://github.com/skyeberhard/Hamletfolk/issues/59) | Stale autosave can't overwrite a newer save | Each save snapshot carries a sequence number; a write older than the last one written is skipped, so an in-flight autosave can't overwrite the shutdown save | Done |
| R1.18 | [#60](https://github.com/skyeberhard/Hamletfolk/issues/60) | Save soon after a donation | A donation schedules a debounced save within a few seconds instead of waiting up to 5 minutes for the next autosave | Done |
| R1.19 | [#61](https://github.com/skyeberhard/Hamletfolk/issues/61) | Periodic backups while running | Besides the startup backup, a backup is taken on a configurable interval (default daily) using the same rotation | Done |
| R1.20 | [#62](https://github.com/skyeberhard/Hamletfolk/issues/62) | Fair allocation of scarce inputs | When an input runs short, which workers go without rotates day to day, so no resident is permanently idle while others always work | Done |
| R1.21 | [#63](https://github.com/skyeberhard/Hamletfolk/issues/63) | Bounded history | Repeated minor events are merged and stored entries are capped, keeping major events (founding, deaths, raids, famines) over minor ones | Done |
| R1.22 | [#64](https://github.com/skyeberhard/Hamletfolk/issues/64) | Record skipped catch-up days | When catch-up skips days beyond `max-catch-up-days`, history records how many were skipped | Done |
| R1.23 | [#65](https://github.com/skyeberhard/Hamletfolk/issues/65) | Handle world time moving backwards | If the world's day is earlier than a settlement's last simulated day, new residents and history use the settlement's day, and nothing is recorded out of order | Done |
| R1.24 | [#66](https://github.com/skyeberhard/Hamletfolk/issues/66) | Decide on unemployed foraging | `UNEMPLOYED` producing 1 food/day ("foraging") is either documented as intended (README and dialogue) or removed. | Done |
| R1.25 | [#74](https://github.com/skyeberhard/Hamletfolk/issues/74) | Shared Claude Code permissions for unattended runs | `.claude/settings.json` is checked in with an allowlist for Gradle, `git add`/`git commit` and `git push` to the working branch only, so an unattended local run doesn't stall at permission prompts; personal overrides go in the git-ignored `.claude/settings.local.json` | Done |
| R1.26 | [#75](https://github.com/skyeberhard/Hamletfolk/issues/75) | Playtest scenario T6 accounts for famine | T6 in docs/TESTING.md no longer promises the "no metal" dialogue while the village is starving, and a new scenario T6b checks that line once food has been donated. | Done |
| R1.27 | [#76](https://github.com/skyeberhard/Hamletfolk/issues/76) | Settlements anchored to a village's real area | A villager tracked inside a vanilla-generated village belongs to one settlement anchored to that village's whole area, however far it sits from the first villager seen, so a village spread over uneven terrain is a single settlement; outside any generated village the radius rule still applies; settlements whose areas overlap or lie within a few blocks are merged (residents, ledger and history combined, recorded in history) | Planned |
| R1.28 | [#80](https://github.com/skyeberhard/Hamletfolk/issues/80) | Reviewer subagent for risky changes | `.claude/agents/reviewer.md` defines a read-only reviewer subagent that runs on Opus and checks a diff against this repo's known traps (core vs paper placement, single-day output tests that flake, save-format migrations, occupation overwrites, determinism, roadmap bookkeeping, UTF-8), reporting each finding as verified or unverified; `CLAUDE.md` says when to run it, so a session on a cheaper model can have its risky changes reviewed by Opus without switching models by hand | Done |
| R1.29 | [#92](https://github.com/skyeberhard/Hamletfolk/issues/92) | Work plan and standing permissions for unattended work | `docs/WORKPLAN.md` lists every remaining roadmap item in a dependency-ordered queue split into core-first work, Paper-heavy work that can be written before a playtest, and work that needs a person in game. It also gives the working loop for one item (core first, stable tests, review, bookkeeping, gated commit) and the conditions for stopping to ask. The git-ignored `.claude/settings.local.json` holds a broad allowlist for that work (builds, tests, git and `gh` on this repo, file edits, helper scripts, the reviewer agent) and denies destructive operations, including edits to the settings files themselves. | Done |

## M2: Buildings (v0.3.0) · [#3](https://github.com/skyeberhard/Hamletfolk/issues/3)

Goal: what players build shapes what the village can do.

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R2.1 | [#24](https://github.com/skyeberhard/Hamletfolk/issues/24) | Sign registration | A `[Farm]`, `[Smithy]`, `[Mine]`, `[House]` or `[Guard Post]` sign inside a settlement registers a building, breaking the sign removes it, and `/settlement buildings` lists them. | Done |
| R2.2 | [#25](https://github.com/skyeberhard/Hamletfolk/issues/25) | Housing capacity | Beds within the settlement set a housing capacity, shown in `/settlement`. | Done |
| R2.3 | [#26](https://github.com/skyeberhard/Hamletfolk/issues/26) | Buildings affect output | A registered mine lets an unemployed resident become a miner who produces stone and metal, so smiths can work without donations. | Done |
| R2.4 | [#27](https://github.com/skyeberhard/Hamletfolk/issues/27) | Buildings inferred from blocks | Placing a workstation, bed and roof is recognized without a sign; analysis runs only when blocks in the area change | Planned |
| R2.5 | [#85](https://github.com/skyeberhard/Hamletfolk/issues/85) | Storefront for merchants | A `[Shop]` sign inside a settlement registers a storefront; breaking the sign removes it, and `/settlement buildings` lists it. A MERCHANT needs a free storefront to work, one merchant per storefront, so a settlement with no storefront has no merchant selling (a merchant without one is released back to unemployed), and building a second storefront lets a second merchant work. | Planned |
| R2.6 | [#86](https://github.com/skyeberhard/Hamletfolk/issues/86) | Treasury building | Without a treasury building a settlement can bank only a small base amount of emeralds (a number in config), and income beyond it (merchant sales, donations) is wasted as it is for goods over a storage limit (R3.10). A `[Treasury]` sign on a counting house inside the settlement registers a treasury building, and each registered one raises the limit by a fixed amount; breaking the sign lowers it again. `/settlement` shows the treasury and its limit. | Planned |
| R2.7 | [#88](https://github.com/skyeberhard/Hamletfolk/issues/88) | Bank counter for donations and exchange | Donating resources and exchanging them for emeralds (`/settlement donate`, and the payout for a request, R3.3) can only be done at a registered treasury building (R2.6), not anywhere in the settlement. Away from one, the command says where the nearest is. A settlement with no treasury building keeps the current anywhere-in-the-settlement rule, so existing villages still work. | Planned |

## M3: Economy (v0.4.0) · [#4](https://github.com/skyeberhard/Hamletfolk/issues/4)

Goal: trading with villagers is part of the village economy.

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R3.1 | [#28](https://github.com/skyeberhard/Hamletfolk/issues/28) | Prices follow supply | Villager trade prices rise when the ledger is short of that resource and fall when it has a surplus. | Done |
| R3.2 | [#29](https://github.com/skyeberhard/Hamletfolk/issues/29) | Trades feed the ledger | Selling food to a farmer adds it to the village's stores | Done |
| R3.3 | [#30](https://github.com/skyeberhard/Hamletfolk/issues/30) | Village requests | When a resource runs short, the village posts a request (e.g. "32 iron"), and fulfilling it pays emeralds from the treasury. | Done |
| R3.4 | [#31](https://github.com/skyeberhard/Hamletfolk/issues/31) | Player reputation | Each settlement tracks a reputation for each player based on donations, fulfilled requests and harm done, and both dialogue and prices reflect it | Done |
| R3.5 | [#32](https://github.com/skyeberhard/Hamletfolk/issues/32) | Resident wealth and wages | Residents earn from their work and spend on food, and their wealth shows in dialogue | Done |
| R3.6 | [#67](https://github.com/skyeberhard/Hamletfolk/issues/67) | Tool wear | Gathering occupations (farmer, fisher, lumberjack, mason, future miner) occasionally consume TOOLS as they work, and produce less while the village has none. A tool shortage is recorded and mentioned in dialogue like any other shortage. | Done |
| R3.7 | [#68](https://github.com/skyeberhard/Hamletfolk/issues/68) | Resource flow tracking | Each settlement keeps a rolling 7-day produced/consumed total per resource, shown in `/settlement` | Done |
| R3.8 | [#69](https://github.com/skyeberhard/Hamletfolk/issues/69) | Donations valued by what they're worth | Storage blocks count as their contents and tools by material tier; donating confirms (or takes a quantity) instead of silently taking the whole stack | Done |
| R3.9 | [#70](https://github.com/skyeberhard/Hamletfolk/issues/70) | Merchant occupation | A MERCHANT occupation sells surplus (goods, stone, and anything well above the village's needs) for emeralds into the treasury, so surplus has an outlet and the treasury has income. | Done |
| R3.10 | [#71](https://github.com/skyeberhard/Hamletfolk/issues/71) | Storage capacity and food spoilage | Each resource has a storage limit (a base amount, raised later by storage buildings from M2), production beyond it is wasted, and food slowly spoils. A surplus can end, and the "granaries full" milestone can clear and recur. | Done |
| R3.11 | [#81](https://github.com/skyeberhard/Hamletfolk/issues/81) | Donations respect storage limits | `/settlement donate` tells a player how much room the settlement has for what they are holding, and credits only what fits, so a donation to a full store is never accepted and then silently wasted. | Done |
| R3.12 | [#87](https://github.com/skyeberhard/Hamletfolk/issues/87) | Fair donation values for wood and tools | Donations are valued by what the items are made of, so handing in the same material in a cheaper form is not worth more. Wood: a log is worth four planks and a plank two sticks (a stack is valued as a whole and rounded down, so a stack of sticks is worth a fraction of the same number of logs). Tools: a worn tool is worth less than a new one in proportion to its remaining durability. A request payout (R3.3) can then never pay more for a stick or a nearly broken tool than for the log or the new tool it came from. | Done |
| R3.13 | [#91](https://github.com/skyeberhard/Hamletfolk/issues/91) | Tool requests can't be farmed from raw materials | Handing in a crafted tool never pays more emeralds on a request than handing in the materials it is made of. A tool is valued from its recipe (a wooden shovel is a plank and two sticks, a stone sword two cobblestone and a stick), converted at the same rates as raw donations, and a TOOLS request pays by that value, not by material tier alone. | Done |
| R3.14 | [#103](https://github.com/skyeberhard/Hamletfolk/issues/103) | Villagers sell only what the village can spare | A villager sells food, wood, stone or metal only while the settlement can spare it: once the stores are down to the level the village wants to hold, a sale the stores cannot cover is refused until they have recovered. A newly founded village starts with some food so its first traders have something to sell. | Done |

## M4: Growth and migration (v0.5.0) · [#5](https://github.com/skyeberhard/Hamletfolk/issues/5)

Goal: villages grow or shrink because of their circumstances.

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R4.1 | [#33](https://github.com/skyeberhard/Hamletfolk/issues/33) | Newcomers | When there's a food surplus and free beds, a new villager arrives and the arrival is recorded in history. | Done |
| R4.2 | [#34](https://github.com/skyeberhard/Hamletfolk/issues/34) | Migration | Unemployed or unhappy residents leave for a better-off settlement nearby, recorded in both histories | Planned |
| R4.3 | [#35](https://github.com/skyeberhard/Hamletfolk/issues/35) | Jobs follow need | Unemployed residents take the occupation the village is shortest of, if a workstation is free. The simulation owns a resident's occupation: the villager's vanilla profession only seeds it: it fills in an occupation while the resident is still `UNEMPLOYED` (a newborn, or someone who claims a workstation later) and never replaces one. Both places that currently overwrite it stop doing so: `SettlementService.track()` (it unconditionally calls `resident.setOccupation(occupationOf(villager))` on every tracking pass) and the career-change handler in `VillagerListener`. Otherwise an assigned `LUMBERJACK`/`MINER` is stomped back to the villager's vanilla profession (often `UNEMPLOYED`) the moment the entity re-enters a loaded chunk or claims a nearby workstation | Done |
| R4.4 | [#54](https://github.com/skyeberhard/Hamletfolk/issues/54) | Lumberjack occupation | A LUMBERJACK occupation produces WOOD with no vanilla profession backing it and no building required, replacing FLETCHER as the de facto wood source | Done |
| R4.5 | [#55](https://github.com/skyeberhard/Hamletfolk/issues/55) | Fletcher consumes wood, not produces it | FLETCHER consumes WOOD and produces GOODS (arrows), matching its actual trade, once R4.4 gives wood a real source | Done |
| R4.6 | [#56](https://github.com/skyeberhard/Hamletfolk/issues/56) | Building templates with tiers | A building type can have multiple ordered tiers (e.g., crude → solid) for each biome (plains, desert, savanna, snowy, taiga), each tier a structure template: a vanilla village piece where the game ships one (small, medium and big houses, farms, libraries and so on) and a hand-authored NBT structure for the gaps (a mine, guard post, shop, storage, and any tier vanilla lacks). A catalog maps building type, biome and tier to a template key, and the Paper layer checks at startup that every key exists on the server. A settlement's biome is that of its village, plains if unknown. Given a building's current tier and the settlement's available materials, the system can work out the materials a tier needs from its blocks, determine the best affordable tier, and compute the block-level diff needed to reach it from the current world state. | Planned |
| R4.7 | [#57](https://github.com/skyeberhard/Hamletfolk/issues/57) | Terrain-triggered construction and upgrade projects | A settlement lacking a needed building type queues a project on suitable terrain (R4.6); existing buildings are checked for affordable upgrades; player-built buildings are never auto-replaced | Planned |
| R4.8 | [#58](https://github.com/skyeberhard/Hamletfolk/issues/58) | Builder occupation and work queue | A BUILDER occupation claims one queued project at a time, consumes materials from the ledger as it executes the block diff over real time, and the remaining work survives a restart | Planned |
| R4.9 | [#72](https://github.com/skyeberhard/Hamletfolk/issues/72) | Unmet needs reduce output | A resident's food, safety and purpose affect how much they produce: a starving or terrified worker produces noticeably less. Combined with R4.2 (migration), low needs have real consequences. | Done |
| R4.10 | [#73](https://github.com/skyeberhard/Hamletfolk/issues/73) | Children become apprentices in needed trades | When a child grows up, they take the occupation the village is shortest of (using R4.3's logic), preferring a parent's trade when it's also needed, and history records the apprenticeship. | Done |
| R4.11 | [#77](https://github.com/skyeberhard/Hamletfolk/issues/77) | Personality drives visible behavior | A resident's sociability, bravery, work ethic and ambition change what players can see them do, not just what they say, using vanilla mechanisms that work with villager AI (e.g. steering via job-site, home and meeting-point memories); at least two traits produce a noticeable, observable difference between residents | Planned |
| R4.12 | [#78](https://github.com/skyeberhard/Hamletfolk/issues/78) | Overheard lines and mood display | Villagers near a player occasionally produce short ambient reactions driven by simulation state: an overheard line shown to nearby players, particles or a head-shake around a struggling worker, and a mood color or marker in the display name; rate-limited and switchable in config | Planned |
| R4.13 | [#79](https://github.com/skyeberhard/Hamletfolk/issues/79) | Pair sim-only occupations with a vanilla workstation | Each occupation that has no vanilla profession (`LUMBERJACK`, future `MINER`) is paired with an existing vanilla workstation block, chosen and documented per occupation (for example the stonecutter, whose saw blade suits a lumberjack), so a resident holding it visibly works at a job block during work hours; the pairing is cosmetic and doesn't affect the simulation's occupation | Planned |
| R4.14 | [#82](https://github.com/skyeberhard/Hamletfolk/issues/82) | Residents have a gender | Every resident has a gender (female or male), chosen when they are enrolled and saved. Given names come from per-gender lists (the existing names are split into feminine and masculine), so a resident's name and gender agree. Residents in an existing save are given a gender matching their current given name (a name on no list gets one deterministically from the resident's id) and keep their names; a resident an earlier build saved as nonbinary is given one the same way. `/settlement residents` and dialogue can refer to a resident correctly. Children take a gender independently of their parents'. | Done |
| R4.15 | [#83](https://github.com/skyeberhard/Hamletfolk/issues/83) | Life stages and aging | A resident's age in days follows from their birth day, and their life stage (child, adult, elder) follows from their age. Elders produce less or stop working, and old age eventually ends a resident's life, recorded in history like any other death. A resident's age or stage shows in `/settlement residents` and can come up in dialogue. Residents who were already present when a settlement was founded get a plausible age rather than all being born on the founding day. | Done |
| R4.16 | [#84](https://github.com/skyeberhard/Hamletfolk/issues/84) | Appearance hooks for a resource pack | Each tracked villager carries stable, documented appearance data for matching on the server (datapacks, command selectors, other plugins): persistent data keys for gender, occupation and life stage (after R4.14 and R4.15). These stay on the server and clients cannot see them. Optionally (off by default, a config setting), the plugin also sets the villager's vanilla type from a documented mapping of gender and life stage, which does reach clients, so a plain resource pack that redefines those type skins can reskin villagers with no client mod; turning the setting off restores the original type. The data keys and the mapping are written up in docs/APPEARANCE.md, and the mapping lives in `core` with a unit test. | Done |
| R4.17 | [#104](https://github.com/skyeberhard/Hamletfolk/issues/104) | Residents visibly work at their workplace | While a player is nearby, a resident whose job has a registered workplace (a farmer at a `[Farm]`, a miner at a `[Mine]`, a smith at a `[Smithy]`) walks there during work hours, plays a short work animation, and returns, using only vanilla pathfinding and animations. It is presentation only: the ledger, output and needs are exactly what they would be without it. Residents far from every player do nothing, the number of residents moving at once is limited, and the whole thing can be switched off in config. | Planned |

## M5: Defense (v0.6.0) · [#6](https://github.com/skyeberhard/Hamletfolk/issues/6)

Goal: danger creates demand, and the village responds.

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R5.1 | [#36](https://github.com/skyeberhard/Hamletfolk/issues/36) | Guards | Sustained high threat turns a resident into a guard who consumes tools and food | Planned |
| R5.2 | [#37](https://github.com/skyeberhard/Hamletfolk/issues/37) | Defenses reduce threat | Recognized walls, lighting and towers lower how much threat each attack adds | Planned |
| R5.3 | [#38](https://github.com/skyeberhard/Hamletfolk/issues/38) | Calls for help | When threat is high, residents ask nearby players for help, and defending the village raises reputation | Planned |
| R5.4 | [#89](https://github.com/skyeberhard/Hamletfolk/issues/89) | Vault break-ins and their consequences | Taking items from a registered vault (the treasury building, R2.6) or breaking its protected blocks without the settlement's leave counts as a break-in. The stores fall by what was actually taken, the history records a major event naming who did it, residents' mood and the settlement's threat worsen, and a player's reputation (R3.4) falls. Mobs or raiders that break in have the same effects without a reputation penalty. | Planned |

## M6: Society (v0.7.0+) · [#7](https://github.com/skyeberhard/Hamletfolk/issues/7)

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R6.1 | [#39](https://github.com/skyeberhard/Hamletfolk/issues/39) | Households and marriage | Residents form households; children live with parents; history records marriages | Planned |
| R6.2 | [#40](https://github.com/skyeberhard/Hamletfolk/issues/40) | Businesses | A building can be owned by a resident or player, employ residents and pay wages | Planned |
| R6.3 | [#41](https://github.com/skyeberhard/Hamletfolk/issues/41) | Player investment | Players can fund a business or building and receive a share of its output | Planned |
| R6.4 | [#42](https://github.com/skyeberhard/Hamletfolk/issues/42) | Village leadership | Each settlement has an elder chosen from its residents, who sets one policy (e.g. tax rate) | Planned |
| R6.5 | [#43](https://github.com/skyeberhard/Hamletfolk/issues/43) | Trade between settlements | Settlements exchange surplus for shortage along recorded trade routes | Planned |
| R6.6 | [#90](https://github.com/skyeberhard/Hamletfolk/issues/90) | Steward occupation | Once a settlement is large enough that its elder (R6.4) cannot also run it, an unemployed resident becomes its STEWARD, one per 40 residents. The steward keeps the stores, sets the exchange rates (replacing the fixed ones; they follow supply, R3.1) and staffs the bank counter (R2.7), where players trade resources for emeralds. A settlement below the size threshold has no steward and its elder does that work. | Planned |

## M7: Optional AI dialogue · [#8](https://github.com/skyeberhard/Hamletfolk/issues/8)

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R7.1 | [#44](https://github.com/skyeberhard/Hamletfolk/issues/44) | Pluggable dialogue provider | Dialogue goes through an interface; the template provider remains the default | Planned |
| R7.2 | [#45](https://github.com/skyeberhard/Hamletfolk/issues/45) | Local model provider | An optional provider generates lines from simulation state using a local model; off by default, falls back to templates | Planned |

## M8: Plan-owned villages · [#93](https://github.com/skyeberhard/Hamletfolk/issues/93)

Goal: villages the plugin plans, with a guaranteed square, street and lots, growth into reserved lots, sites chosen by resources, buildings graded onto terrain, and ruins on failure. Behind a `village-mode` switch; see [docs/VILLAGE_PLANNING.md](docs/VILLAGE_PLANNING.md).

| ID | Issue | Item | Done when | Status |
|---|---|---|---|---|
| R8.1 | [#94](https://github.com/skyeberhard/Hamletfolk/issues/94) | Village planner and decision log | Once per simulated day a settlement's planner reads the ledger and needs and issues directives (reserve a lot, open a job slot, flag an import need), working through the tiers food, shelter, safety, then trade and growth, with hysteresis (a tier counts as met at 100% and slips at 70%) so priorities do not flap. It finds the binding constraint by walking a production dependency graph (tools need metal, which needs a miner, which needs a mine) back from the unmet demand to its root. Every decision is written, with its reason (for example "building mine: metal shortage, ore at X"), to a bounded decision log that is separate from the history, deterministic for a given settlement and day, and readable in dialogue. A village its founder has walked away from keeps running the planner at a slow default pace, so administration is an optimisation layer, not a chore. | Planned |
| R8.2 | [#95](https://github.com/skyeberhard/Hamletfolk/issues/95) | Site scoring from resources | A pure function scores a candidate site from sampled data within 64 to 96 blocks (water, lumber, farmland, livestock, stone and ore) with weights and a minimum threshold, not a pass or fail per need, and returns a site profile: the per-resource scores and the starting conditions they imply (growth rate, food balance, what the village must import). A site missing a resource is allowed. The Paper layer samples on a coarse grid (every 16 to 32 blocks) using computed biome lookups, without generating chunks to reject them. | Planned |
| R8.3 | [#96](https://github.com/skyeberhard/Hamletfolk/issues/96) | Road graph and lot reservation | At founding a settlement stores a plan: a main square, a main street spine, branches, and reserved lots along them, each lot tied to a building type, a biome set and a village stage. Lots on a slope over the limit are dropped at founding. Growth fills the next reserved lot and extends the roads as the village levels up. The plan is saved with the settlement. | Planned |
| R8.4 | [#97](https://github.com/skyeberhard/Hamletfolk/issues/97) | Terrain pads for lots and roads | For a lot, given a heightmap of its footprint and a buffer, the system computes a pad: a target height (the median of the footprint), the blocks to cut, the fill with biome-appropriate blocks, a foundation skirt down to solid ground so nothing floats, and a blended edge. Roads rise or fall at most one block per step, with slabs and stairs on slopes. The pad is computed and applied when the building is built, not at founding. | Planned |
| R8.5 | [#98](https://github.com/skyeberhard/Hamletfolk/issues/98) | Players found villages | A player founds a village by placing a bell, a bed under a roof and a sign reading [Village] (the name on the second line) together, and handing over a starter kit of food, wood and stone that goes into the new village's stores. A cap on villages per world (default 3, counting player-founded ones), a minimum spacing between villages, and the kit are configurable, and failing any of them says why. The first resident is a villager standing nearby who is not already in a settlement; failing that a traveller arrives and settles in the bed. That founder, with the planner (R8.1) and the stores, develops the village from there. A survey item (a book) reports the site profile (R8.2) before the player commits. The score never blocks founding. | Planned |
| R8.6 | [#99](https://github.com/skyeberhard/Hamletfolk/issues/99) | Place planned buildings over time | Reserved lots are filled by placing the biome's template (R4.6) through the server's structure manager: block edits on the main thread in slices of a few thousand a tick, with physics updates skipped and chunks loaded asynchronously beforehand. Each placed building is registered as a building (R2.1) automatically, and a failed placement leaves its lot reserved. The builder occupation (R4.8) is what spends materials from the ledger as it goes. | Planned |
| R8.7 | [#100](https://github.com/skyeberhard/Hamletfolk/issues/100) | Village mode switch and vanilla village suppression | A config setting `village-mode` chooses `adopt-vanilla` (today's behaviour, the default) or `plugin-owned`. In plugin-owned mode a bundled datapack empties the vanilla village structure set so vanilla villages stop generating in new chunks, and the plugin chooses sites itself (R8.2), deterministically for the world seed, keeping the best of several candidates per world. Before this is relied on, the datapack is shown to apply to worlds created through the portal world-creation path. | Planned |
| R8.8 | [#101](https://github.com/skyeberhard/Hamletfolk/issues/101) | Ruins instead of vanishing | When a settlement is abandoned its buildings decay rather than disappear: placed buildings are swapped for their ruined versions where the game ships one (the zombie village pieces) and the rest are left as they are. Nothing a player built is removed, ruins can be resettled, and a declined or abandoned village costs almost nothing to simulate. | Planned |
| R8.9 | [#102](https://github.com/skyeberhard/Hamletfolk/issues/102) | Village stages and the failure path | A village has stages and can slip back: stalled, then declining, then abandoned. Low farmland stalls growth and makes people leave, low lumber or stone leaves lots unfilled until supplied, and low water caps the village's stage. The thresholds are configurable and every transition, with its reason, goes to the planner's decision log (R8.1). | Planned |

## Unscheduled ideas

Not committed to. Promote an idea to a milestone (with a new ID) before working on it.

- Roads: paths wear in between homes, work and neighboring settlements, and become roads as traffic grows.
- Settlement tiers: hamlets grow into towns and cities, with districts and richer and poorer neighborhoods.
- Cultures per region: naming and values vary by biome. (Architecture per biome is covered by R4.6's template sets.)
- Villagers carrying goods between buildings when players are near. (Walking to the workplace and working there is R4.17.)
- Villagers walking the planned roads (A* over the road graph, fed to the pathfinder). Risky: vanilla villagers move through their brain, not the goal system, so prefer real path blocks and steering through job-site, home and meeting-point memories (R4.11).
- Crime, disputes over property.
- Disasters: drought, fire, disease.
- Smelting and fuel: miners produce ore, smiths turn ore plus fuel (wood or coal) into metal. Needs ORE and FUEL resource types; keep a single METAL until it matters.
- Distinct purposes for cleric (healing, slower need loss), librarian (faster skill learning) and cartographer (finding trade routes), instead of generic GOODS.
- Animal husbandry chain: a rancher-type occupation (SHEPHERD, extended) raises livestock that BUTCHER consumes, instead of butcher producing food for free. Same raw-producer/craft-consumer pattern as R2.3 (miner to smith) and R4.4/R4.5 (lumberjack to fletcher).
