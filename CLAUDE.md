# Hamletfolk

Paper server plugin (Minecraft **26.2**, **Java 25**) that turns vanilla villages into
simulated communities: persistent residents, a per-settlement resource ledger, daily
production/consumption, and a written history. Server-side only (works for Bedrock via Geyser).

Start with [README.md](README.md) (what it does), [ROADMAP.md](ROADMAP.md) (what's next) and
[docs/DESIGN.md](docs/DESIGN.md) (principles).

## Layout

- `core/` — the simulation, plain Java, **no Minecraft dependencies**. Residents, settlements,
  ledger, simulator, dialogue, save codec. Everything here is unit tested.
- `paper/` — thin Paper layer: maps villager entities and game events onto `core`, commands,
  saving. No unit tests; verified by compiling and by playtesting.

Put logic in `core` whenever possible and keep `paper` thin. Anything that can be expressed
without Bukkit types (e.g. `SaveSequence`, `SaveBackups`) belongs in `core` with a test.

## Commands

```
./gradlew build              # compile everything + core tests (Windows: gradlew.bat)
./gradlew :core:test         # simulation tests only
./gradlew runServer          # local Paper server in paper/run/ with the plugin installed
```

The first `runServer` stops to make you accept the EULA in `paper/run/eula.txt`.
`docs/TESTING.md` has setup, useful in-game commands, and playtest scenarios T1–T16.

## Workflow rules

- **Every change maps to a roadmap ID.** Commit messages and PR titles start with it
  (`R1.20: Fair allocation of scarce inputs`) and end with `Closes #<issue>`. Work that fits no
  item gets an item and a GitHub issue first (parented under its milestone issue, #1–#8).
- **ROADMAP.md is the source of truth.** When an item is done, set its row to `Done` and add a
  line to `CHANGELOG.md` under Unreleased, in the same commit. Keep row text identical to the
  issue body — read the issue, don't paraphrase from the title.
- **"Done when" is the acceptance test**, checked by a unit test or a `docs/TESTING.md`
  scenario that names the ID.
- Work on branch `claude/minecraft-npc-settlement-mod-h6nhem` (currently the default branch).
- **Review risky changes.** For Paper-layer, save-format and simulation-rule changes, run the
  `reviewer` subagent (`.claude/agents/reviewer.md`, runs on Opus) on the diff before committing.
  Fix the findings you verify are correct and say why you set aside any you don't; don't apply
  its suggestions blindly. Skip it for small core-only changes with clear tests.
- Deploys follow `docs/DEPLOYING.md`; `PRODUCTION.md` records what's live vs. verified.

## Gotchas

- **Single-day output tests flake.** A worker's daily output is
  `floor(base × diligence + random)`, which can legitimately be 0. Assert on accumulated output
  over several days (see `smithsIdleWithoutMetalAndResumeWhenSupplied`). Stress-test new
  simulation tests with repeated `--rerun` runs, checking Gradle's own exit code.
- **Save format changes need a migration.** Bump `SettlementCodec.FORMAT_VERSION` and add a
  step in `migrate()` with a test using a hand-built old-format map.
- **The simulation owns occupations** (R4.3). The vanilla profession may only fill in an
  UNEMPLOYED resident (`Resident.seedOccupation`); never call `setOccupation` from `SettlementService`
  or `VillagerListener`. Whether a job can be *assigned* comes from registered buildings
  (`SettlementSimulator.buildingsAllow`: farm and mine, four places each; shop, one merchant each;
  the lumberjack needs none). Food comes first in job assignment (R2.3).
- **Adult enrollment draws an age** (R4.15, 12 to 51 days), so a test that enrolls adults and runs
  past about 39 days can lose them to old age. Build residents with `new Resident(..., bornDay, ...)`
  and `Settlement.addResident` to control age, or use `SettlementSimulator.withOldAgeDeaths(false)`. The lifespan scale is a static
  (`Resident.setLifespanScale`, 1 in core, 20 from the Paper config): a test that changes it must restore it.
- **The treasury has a limit** (R2.6: `economy.treasury-base` plus 500 per `[Treasury]` sign; a village that held more keeps
  room for it via the `treasuryLegacy` condition). Tests that bank a lot must register a treasury building or put
  `SettlementSimulator.TREASURY_LEGACY` in the settlement's conditions. It has one spending so far: merchants (R3.9) fill it and requests (R3.3) spend it
  (the reward is held aside when a request is posted); R4.8 (builder) and R5.1 (guards) are next. STONE and GOODS now have an outlet in the
  merchant. TOOLS wear out (R3.6).
- **The tool penalty is a setting** (`economy.tool-penalty`, on in the Paper config, off by default in
  `new SettlementSimulator()` so core tests are unaffected; `SettlementSimulator.configured(oldAge, toolPenalty)`).
  A village with no mine has no metal and so only stone tools (R3.17: a smith falls back on cobblestone): that is the pressure to build one.
- **Construction (R4.7/R4.8):** `core/Construction` decides, `paper/ConstructionService` places. The diff is never saved (it is
  recomputed from the world each pass), a project stores only what and where plus half-unit payment credit. `Blueprint.diff` and
  `Construction.matches` compare only the block properties a blueprint names. Only projects on record are ever upgraded, so a
  player's building is never replaced; the site check gives up a lot that holds anything not terrain, trees or plants.
  Coded but unplayed (T49, T50). BUILDER is simulation-owned like GUARD.
- **Works (R5.6):** `BuildingType.STREET_LIGHTS` and `PALISADE` (`isWorks()`) are projects on the plan with no lot, no sign and no
  template: `core/Works` gives the columns, `ConstructionService.workWorks` places a fence post (and a torch) per column and
  charges `OAK_FENCE` for each. They are asked for by `Planner.withDefence` after any recorded incident; `BuildingType.fromSign`
  skips them so a sign cannot register one, `fromTarget` does not. An unaffordable need blocks wants (growth builds, the square,
  upgrades) in `Construction.propose`.
- **Village clock and warp (R4.22):** `/settlement admin warp` runs a village ahead and stores `Settlement.clockAhead()`; the
  simulation then targets `SettlementService.villageDay` (world day plus the lead). Paper code that records a day for a village
  uses `settlement.effectiveDay(day(world))`, never the raw world day. `construction.unattended` holds chunks with reference
  counted plugin tickets (`ConstructionService.hold/release`); nothing else in the plugin uses chunk tickets.
- **Suppliers (R4.24):** a village of four keeps a lumberjack, and a miner once a mine has room; lumberjacks, miners and masons
  rest at the storage limit (`resting:` conditions). Tests that measure a gatherer's output must spend the stock each day.
- **Levels, golems, marks (R4.29, R5.9, R4.25):** `Resident.xp()` is days worked in the trade the experience was earned in
  (`xpTrade`; a spell as a builder or guard keeps it, working at another trade starts it again); `TradeLevel` holds what each level gives. `Golems` decides and charges in core (called from
  `simulateDay`), the Paper layer spawns them (`SettlementService.placeGolems`) and records each by UUID. `WorldMarks` keeps
  the debt and the quarry in conditions; `WorldMarksService` (Paper) fells and digs. A test that runs lumberjacks or miners
  now also builds up `woodOwed`/`stoneOwed` conditions.
- **Commodities (R3.16, R3.17):** `Ledger` holds `Commodity` amounts; `get/add/take(ResourceType)` work on category totals (add
  goes to the plain commodity, take follows the declared order: produce before bread, stone tools before iron). Code that
  stores a real item uses `ResourceMapper.commodity(item)`. FUEL is a seventh `ResourceType`: any new switch over
  `ResourceType` must cover it. Smiths are special-cased in `SettlementSimulator.work` (iron, else cobblestone); smelting,
  charcoal and baking are `SettlementSimulator.process`, run before the day's work. Births need bread.
- **Pins, births, fast-forward (R1.31, R4.28, R4.27):** a pinned resident (`Settlement.isPinned`, `pinned:` conditions) is skipped
  by every rule that moves people between trades; a new rule that changes someone's occupation must skip pinned residents too.
  `Births.run` enrolls a sim-born child under a stand-in UUID (`awaitingVillager:` condition) and the Paper layer moves it onto
  its villager with `SettlementRegistry.bringToLife` inside `world.spawn`'s consumer, before the entity is added. Fast-forward
  is in memory only (stops on restart) and works through `SettlementService.warp`.
- **Building creates work (R4.20, R4.21):** a material a wanted building could not be paid for counts as short for 10 days
  (`Construction.lacking`, from the `constructionLacks:` conditions), whatever the stock per head, so a test that leaves a village
  unable to afford something can see a jobless resident become a lumberjack. A builder stays BUILDER between projects (released
  after `BUILDER_IDLE_DAYS` idle, or in a famine unless building a farm). A village with no mine gets a SUPPLY-tier "Build a
  mine" directive first once fed: planner tests filter it out with `needs(...)`.
- **Beds are not block entities in 26.2** (there is no bed block entity class), so `Chunk.getTileEntities` never finds one.
  Count them through points of interest (`World.locateAllPoiInRange` with `PoiTypes.HOME`), which also says which are claimed.
- **Villager brain code (M9):** the public API cannot add villager behaviours, so anything that needs it (guards that
  fight, R9.3) goes in the isolated, switchable `brain/` module described in docs/SMART_VILLAGERS.md, never into `core` or the
  rest of `paper`. Pinned to Paper 26.2; it must have the startup self-check and the live off switch before any behaviour.
- **`SettlementService.track()` returns null** for a world excluded by `worlds.allow/deny`
  (R1.10); callers must handle it.
- **Line endings:** working copies are CRLF (autocrlf) while the repo stores LF, so git warns
  about "LF will be replaced by CRLF". Harmless.
- The Paper module compiles on the local machine and has been run (playtest 2026-10-01: the server
  loads the plugin, a format-4 save migrated to 8, `/settlement`, talking, requests and donation
  payouts work). Most of it is still unplaytested: see docs/TESTING.md T1-T29.

## Where things stand

- Done: M0; M1 items R1.2, R1.3, R1.4, R1.5, R1.6, R1.8, R1.9, R1.10, R1.13, R1.14, R1.16, R1.17,
  R1.18, R1.19, R1.20, R1.21, R1.22, R1.23, R1.28, R1.29, R1.30; M2 items R2.1, R2.2, R2.3, R2.5, R2.6, R2.7; M3 items R3.1, R3.2, R3.3, R3.4, R3.5, R3.6, R3.7, R3.9, R3.10, R3.11, R3.12, R3.13, R3.14; M5 items R5.1, R5.5; M8 items R8.1, R8.2, R8.3, R8.4; M4 items R4.1, R4.2, R4.3, R4.4, R4.5, R4.9, R4.10, R4.14, R4.15, R4.16.
  Save format is 24 (R4.29 added residents' days worked in their trade; R3.16 made the stores hold commodities; R5.6 added the works STREET_LIGHTS and PALISADE as project types; R4.21 added the buildings a builder has finished; R4.7 and R4.8 added construction projects, their turn and grading and the builder occupation; R8.3 added the village plan; R8.1 added the planner's decision log; R5.5 added the days of recent attacks; R5.1 added the guard occupation; R2.6 kept room for existing treasuries; R4.2 added the departure history kind; R3.5 added resident wealth; R3.4 added per-player reputation; R2.2 added bed counts; R2.1 added buildings; R3.3 added requests; R1.21 added event count/actor, R3.7 added flow, R4.14 added gender, R4.15 added departed ids; 7 dropped the nonbinary gender).
- **Next, highest value:** R1.1 (first playtest — the Paper layer has never run; scenarios
  T1–T48 in docs/TESTING.md, of which T17 (admin), T18 (worlds), T19 (backups), T26 (appearance), T27 (requests) and T28 (donation values) and T29 (tool requests) and T30 (trade prices) and T31 (donation room), T32 (building signs) and T33 (housing) T34 (miners), T35 (reputation), T36 (trades) and T37 (trade stock), T38 (wealth) and T39 (migration) and T40 (membership) T41 (shops) and T42 (treasury) T43 (bank counter) T44 (guards) T45 (planner) T46 (survey) and T47 (plan and lots) and T48 (exempt signs) are
  Paper-only). After that R1.24 (needs a decision), R1.7 and R1.12, then M2 buildings.
- On a local machine, much of R1.1 can be driven from the server console (`/summon`, `/time add`,
  restarts, reading `plugins/Hamletfolk/settlements.json`); player-only steps (sneak +
  right-click, `/settlement` as a player, donate, the history book, Bedrock) need a person in game.
