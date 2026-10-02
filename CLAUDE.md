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
  or `VillagerListener`. Vanilla-backed jobs get a free workstation from M2 (see
  `SettlementSimulator.workstationFree`).
- **Adult enrollment draws an age** (R4.15, 12 to 51 days), so a test that enrolls adults and runs
  past about 39 days can lose them to old age. Build residents with `new Resident(..., bornDay, ...)`
  and `Settlement.addResident` to control age, or use `SettlementSimulator.withOldAgeDeaths(false)`. The lifespan scale is a static
  (`Resident.setLifespanScale`, 1 in core, 20 from the Paper config): a test that changes it must restore it.
- **The treasury has one spending so far:** merchants (R3.9) fill it and requests (R3.3) spend it
  (the reward is held aside when a request is posted); R4.8 (builder) and R5.1 (guards) are next. STONE and GOODS now have an outlet in the
  merchant. TOOLS wear out (R3.6).
- **Tool wear penalty is off** (`SettlementSimulator.toollessPenalty`, false by default): no
  occupation produces METAL until R2.3, so smiths can't make tools and a penalty would starve
  every village. Turn it on as part of R2.3; then R3.6 can be marked Done.
- **`SettlementService.track()` returns null** for a world excluded by `worlds.allow/deny`
  (R1.10); callers must handle it.
- **Line endings:** working copies are CRLF (autocrlf) while the repo stores LF, so git warns
  about "LF will be replaced by CRLF". Harmless.
- The Paper module compiles on the local machine and has been run (playtest 2026-10-01: the server
  loads the plugin, a format-4 save migrated to 8, `/settlement`, talking, requests and donation
  payouts work). Most of it is still unplaytested: see docs/TESTING.md T1-T29.

## Where things stand

- Done: M0; M1 items R1.2, R1.3, R1.4, R1.5, R1.6, R1.9, R1.10, R1.13, R1.14, R1.16, R1.17,
  R1.18, R1.19, R1.20, R1.21, R1.22, R1.23; M3 items R3.1, R3.3, R3.7, R3.9, R3.10, R3.11, R3.12, R3.13 (R3.6 partly, see above); M4 items R4.1, R4.3, R4.4, R4.5, R4.9, R4.10, R4.14, R4.15, R4.16.
  Save format is 8 (R3.3 added requests; R1.21 added event count/actor, R3.7 added flow, R4.14 added gender, R4.15 added departed ids; 7 dropped the nonbinary gender).
- **Next, highest value:** R1.1 (first playtest — the Paper layer has never run; scenarios
  T1–T31 in docs/TESTING.md, of which T17 (admin), T18 (worlds), T19 (backups), T26 (appearance), T27 (requests) and T28 (donation values) and T29 (tool requests) and T30 (trade prices) and T31 (donation room) are
  Paper-only). After that R1.8, R1.24 (needs a decision), R1.7 and R1.12, then M2 buildings.
- On a local machine, much of R1.1 can be driven from the server console (`/summon`, `/time add`,
  restarts, reading `plugins/Hamletfolk/settlements.json`); player-only steps (sneak +
  right-click, `/settlement` as a player, donate, the history book, Bedrock) need a person in game.
