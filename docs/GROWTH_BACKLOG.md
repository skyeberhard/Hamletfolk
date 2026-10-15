# Growth backlog

Work found by the log review of 2026-10-15: the live server ran ten villages unattended at 20x for about
29 hours (village days 2,600 to 2,870), and no village gained a person in over 1,500 village days. This file
gives each fix its evidence, an approach and the traps to watch, so it can be worked one item at a time.
ROADMAP.md holds the "Done when" of each item and stays the source of truth; this file only holds notes.

R4.38 (the plan grows when it is full) and R4.39 (a first mine costs no stone) are already built.

## How to work an item

Follow CLAUDE.md and the loop in docs/WORKPLAN.md:

- One item per commit, the message starting with its ID. The GitHub issues are not created yet (no GitHub
  access in the sessions that wrote them): create the issue from the ROADMAP row text, word for word, before
  starting, parented under the milestone issue named in the row, and put its number in the row.
- Use `Refs #n` in the commit, never `Closes #n`: the row stays `Planned` until it has been seen in game.
- Logic goes in `core` with a unit test; `paper` stays thin. Add a `docs/TESTING.md` scenario (next free number
  is T94) and a CHANGELOG line under Unreleased in the same commit.
- Run `./gradlew.bat :core:test`, and `:paper:compileJava :brain:compileJava` for Paper changes. Never run a full
  `build` while the user's server in `paper/run` is up (check the end of `paper/run/logs/latest.log`).
- Run the `reviewer` agent on any simulation-rule, save-format or Paper change before committing, and say why
  for any finding you set aside.
- A simulation test asserts on output over several days, never one day (see the CLAUDE.md gotchas).
- The live save to check a rule against is `paper/run/plugins/Hamletfolk/settlements.json` (read only). A
  short Python script that prints each village's stock, jobs, conditions and last history lines is the fastest
  way to see what a rule would change.

## Suggested order

1. **R4.41** food surplus, then **R4.42** tools first: they decide whether a village can grow at all.
2. **R2.8** adopted buildings: gives every vanilla village a true starting point (beds, smithy, farms).
3. **R4.43** stuck projects, **R4.40** requests, **R3.18** selling the surplus.
4. **R4.44** quieter history, **R1.33** saving on a locked file.
5. The appearance items **R4.45**, **R4.46**, **R4.47**, after the growth items are played.

## The items

### R4.41 A village grows a food surplus

Evidence: Hollowstead makes 10 food a day and eats 10, and has had 120 one-day famines; its farm has 14 free
places, but its other three people are a lumberjack, a miner and a toolsmith, each the last of a supplying
trade, so `SettlementSimulator.spareWorker` never moves them. Testford makes 9 and eats 9. With nothing left
over, `newcomerDue` (food per head) and `Births` (bread) never fire.

Approach:
- Read `SettlementSimulator.cover`, `staffShortages`, `spareWorker` and `onlySupplier`. Find where "food is
  short" is decided and add the aim: food made over the last few days (the `flow` record) should be at least
  1.25 times what was eaten. Keep it a named constant.
- A supplier is resting when the `resting:<OCCUPATION>` condition is today (R4.24). Let `spareWorker` take a
  resting supplier for a food place even if it is the only one (`onlySupplier`), and only for food.
- Make sure the supplier rule takes the trade back up when stock falls below the wanted level, or the village
  will run out of wood. `staffSuppliers` should already do this; write a test that proves it over 30 days.

Watch for:
- Oscillation: a worker moved to the farm, then back the next day. `MOVE_COOLDOWN_DAYS` and `noteMoved` exist
  for this; use them.
- Pinned residents, guards, merchants, elders and a builder at work are never moved (R4.35 rules).
- `SimulationPerformanceTest`: anything called for every resident every day must not build streams or copies.

### R4.42 Tools come first when tools are what is short

Evidence: Testford has 98 iron, coal, a smithy and no toolsmith, has been in famine since day 1,261, and its
`shortage:tools` dates from day 333. `staffShortages` tries the food target first, so a smith is never chosen
while food is short; without tools every worker makes less, so food stays short.

Approach: in `staffShortages`, when the village is in a famine, has no tools in store, `smithCanWork` and a free
smithy place, try the toolsmith before the food target. Keep the existing guard that the last food worker is not
moved (the smith should come from a jobless adult, a resting supplier or an idle builder first).

Watch for: Testford's builder Dara is pinned as BUILDER (`pinned:29fe824e...`), a leftover from testing. Unpin
her in game (`/settlement admin job unpin` while looking at her); do not change the pin rules for it.

### R2.8 An adopted village's buildings are found

Evidence: Hollowgate and Oakford have no registered buildings at all, though they are vanilla villages with
houses, beds and fields. Oakford found no ground for a farm within 64 blocks and starves, with farmland in the
village. The plan lays lots over vanilla buildings (they are only dropped when a lot sits on a roof and reads as
a slope). The user saw vanilla smithies and houses that were never counted.

Approach:
- Core: a pure `BuildingFinder` (or similar) that takes what the Paper layer read (job-site blocks with
  position and whether they are roofed, bed positions, farmland cells) and returns found buildings: a house per
  cluster of beds under a roof, a work building per roofed job-site block (the table from vanilla job-site block
  to `BuildingType` belongs in core with a test), a farm per connected field of farmland of at least a set size.
  Test it with hand-built inputs.
- Plan: mark lots that overlap a found building FILLED (`VillagePlan.fill`), so nothing is built over it.
  Do this when the plan is made and when the stage grows (R4.38), as new lots can land on old buildings.
- Paper: read the blocks when the plan's ground is already loaded (`ensurePlan` holds it), never by loading
  chunks just for this. Beds are not block entities in 26.2: use the POI API as `/settlement beds` does
  (`World.locateAllPoiInRange` with `PoiTypes.HOME`); job sites have POI types too, which is cheaper than a block
  scan. Farmland has no POI: scan only the plan's area, once, off the hot path.
- `Building` records need a source saying "found" so `/settlement buildings` can list them and a later check
  can drop one whose blocks are gone. That is probably a save-format change: bump
  `SettlementCodec.FORMAT_VERSION` and add a `migrate()` step with a test (CLAUDE.md).

Watch for:
- Never register what an `[Exempt]` sign protects (R1.30), and never take over a player's own builds: a
  player's signed building wins over a found one at the same place.
- R2.4 (buildings inferred from blocks, when blocks change) is the continuous version of this; R2.8 is the
  one-off at adoption. Keep the matching rules shared so R2.4 can reuse them.
- The game's own claim on a job site (a villager's job memory) is separate from ours; the simulation owns
  occupations (R4.3). Finding a smithy adds places; it does not change anyone's job directly.

### R4.43 A project nobody can build does not hold up the rest

Evidence: Hollowgate (4 people) queued a palisade on day 2,541 and it was still queued at day 2,866. Its two
farmers are its only food workers, the toolsmith and lumberjack are the last of their trades, so nobody can be
drafted; and because a village has one open project at a time, its mine, shop and every other building wait.

Approach: in `Construction` (the queued-project checks near `dropStaleQueuedProject`), cancel a QUEUED project
that has had no builder for 20 days, with a history line, and for works rely on `WORKS_RETRY_DAYS` so it is not
asked for again at once. Let the draft take a resting supplier (see R4.41) before giving up.

Watch for: a project cancelled and re-queued every 20 days forever. Test that the retry waits, and that a
village that can staff it does so instead of cancelling.

### R4.40 A village with an empty treasury can still ask for help

Evidence: 7 of 10 treasuries are at 0. `SettlementSimulator.updateRequests` sets the reward aside first
(`spendTreasury`) and skips the request when it cannot, so Redwick, which needed 2 stone for 2,000 days, never
told a player. Requests also only follow stock below the wanted level, not what construction lacks.

Approach:
- Allow a request with a reward of 0 emeralds when the treasury cannot pay; the payout then gives reputation
  (R3.4) and a history line. `Request` already has the fields for reward and paid.
- Add the materials of `Construction.lacking` to what can be requested, sized from the shortfall the
  "cannot afford" line already works out.
- Update the request text in `Dialogue` and `/settlement` so "will pay nothing but thanks" reads naturally.

Watch for: R3.3 rules on cooldowns and expiry, the treasury limit (R2.6), and donation payouts (R3.11). A
request with no reward must not later claim emeralds when the treasury fills; decide and test which happens.

### R3.18 Full stores are sold to passing traders

Evidence: almost every village sits at its storage limit (stock of 150 to 220 in every material) with
gatherers resting, and the cap throws the rest away (`spoilAndCap`). Only Mossmoor, the one village with a shop
and a merchant, has money.

Approach: in core, keep what `spoilAndCap` would discard (not spoiled food) as a weekly "for sale" amount per
resource in conditions, and once a week turn it into emeralds at the R3.1 price, into the treasury up to its
limit. A shop and merchant raise the price a little. History line with the amounts.

Watch for: money from nothing can undo the economy's balance. Keep the rate low (below what a merchant earns)
and look at how fast treasuries fill in a 100-day simulated test. Respect `TREASURY_LEGACY` and the limit.

### R4.44 The history says a repeated thing once

Evidence: Elderbrook has 498 history entries, nearly all "wants a ... but cannot afford the plainest one yet"
every 5 days; Oakford repeats "looked for ground for a farm and found none" every 10 days; Testford posts and
lapses the same food request every 37 days; decision logs repeat the same line every 12 days.

Approach: `Construction.explainWhyNot` already rate-limits with `constructionBlocked:<TYPE>`; also skip the
line when its text is the same as the last one for that type and fewer than 30 days have passed. For the
decision log, find where `Planner` decisions are appended and merge a repeat into the previous entry (first and
last day). Check the saved shape of decisions before changing it; if it changes, it is a save-format change.

### R1.33 Saving survives a locked file

Evidence: on 2026-10-13 at 17:40 a save failed with `AccessDeniedException` moving `settlements.json.tmp`
over `settlements.json` (`SettlementStore.write`, the `Files.move` with `ATOMIC_MOVE`). The repo, and so
`paper/run`, is inside OneDrive, which holds files open while syncing. The next autosave worked.

Approach: retry the move a few times with a short wait on `AccessDeniedException` and log once at WARNING if
it still fails (the next autosave tries again). Optionally say in docs/DEPLOYING.md that the server folder should
not be in a synced folder. The retry rule (attempts, waits) can be a small core helper next to `SaveSequence`
with a test; the file move stays in Paper.

### R4.45 A villager's clothes show its real work

Context: the simulation owns occupations (R4.3) but leaves the vanilla profession alone, so a villager the
simulation made a farmer can still wear a weaponsmith's apron (Oakford has two). Any resource-pack clothing
(R4.46) would show the wrong job.

Approach: a table in core from `Occupation` to a vanilla profession name (or none), with a test, documented in
docs/APPEARANCE.md. The Paper layer applies it behind `appearance.profession-follows-occupation` (off by
default), next to the R4.16 villager-type code in `SettlementService`.

Watch for:
- Vanilla resets a villager's trades when its profession changes, and locks the profession once a player has
  traded with it (it has experience). Never change a locked villager; check how 26.2 exposes this with javap
  before relying on any method.
- The game also changes professions itself when a villager claims or loses a job site; setting it once is not
  enough, and fighting the game every tick is wrong. Decide when to apply it (on enrolment, on a job change, on
  chunk load) and test it on the console test server described in the memory notes, never on the live world.
- Occupations with no vanilla match (lumberjack, miner, builder, guard, merchant, mason, ...) need a decision
  for the table; the nearest look or none (NONE shows the plain robe).

### R4.46 Villager Uplift resource pack, first version

Notes: `Resource Packs/VillagerUplift/README.md` has the plan (phase 1). Read the pack format and the villager
texture paths from the 26.2 client jar rather than from memory. Do not redistribute extracted vanilla textures
unchanged. This item needs a person to look at it in game; a session can only prepare the pack.

### R4.47 A client-visible appearance profile

Notes: the seven villager types run out after gender and age (R4.16 uses four). Before any art, check in 26.2
whether a villager draws an item in its head slot (the guard's sword in `GuardArms` shows a held item works) and
whether an item's model can be chosen by a component a pack reads. If it does, a hat or hood item per profile
gives region, climate and status without a client mod; it must never drop (drop chance 0, as `GuardArms`
does) and must be tagged so it is removed when the setting is off.
