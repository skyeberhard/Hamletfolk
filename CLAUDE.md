# Hamletfolk

Paper server plugin (Minecraft **26.2**, **Java 25**) that turns vanilla villages into
simulated communities: persistent residents, a per-settlement resource ledger, daily
production/consumption, and a written history. Server-side only (works for Bedrock via Geyser).

For what each building needs (lot size, beds, places, when it is asked for) see [docs/BUILDING_REQUIREMENTS.md](docs/BUILDING_REQUIREMENTS.md).

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
`docs/TESTING.md` has setup, useful in-game commands, and playtest scenarios T1–T81.

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

- **`SimulationPerformanceTest` is a timing test** (median day for 50 villages of 50 under 5 ms) and was the cause of an unexplained
  one-in-forty build failure: it had crept to 4.0 to 5.0 ms as features were added, so any load on the machine tipped it over. If it
  fails, profile before touching the budget: `core/build/classes` plus a small harness with `-XX:StartFlightRecording`, then
  `jfr print --events jdk.ExecutionSample`. Anything called for every resident every day (`Trades.worked`, `Ledger.get`,
  `Commodity.of`) must not build a stream, a copy or a string. After the R9.2 clean-up it sits at 3 to 4 ms.
- **Single-day output tests flake.** A worker's daily output is
  `floor(base × diligence + random)`, which can legitimately be 0. Assert on accumulated output
  over several days (see `smithsIdleWithoutMetalAndResumeWhenSupplied`). Stress-test new
  simulation tests with repeated `--rerun` runs, checking Gradle's own exit code.
- **Save format changes need a migration.** Bump `SettlementCodec.FORMAT_VERSION` and add a
  step in `migrate()` with a test using a hand-built old-format map.
- **The vanilla profession follows the simulation (R4.45):** `SettlementService.applyAppearance` sets it from `Appearance.profession`, `VillagerListener.guardCareerChange` cancels the game's own changes that disagree (not for an unplaced jobless resident), and nothing may change the profession of a villager with experience or a level above 1 (`professionLocked`). `applyLevel` raises the vanilla level only for the resident's real trade.
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
- **Shortage staffing (R4.35):** `assignJob` only serves the jobless, so `SettlementSimulator.staffShortages` (after `staffSuppliers`) moves one
  `spareWorker` a day onto a free food place while food is short, else onto a free smithy place while tools are short (even in famine). Anything that moves
  employed people must keep skipping pinned residents, guards and the last of a supplying trade (all inside `spareWorker`, which also skips anyone moved in the last `MOVE_COOLDOWN_DAYS` and prefers the least experienced; call `noteMoved` when a rule moves someone).
- **Famine building (R4.36):** in a famine only `Construction.endsFamine` types are queued or staffed: a farm, and a smithy while
  `Planner.smithyWouldEaseShortage` (no tools, no smithy, `SettlementSimulator.smithCanWork`). `propose` returns nothing else in a famine, and
  `dropStaleQueuedProject` cancels a queued non-famine project when a FOOD-tier BUILD is wanted (one open project at a time).
- **Lots (R4.37):** `Construction.propose` no longer lets a building use a lot reserved for another kind the village has none of yet (houses
  excepted as the donor), and when the plan has no RESERVED lot of the wanted kind it calls `PlanGenerator.addReplacementLot` (nearest good ground, near a
  street, clear of lots, streets and registered buildings; `lotSearch:<TYPE>` condition, once in 10 days). The smithy is also a SUPPLY directive
  (`Planner.withSupply`) outside a famine. A mine already standing is upgraded before a second is dug.
- **Suppliers (R4.24):** a village of four keeps a lumberjack, and a miner once a mine has room; lumberjacks, miners and masons
  rest at the storage limit (`resting:` conditions). Tests that measure a gatherer's output must spend the stock each day.
- **Counts leanings (R8.13):** SCHOLARLY, CRAFT and TRADING come after ALL_ROUND in `Leaning` (the saved number is the ordinal plus one:
  never insert before them) and are never stored: `Settlement.leaning()` returns them only when the stored biome reading is ALL_ROUND,
  from `LandCounts` (`Leaning.fromCounts`). A leaning's signature that is also a trade building (`Trades.isTradeBuilding`: harbour, pens,
  map room, glassworks) is asked for by the trade rule once its trade opens, not by the signature rule; the trading post is not a trade building.
- **Street grading (R4.33):** `StreetGrade.computeStreets` takes `Street(rect, alongX)`; a piece of street clipped to a window can be wider than long,
  so use `VillagePlan.runsAlongX(road)` for its direction, never the piece's shape. `compute(List<Rect>)` guesses from the shape and is for whole streets only.
- **Plan stages and districts (R8.14):** `VillagePlan.stage()` runs 1 to `PlanGenerator.MAX_STAGE` (4); `PlanGenerator.stageFor(population)` says which
  stage a village is big enough for (25, 50, 100) and `extend` adds one stage at a time (the main street is one `SPINE` road piece per stage:
  never assume there is one, and `VillagePlan.fromMap` accepts any number of them). `Districts` (core) derives the districts from the plan,
  never stored: stage 1 is the old town, stage 2 the new quarter, later ones are named for the commonest non-house lot type of their stage.
  `Districts.wall` reads the finished PALISADE (its tier is the stage) and RAMPART (its `stage()`) projects.
- **Workshop trades (R8.15):** fletcher, mason, weaponsmith, armorer and cleric are `Trades` rules too, but their triggers also read the
  settlement (a mine, a smithy, an attack in the last 90 days, metal in the stores, temperament, size). A building can employ more than one
  trade: use `BuildingType.jobs()`, not `job()` (the first), when counting places. `Trades.wanted` lists a building once however many of its
  trades are open.
- **Land trades (R8.13):** `LandCounts` (conditions `landCount:*`, only ever raised) is filled by `SettlementService.surveyLand` every 10
  village days from loaded chunks; `Trades` holds the rules (`opens`, `wanted`, `buildingFor`, `noteOpened`). A land trade is only a
  job candidate while `Trades.opens` (`SettlementSimulator.candidates`; FISHERMAN is also in `JOB_CANDIDATES` and so is not gated by the
  land), and needs a registered trade building (`BuildingType.job()`, four places) like a farmer needs a farm. `Planner.growth` asks
  for the first wanted trade building. The trade buildings are generated (`BuildingGenerator.furnish`) with their trade's workstation;
  `Blueprint.WORKSTATIONS` prices those blocks. Keep goods out of any trade building's price (a shepherd's pens must not wait for wool).
  A test that wants a trade open sets counts with `LandCounts.record(settlement, Map.of(feature, n), day)`.
- **Rampart parts (R5.11):** `BuildingType.GATEHOUSE` and `TOWER` (`isPart()`) are captured templates only: no ladder, never a project,
  never read from a sign (`fromSign` skips them; `fromCapture` accepts them). `TemplateCatalog.part(type, style, rampartTier)` is the
  exact-tier lookup. `RampartParts` (core) gives the placements: a gatehouse is captured with its passage north-south and its outside
  to the south, a tower as the north-west one; both are turned with `Blueprint.rotated`. `Rampart.pieces/price` take the two optional
  blueprints, `Rampart.parts` lists the placements, and `ConstructionService.workRampart` builds them after the wall pieces.
  A rampart project now stores the village's biome (it was "plains") so the Paper layer finds the right captures.
- **Rampart (R5.10):** `Rampart.pieces(plan, stage, tier)` (core) describes wall columns, gate pillars/lintels and corner towers as
  blocks relative to each anchor column's ground; `ConstructionService.workRampart` finds that ground by looking down through the
  rampart's own materials and re-evaluates every piece from the world each pass. A RAMPART project's `tier` is the wall tier (2 or 3)
  and its `stage` the plan stage; `Construction.wallTier(settlement, stage)` is 0 (nothing), 1 (fence), 2 or 3. It never sets
  `needUnmet`, unlike lights and the fence.
- **Voices (R4.30):** `Dialogue` decides *what* is said (a `Situation` plus slot values, or a plain string for the many remarks that
  have no voiced lines yet); `Voice.of(resident, settlement, day)` (never stored, deterministic) gives the tone and habit; `Lines`
  holds the text (`core/src/main/resources/dialogue/<situation>.txt`, `tone: text`, `+tone:` adds, `{slots}`; a line with an unfilled
  slot is skipped). `Dialogue.setLibrary` swaps the lines in use (the Paper layer lays `plugins/Hamletfolk/dialogue/` over the
  built-ins; a test that changes it must restore it). The tone weights in `Voice.BIAS` were solved so that ordinary traits give about
  one villager in six per tone: change a weight and re-run `VoiceTest`. Tests must not assert one exact wording of a voiced line
  (it depends on the villager's random traits): assert on its facts (the slot values) or on membership in the tone's lines.
  `Resident.lastLine` (not saved) stops a repeat. R4.32: the land, mood, size, wealth and parent remarks have lines of their own (LAND, MOOD,
  SIZE, WEALTH, PARENT) whose facts come from `vocab_land/mood/size/wealth.txt` (`Lines.VOCAB_GROUPS`); the plain sentences in `landLine`,
  `moodLine`, `sizeLine`, `wealthLine` and `parentLine` are the fallback (and what `characterLines` returns). R4.31: an `Option` with no situation (every other remark) is wrapped by the REMARK lines
  (`{statement}` is the sentence), so a test that looks for a remark's words can still find them; talk about a trade is WORK or WORK_EXPERT
  with the slots of the trade's entry in `vocab_trade.txt` (`Lines.vocab`); `Dialogue.Ambient` (sky, time of day) comes from the Paper layer and
  is null elsewhere; `{Name}` in a line is the slot `name` with a capital letter. `VillagerListener.sayGoodbyeWhenTheyWalkAway` calls `Dialogue.farewell`.
- **Signature buildings (R8.12):** `Leaning.signature()` names the building; `Planner.growth` asks for it (after the house check, so it
  never hides one); the effects are three `buildingCount` checks in `SettlementSimulator` (lumberjack output, `process` smelting,
  `spoilAndCap`). They are real registered buildings, so they take any free lot and can be registered by sign.
- **Character (R8.11):** `Settlement.leaning()` (from `setLand`, stored in conditions as `leaning`/`land:*`, read once by
  `SettlementService.readTheLand`) and `VillageCharacter.temperament(settlement)` (derived from history every call, never stored)
  change behaviour in a few places: `SettlementSimulator.leaningBias` and `permanentTrade`, `Construction.direction` and the
  upgrade pace, `Golems.wanted`, `newcomerDue`. Temperament reads the last 60 days of the capped history (cheap), so it is safe to call often; it is meant to change back.
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
  rest of `paper`. Pinned to Paper 26.2. R9.1 built it: `brain/` compiles against `paper/run/versions/26.2/paper-26.2.jar` and
  `paper/run/libraries` (run `./gradlew runServer` once first, or the build leaves the module out), and its classes go into the
  plugin jar. `paper` talks to it only through `BrainModule` (no internal types) and loads `VillagerBrains` by name after
  `BrainSelfCheck` passes; every internal class, field or method the module uses must be on `BrainSelfCheck.REQUIRED`. Behaviours
  go into the brain's private `availableBehaviorsByPriority` table (not `Brain.addActivity`, which replaces requirements), and that
  table may only be changed between ticks, never from inside a behaviour. R9.2 tools: `BrainStats` (per call and per tick, fed by
  `VillagerBrains.record(name, serverTick, nanos)` from every behaviour), `DecisionLog` (filled only while debug is on, through
  `VillagerBrains.decided`, which a behaviour calls only when `debugging()`), `BrainReport` (the text), and `BrainService.inspect/debug/report`. Read internals from the jar with javap, never from memory
  (the villager class moved to `npc.villager` in 26.x). R9.4: a new behaviour needs three things: a `Spec` in `BrainBehaviours.ALL`
  (name, family, budget), a maker in `VillagerBrains.MAKERS`, and a `brain.behaviours.<name>` block in config.yml. It must ask
  `module.active(name)` before acting and call `module.record(name, serverTick, nanos)` for every call (that is what the budget
  judges). `BrainModule.attach(villager, names)` gives exactly that set; the plugin decides the set (`BrainService.fit`: the
  running ones near a player, none otherwise). The budget is not judged for the first `BehaviourMeter.WARM_UP` (100) measured
  ticks after a start or a switch-on. An over-budget trip arrives inside a brain tick, so the plugin only queues the
  `detachEverywhere` for the timer. R9.5: a behaviour that needs a villager somewhere calls `VillagerBrains.travel` (the plugin:
  `BrainModule.travel`), never sets WALK_TARGET itself; `Journey` (core) holds the fallback rules and is tested with a fake
  `Mover`, `JourneyBehaviour` is the only mover. The result callback runs inside a brain tick (or from a take-off), so it only logs or
  notes. `standingNear` must never load a chunk. A position in a log line is written `x=.. y=.. z=..` (`Journey.Point.toString`) so
  `BrainReport.redact` takes it out. R9.3: `GuardBehaviour` sits at priority -1 in CORE (`VillagerBrains.priority`), before
  vanilla's panic trigger (priority 0), and does everything in `checkExtraStartConditions` (it never "runs"): for a guard fit to
  fight it erases NEAREST_HOSTILE and HURT_BY (only when they are a monster it fights, `GuardBehaviour.hostile`: zombies, skeletons,
  spiders, raiders; never a zombie villager, zombified piglin or wither skeleton) so the villager does not panic, and a badly hurt one leaves them, so vanilla panic is its retreat. Villagers never heal
  by themselves, so a guard heals slowly out of a fight (`GuardDuty.heals`), or a retreat would last for ever. Chasing a moving monster is the one place a behaviour sets WALK_TARGET itself. The plugin gives it only to a guard
  on duty (`GuardDuty.onDuty`: a GUARD with tools in the stores; `GuardDuty.fit`), checked each second; the rules are in core.
  R9.6: `PatrolBehaviour` (priority -1, after the guard at -2, same "never runs" style) walks `PatrolRoute` spots through `JourneyBehaviour.begin` and
  gives way when `GuardBehaviour.busy()`; `BrainService.routeFor` feeds `setPatrol` each second. A night-watch guard is woken with `stopSleeping` and
  walks without pausing (a villager with no walk target goes to bed); the guards on duty take the nights in turns (`PatrolRoute.watchesNight`).
  R5.12: the palisade's posts are stacked (`Works.stacks`, `ConstructionService.wallOf`) so the wall has no step of more than a block, carried
  through water up to 3 deep (posts from the bed up), and allowed on plain paving; the ground is never changed and the answer is computed looking
  down through the posts already standing, so an old wall is mended by adding to it (`/settlement admin regrade` = `Construction.reopenPalisade`,
  which queues a NEW palisade project, not an upgrade, so no old ring is taken down; staffed like any project: an idle or
  jobless builder, else a worker drafted after `DRAFT_AFTER_DAYS`). A wet column that already holds posts is still read as wet from the
  water beside it, and a floor that cannot take a post (a roof, a wall top) makes its cell a gap rather than raising its neighbours.
  R4.34: `/settlement admin resettle` (`SettlementService.resettle`, rules in core `Resettlement`) spawns settlers at a village's centre once the chunk
  is loaded (holding it under key "r"+id if not), enrols them with `track`, and adds a kit; a lost village (empty or abandoned) also has its threat,
  `abandoned` and `emptySince` cleared at once (the sim only clears them on the village's next day). Newcomers are spawned from nothing; migration moves
  real villagers; neither can revive a village with nobody in it.
  R8.18: `SettlementService.ensurePlan` holds the ground a plan needs (`ChunkRegion.covering`, key "g"+village id, through
  `ConstructionService.holdKey`) when `construction.unattended` is on, retries every 40 ticks for 30 s, and lets go once planned; it used to wait for a
  player to load it, so a village nobody visited never had a plan.
  R4.17: `WorkBehaviour` (priority -1, "never runs" like the patrol) walks a resident to `WorkDuty.workplace` (the registered building of its trade's kind, picked by id) through `JourneyBehaviour.begin` in the working hours `WorkDuty.workHours` (the world's clock: vanilla's WORK activity needs a claimed workstation), to the registered height (never the roof: `Journey` finds the floor within a few blocks), then turns its head with a `BlockPosTracker` look target and swings; at most `WorkDuty.MAX_WALKING` walk at once (`VillagerBrains.walkingToWork`). `BrainService.fit` gives it only to a near non-guard resident that has a workplace (`WorkDuty.fit`) and feeds it each second with `setWork`.
  R9.7: a guard's experience is `Resident.guardXp` (kept apart from `xp`/`xpTrade`, so no other trade resets it, and it resets no other);
  `xp()` of a GUARD reads it. `paper/GuardArms` holds the sword (tagged with a persistent-data byte, drop chance 0): only an on-duty guard near a
  player has one, `BrainService.fit` sets or clears it each second, and every place that detaches the behaviours also calls `arms.clearAll()`.
  Gotcha: the game's idle chat (a trade gate, `lockGazeAndWalkToEachOther`) resets WALK_TARGET every tick, so `Journey` waits 2 seconds when the
  brain replaces a path within 10 ticks (`QUICK_TICKS`), rather than retrying at once; stopping the gates or clearing INTERACTION_TARGET made it worse.
- **Room to grow (R4.38) and the first mine (R4.39):** a building `Construction.propose` wanted but found no lot it may use (`mayUse`) records `planOutgrown`;
  `Construction.wantedStage` (not `PlanGenerator.stageFor`) is the stage `ensurePlan` lays out, and `planGrew` clears the call. A want gets the R4.37
  ground search only at `MAX_STAGE`. A village's first mine leaves stone out of its price (`Construction.priceFor`, `stoneFree`) and `charge` takes none.
  The rest of the growth fixes are in docs/GROWTH_BACKLOG.md.
- **Rough lots (R8.19):** `VillagePlan.LotStatus.ROUGH` is a lot the builders can level (`PlanGenerator.gradable`) that is not `buildable`; everything that means "a lot not yet built on" is `status() != FILLED`, not `== RESERVED`. `nextLot`/`candidatesFor` offer ready lots first. Plans made before R8.19 have none.
- **Ground work (R8.20):** grading is `ConstructionService.prepareGround` (free filling); rock cut and logs felled are counted and credited by `GroundWork.credit`. `Construction.queue` gives up a ROUGH lot whose `TerrainPad` is invalid (not when it merely has not been measured), so a rough lot never blocks the plan. The game's bell ("meeting" point of interest) is a found SQUARE.
- **Found buildings (R2.8):** `BuildingFinder` (core) turns points of interest (`home` beds, villager job sites by name) and farmland cells into `Found`
  buildings; `Settlement.syncFound` registers them with `registeredBy` `BuildingFinder.FOUND_BY` ("found"): they have NO sign, so anything that checks a
  building's sign (`pruneBuildings`) must skip them, and they are dropped only by the next look, when their chunk is loaded. A signed or village-built
  building within 12 blocks of the same kind stands for a find. Lots under a found building are filled (`VillagePlan.syncFound` uses a fixed 11 by 11 margin
  after the plan grows). Composters and barrels are deliberately not read.
- **`SettlementService.track()` returns null** for a world excluded by `worlds.allow/deny`
  (R1.10); callers must handle it.
- **Line endings:** working copies are CRLF (autocrlf) while the repo stores LF, so git warns
  about "LF will be replaced by CRLF". Harmless.
- The Paper module compiles on the local machine and has been run (playtest 2026-10-01: the server
  loads the plugin, a format-4 save migrated to 8, `/settlement`, talking, requests and donation
  payouts work). Most of it is still unplaytested: see docs/TESTING.md T1–T81 and the "coded, not yet played" notes in CHANGELOG.md.

## Where things stand

- Done (from ROADMAP.md, which is the source of truth): M0: R0.1, R0.2, R0.3, R0.4, R0.5, R0.6, R0.7; M1: 25 items, R1.2 to R1.30; M2: R2.1, R2.2, R2.3, R2.5, R2.6, R2.7; M3: 14 items, R3.1 to R3.14; M4: 10 items, R4.1 to R4.16; M5: R5.1, R5.5; M8: R8.1, R8.2, R8.3, R8.4. In progress: R1.7. Many other items are coded and still `Planned` because no one has played them yet.
  Save format is 29 (R9.7 added the days a resident has served as a guard, kept apart from other trades; R5.10 added the RAMPART work and a project's plan stage; R8.12 added the SAWMILL, FORGE and GRANARY building types; R4.29 added residents' days worked in their trade; R3.16 made the stores hold commodities; R5.6 added the works STREET_LIGHTS and PALISADE as project types; R4.21 added the buildings a builder has finished; R4.7 and R4.8 added construction projects, their turn and grading and the builder occupation; R8.3 added the village plan; R8.1 added the planner's decision log; R5.5 added the days of recent attacks; R5.1 added the guard occupation; R2.6 kept room for existing treasuries; R4.2 added the departure history kind; R3.5 added resident wealth; R3.4 added per-player reputation; R2.2 added bed counts; R2.1 added buildings; R3.3 added requests; R1.21 added event count/actor, R3.7 added flow, R4.14 added gender, R4.15 added departed ids; 7 dropped the nonbinary gender).
- **Next, highest value:** R1.1 (finish the playtest — the Paper layer has run only in part; scenarios
  T1–T81 in docs/TESTING.md, of which T17 (admin), T18 (worlds), T19 (backups), T26 (appearance), T27 (requests) and T28 (donation values) and T29 (tool requests) and T30 (trade prices) and T31 (donation room), T32 (building signs) and T33 (housing) T34 (miners), T35 (reputation), T36 (trades) and T37 (trade stock), T38 (wealth) and T39 (migration) and T40 (membership) T41 (shops) and T42 (treasury) T43 (bank counter) T44 (guards) T45 (planner) T46 (survey) and T47 (plan and lots) and T48 (exempt signs) are
  Paper-only). After that R1.7 and R1.12, then R1.27 (split villages). R1.24 is decided (foraging is intended).
- On a local machine, much of R1.1 can be driven from the server console (`/summon`, `/time add`,
  restarts, reading `plugins/Hamletfolk/settlements.json`); player-only steps (sneak +
  right-click, `/settlement` as a player, donate, the history book, Bedrock) need a person in game.
