# Work plan

The queue for unattended work: what to build next and in what order, how each item is finished, and
when to stop and ask. [ROADMAP.md](../ROADMAP.md) stays the source of truth for *what* each item is;
this file says *which one next* and *how*. Update the status column and the log at the bottom as items
land, in the same commit.

## The loop (one item at a time)

1. **Pick** the first `Next` item in Tier 1 whose dependencies are Done. Read its issue
   (`gh issue view N`), not just the roadmap row.
2. **Core first.** Put the rule in `core/` with unit tests; keep `paper/` to wiring. Anything that
   can be expressed without Bukkit types belongs in `core`.
3. **Tests that are stable.** Seeded or fixed inputs, no assertions on a single day's output,
   residents built with a far-future `bornDay` when a test runs past ~39 days (aging). Then 40+
   repeated `./gradlew :core:test --rerun-tasks` runs: **commit only if every run passes.** If one
   fails, find it (loop and capture) before committing; never commit past a failure.
4. **Review** with the `reviewer` agent before committing any Paper-layer, save-format or
   simulation-rule change. Fix the findings that are verified; say why for any set aside.
5. **Bookkeeping in the same commit:** ROADMAP row to `Done` with text identical to the issue's
   "Done when" (reword both together if the spec was wrong), a CHANGELOG line, a `docs/TESTING.md`
   scenario naming the ID if anything Paper-side is unverified, `CLAUDE.md` "Where things stand",
   save format bump + `migrate()` step + old-format test if anything saved changed.
6. **Commit and push** to `claude/minecraft-npc-settlement-mod-h6nhem` as
   `R<ID>: <what>` ending `Closes #<issue>`. Then record progress here and in memory.
7. Next item.

## Stop and ask only when

- an item says **needs a decision** (listed below) or the issue text is ambiguous in a way that
  changes the design;
- the work **needs a person in game** (Tier 3), or a verification only a playtest can give;
- the **server is running** (`paper/run`): do not run Gradle then (lock contention); read
  `paper/run/logs/latest.log` and `plugins/Hamletfolk/settlements.json` read-only;
- an action is outside the allowlist in `.claude/settings.local.json` (force-push, history rewrite,
  deleting branches or issues, closing issues by hand, anything outside this repo and its scratchpad);
- a change would **loosen a rule** already decided (vanilla-only clients, ledger as the single
  source of truth, the player is not the ruler: see [DESIGN.md](DESIGN.md)).

## Tier 1: core first (no playtest needed to build and fully test)

Dependency-ordered. `Next` is where to start.

| # | Item | Why now | Depends on | Status |
|---|---|---|---|---|
| 1 | **R3.1** Prices follow supply (#28) | Committed `46a5841`; unplaytested (T30) | none | Done |
| 2 | **R1.15** Admin and abandonment land before M2 (#52) | R1.4 and R1.5 are Done, so the condition is already met: bookkeeping only | none | Done |
| 3 | **R3.11** Donations respect storage limits (#81) | A 64-log stack is now 256 units and is mostly wasted in a small village | none | Done |
| 4 | **R2.1** Sign registration (#24) | Core `Building` model + registry + save (format 9); a thin sign listener. Everything in M2 builds on it | none | Done |
| 5 | **R2.2** Housing capacity (#25) | Replaces the Paper bed count R4.1 uses with a core capacity | R2.1 | Done |
| 6 | **R2.3** Buildings affect output (#26) | Drives `SettlementSimulator.workstationFree` from registered buildings, adds MINER, so R4.3 job assignment stops being dormant; turn on `toollessPenalty` and finish **R3.6** (#67) | R2.1 | Done |
| 7 | **R3.4** Player reputation (#31) | Per-player standing from donations, requests and harm; feeds prices and dialogue | none | Done |
| 8 | **R3.2** Trades feed the ledger (#29) | Core hook for trades; revisit price arbitrage (R3.1 review note) | R3.1 | Done |
| 9 | **R3.5** Resident wealth and wages (#32) | Residents earn and spend; wealth shows in dialogue | R3.9 done | Done |
| 10 | **R4.2** Migration (#34) | Unemployed or unhappy residents leave for a better-off settlement; core moves the record, Paper moves the villager later | none | Done |
| 11 | **R1.8** Membership follows residents (#23) | A resident in another settlement's area for 3 days moves there | R4.2 (shares the move) | Done |
| 12 | **R2.5, R2.6, R2.7** Storefront, treasury building, bank counter (#85, #86, #88) | Building-dependent economy rules | R2.1, R2.3 | Done |
| 13 | **R5.1** Guards (#36) | Sustained threat makes a guard that consumes tools and food | R2.3 | Done |
| 13b | **R5.5** Guards respond to a pattern of attacks (#109) | Earlier, scaled response from recent attacks; the playtest showed R5.1 waits too long. Needs no brain code | R5.1 | Done |
| 14 | **R6.1** Households and marriage (#39) | Builds on genders, parents, ages | none | Planned |
| 15 | **R6.4** Village leadership (#42), then **R6.6** Steward (#90) | Elder sets a policy; steward from town size | R6.1 | Planned |
| 16 | **R6.5** Trade between settlements (#43) | Surplus for shortage along routes; the merchant bridges | R3.2, R3.9 | Planned |
| 17 | **R6.2** Businesses (#40), **R6.3** Player investment (#41) | Owned buildings with wages | R2.1, R3.5, R6.1 | Planned |
| 18 | **R5.2** Defenses reduce threat (#37), **R5.4** Vault break-ins (#89) | Needs registered buildings and a treasury building | R2.1, R2.6 | Planned |
| 19 | **R7.1** Pluggable dialogue provider (#44) | A core interface; the template provider stays the default | none | Planned |
| 20 | **R1.27** Settlements anchored to a village's area (#76), core merge | Merging overlapping settlements is core; detecting generated villages is Tier 2 | none | Planned |
| 19b | **R8.1** Village planner and decision log (#94) | The decision layer; works on today's adopted villages and unblocks the rest of M8 | R2.3 | Planned |
| 19c | **R8.2** Site scoring (#95), **R8.3** Road graph and lots (#96), **R8.4** Terrain pads (#97) | Pure functions on sampled grids and heightmaps, fully testable without a server | none | Planned |
| 19d | **R8.5** Players found villages (#98), core rules; **R8.9** Stages and failure (#102) | Founding rules (cap, spacing, kit, validation) and the stall-decline-abandon path | R8.1, R2.1 | Planned |
| 21 | **R4.6** Building templates with tiers (#56), core catalog and diff | The catalog, tier choice, materials from a block palette and the block diff are core (see "Template library" below) | R2.1 | Planned |

## Tier 2: Paper-heavy (write without a playtest, verify in game)

Write the core part and the thin wiring now; each needs a `docs/TESTING.md` scenario and stays
"verified in core only" until played.

| Item | What is Paper-only |
|---|---|
| **R2.4** Buildings inferred from blocks (#27) | block-change analysis |
| **R4.7, R4.8** Construction projects, builder occupation (#57, #58) | placing blocks over time, NBT templates |
| **R4.11, R4.12, R4.13** Visible personality, overheard lines and mood, workstation pairing (#77, #78, #79) | villager AI memories, particles, name display |
| **R4.17** Residents visibly work at their workplace (#104) | choosing who works (core, tested); walking to a registered building and animating (Paper), after R4.13 |
| **R1.12** Paper-layer performance measurement (#49) | an admin command timing real ticks |
| **R1.27** (detection half) | locating a village's real area |
| **R5.3** Calls for help (#38) | chat and player interaction |
| **R7.2** Local model provider (#45) | an optional HTTP provider, off by default |
| **R8.6** Place planned buildings over time (#99) | structure placement in slices, async chunk loading |
| **R8.7** Village mode switch and vanilla suppression (#100) | the datapack and site picking; verify the portal path first |
| **R8.8** Ruins instead of vanishing (#101) | swapping buildings for ruined pieces; the decay choice itself is core |
| **R9.1, R9.2, R9.3** Brain module, its tools, guards that fight (#106, #107, #108) | the isolated internals module with its kill switch and self-check; see [SMART_VILLAGERS.md](SMART_VILLAGERS.md). Start with the R9.1 spike |
| **R9.4, R9.5** Behaviour framework, pathing service (#111, #112) | per-behaviour switches and one shared path-follower, before the behaviours that walk |
| **R4.18** Generated gap buildings and a capture command (#113) | generator in core (tested), capture command in Paper |
| **R5.6** Villages prepare defenses before danger peaks (#110) | needs construction projects (R4.7, R4.8) |

## Tier 3: needs a person in game

- **R1.1** First local playtest (#16): partly done 2026-10-01. Remaining scenarios are in `docs/TESTING.md`
  (T9–T19, T21, T22, T25, T26, T28, T29, T30, merchants).
- **R1.11** Bedrock (Geyser) playtest (#48).
- **R1.7** Match the server's version (#22): `minecraftVersion` now matches 26.2; the remaining
  condition is one session on a copy of the server world.

## Village planning (M8)

The design is in [VILLAGE_PLANNING.md](VILLAGE_PLANNING.md). Build the core pieces first (planner,
site scoring, road graph and lots, terrain pads, founding rules), one biome set and two stages, behind
`village-mode`; the Paper placement and the datapack come last.

## Template library (R4.6, R4.7, R4.8)

Settled 2026-10-02. The village structure library is mostly already in the game.

- **Source of templates.** The 26.2 server jar ships about 480 village structure files across five
  biomes (plains, desert, savanna, snowy, taiga), by role (armorer, butcher, cartographer, fisher,
  fletcher, library, mason, shepherd, farms, animal pens, small/medium/big houses, streets, town
  centers) plus ruined "zombie" variants. The catalog reuses these; only gaps are authored.
- **Catalog (core, tested).** Building type × biome × tier → template key, an ordered ladder per type
  (for example small house → medium house → big house, farm → large farm). A biome with no big house
  has a shorter ladder, written down as a documented gap, not an error. The catalog lives as data in
  `core` resources so it can be validated without a server.
- **Cost.** A tier's materials come from the blocks in its template (a block palette mapped to ledger
  resources with `ResourceMapper`); the build diff is template blocks minus what is already placed.
- **Biome.** A settlement's biome is its village's type, plains when unknown.
- **Gaps are authored.** Mine, guard post, shop, bank/treasury, storage, and any tier vanilla lacks.
  Author once in the plains palette; each other biome gets its materials by block substitution at
  placement (oak→acacia/spruce/sandstone/snow), so it is one authoring job, not five.
- **Paper side (Tier 2, unverified).** A startup check that every catalog key exists on the server
  (log the missing ones), and placing a template through the server's structure manager over time (R4.8).
- **Ruined variants** (the "zombie" pieces) are reserved for abandonment and disaster states.
- v1 scope: a house ladder from vanilla pieces, to prove that path, and one authored building (mine).
- **No hand-building needed (R4.18).** The gaps are *generated* in core (a plain, serviceable layout per building, plains
  palette, other biomes by substitution), and `/settlement admin capture <name>` saves anything an admin has built as a
  template that replaces the generated one. Layout (which lot, which road) is code too (R8.2 to R8.4). Construction can
  start on adopted vanilla villages, adding houses and gap buildings next to what is there, without waiting for M8.
- The checklist of what vanilla ships per biome and what still has to be built is
  [BUILDING_LIBRARY.md](BUILDING_LIBRARY.md): tick items off there as templates are authored.

## Decisions

Settled on 2026-10-02:

- **R1.24:** unemployed foraging (1 food a day) is **intended** and documented (README, DESIGN.md, dialogue).
- **`aging.old-age-deaths`** stays **off** until a playtest shows how villages hold up with a
  month-long life.
- **R2.3** turns the toolless penalty on once mines exist, in the same item, and finishes R3.6.

## Log

- 2026-10-02: plan written. Decisions recorded (foraging intended, old-age deaths off, toolless penalty with
  mines). R3.13 (`c86a28a`), R3.1 (`46a5841`), R1.24 (`22bd114`) committed. The "1-in-300" test failure
  was `toollessGatherersProduceLess` comparing two independently random villages: fixed (`3af668e`).
  R1.15 closed. R4.6 reworded for per-biome template sets (`1182f81`). R3.11 (`ab82bb6`) and R2.1
  (`2d5af13`) committed. M8 (plan-owned villages, #93, R8.1 to R8.9) added from the design notes.
  R2.2 (`6e044ef`), R2.3 (`55d880b`) and R3.4 and R3.2 done. R3.14 (#103, villagers sell only what the village can spare, seeded founding stores) added and done. R3.5 done (resident wealth, save format 12). R4.2 done (migration, no save change). R1.8 done (membership follows residents, no save change). R2.5, R2.6 and R2.7 done (shops; treasury limit, save format 14; bank counter). R5.1 done (guards, save format 15). R5.5 done (earlier guard response, save format 16). Next: village planning and construction (M8 core pieces, R4.6, R4.18), then the R9.1 spike. M9 (smarter villagers, #105) added from the playtest: a raid killed Mossmoor and guards could not fight.
