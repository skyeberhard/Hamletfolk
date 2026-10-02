# Village planning (M8)

The design for villages the plugin plans, agreed 2026-10-02 from the planning notes. The items are
[M8 in the roadmap](../ROADMAP.md); the order they are built in is in [WORKPLAN.md](WORKPLAN.md).
Nothing here changes today's behaviour until `village-mode` is set to `plugin-owned` (R8.7).

## Hard constraints

- **Server-side only.** Clients stay vanilla. A server resource pack and cosmetic-only client mods
  are fine; no logic in a client mod. (See [APPEARANCE.md](APPEARANCE.md).)
- A datapack only affects chunks not yet generated, which suits fresh portal worlds. Old worlds are
  left alone: no converting vanilla villages in place.
- **Verify first:** that a datapack applies to worlds created through the portal world-creation
  path. Everything in plugin-owned mode depends on it (R8.7).

## Two modes

| `village-mode` | Villages come from | Status |
|---|---|---|
| `adopt-vanilla` (default) | Vanilla generation; the plugin adopts the villagers it finds | Today |
| `plugin-owned` | The plugin: sites, roads, lots and buildings; a datapack empties the vanilla village structure set | M8, behind the switch |

Plugin-owned mode loses vanilla's village side-effects (spawning, village explorer maps), so it ships
only once the engine is proven, and the switch means nothing existing breaks meanwhile.

## The engine, in the order it is built

Each piece is a pure function in `core` where it can be, so it is tested without a server.

1. **Planner and decision log (R8.1).** One planner per village, run once per simulated day after
   production and consumption. Tiers: food → shelter → safety → trade and growth, with hysteresis
   (met at 100%, slips at 70%). It finds the binding constraint by walking a production dependency
   graph (tools ← metal ← miner ← mine lot) back from the unmet demand, then acts: reserve a lot,
   open a job slot, or flag an import. When lower tiers are met it looks for unexploited resources.
   Deterministic, and **every decision is logged with its reason**. Villagers do not decide for
   themselves; their behaviour dramatises the planner's decisions and only runs near players.
2. **Site scoring (R8.2).** Resources decide *where*, biome decides *how it looks*. Weighted scores
   (water, lumber, farmland, livestock, stone and ore) with a minimum threshold, not hard gates: a
   village missing a resource imports it. Sampled on a coarse grid using computed biome lookups so
   no chunks are generated just to be rejected.
3. **Road graph and lots (R8.3).** At founding, store a plan: a main square, a street spine,
   branches and reserved lots, each tied to a building type, a biome set and a stage. Drop lots over
   the slope limit at founding. Growth fills the next lot. **One biome set and two stages first.**
4. **Terrain pads (R8.4).** Per lot, not a village-wide plateau: target height is the median of the
   footprint; cut above it, fill below with biome blocks, a foundation skirt to solid ground, blended
   edges. Roads step at most one block, with slabs and stairs. Computed and applied when the building
   is built, not at founding.
5. **Founding (R8.5).** See below.
6. **Placement over time (R8.6, Paper).** Templates (R4.6) through the structure manager; main-thread
   edits in slices of a few thousand blocks a tick, physics off, chunks loaded asynchronously first.
7. **Mode switch and datapack (R8.7).** Last, once the rest is proven.

## Founding a village (R8.5)

A player founds a village by placing together:

- a **bell** (the village centre),
- a **bed under a roof** (the founder's home),
- a **sign** reading `[Village]`, the name on its second line,

and handing over a **starter kit** of food, wood and stone, which goes into the stores.

- **The first resident.** A villager standing nearby who is not already in a settlement becomes the
  founder; if there is none, a traveller arrives and settles in the bed.
- **From there:** the founder, the stores and the planner develop the village. Newcomers arrive on a
  food surplus and a free bed (R4.1), the planner reserves the next lots, the builder (R4.8) fills
  them from the ledger. The starter kit's food is what lets the first newcomer arrive.
- **Guardrails:** a cap on villages per world (default 3, counting player-founded ones), a minimum
  spacing between villages, the kit, and a clear message when any is not met.
- **Survey item** (a book): reports the site profile before committing. An informed choice, not
  hidden rules.
- **The score is never a gate.** Players may found anywhere and live with the consequences; the
  score sets the starting conditions (growth rate, food balance, imports).
- **Autopilot.** If the founder walks away the planner keeps running at a slow default pace, so
  administering a village is an optimisation layer, not a chore.

## Failure (R8.8, R8.9)

- Consequences are mechanical: low farmland stalls growth and people leave; low lumber or stone
  leaves lots unfilled until supplied; low water caps the village's stage.
- Path: **stalled → declining → abandoned**. A village may plateau at hamlet size rather than
  collapse, and abandonment stays reversible (R1.5's "resettled").
- **Ruins, not a wipe.** On abandonment buildings decay into the game's ruined ("zombie") village
  pieces where they exist and are otherwise left as they are. Nothing a player built is removed.
- Declined and abandoned villages cost almost nothing to simulate.

## Not doing (yet)

- **Villagers walking the roads** by A* over the road graph. Vanilla villagers move through their
  brain (schedules and memories), not the goal system the Mob Goals API changes, so a custom goal may
  be ignored and per-tick waypoints would fight the brain. Prefer real path blocks, and steering via
  job-site, home and meeting-point memories (R4.11). Listed under ideas in the roadmap.

## Settled and open

| Question | Answer |
|---|---|
| Does the ledger update on a timer or on events? | Both: simulated one day at a time on a timer with catch-up for unloaded villages; donations, deaths and raids change state directly. The planner runs once per simulated day. |
| Failure severity | Plateau, then decline, then ruins after long abandonment; builds are never wiped. |
| Village cap and founding cost | Default cap 3 per world (configurable), minimum spacing, and the starter kit above. Exact numbers are tuned in play. |
| Datapack through the portal path | **Open**: verify first (R8.7). |
