# From pioneer to steward: ownership, the treasury and player menus

A design discussion, not a plan of record: nothing here is a roadmap item yet, and the feature stays locked until the foundations
below exist. It answers the question "what is the path to owning or stewarding a town, and what are the player's menus?", and
weighs a long outside proposal (the "pioneer to city-state" progression with an allocation pool) against what Hamletfolk already has.

## What the code already gives us

| Need | What exists |
|---|---|
| Who helped a village | `Settlement.reputation` per player (R3.4): donations and fulfilled requests raise it, killing a resident costs it |
| A traceable economy | `ResourceFlow` (R3.7): what each resource produced and consumed, kept per day; every ledger move goes through `Ledger` |
| A public treasury | emeralds with a limit (R2.6) that merchants fill and requests (R3.3) spend; construction costs come from the stores (R4.7) |
| What is a reserve | `SettlementSimulator.wantedLevel`: the stock a village wants (10 food a head, 3 of the rest) before it counts anything as spare |
| Explaining a shortage | `VillageBlockers` and the planner's decision log: "food 187 of the 200 it aims for" |
| Admin power over a village | the dashboard's closed action list with an audit log (R1.36, R8.23); roles planned in R1.37 |
| A leader | the elder (R6.4) and steward occupation (R6.6), both planned |
| Founder | none recorded: `Settlement` knows its name and day founded, not who founded it |

So the accounting and the reputation exist. What is missing is a recorded founder, a notion of role, and a controlled way to spend.

## The progression, made measurable

The outside proposal's five stages (pioneer, established village, steward, city-state, colonist) fit Hamletfolk well. The important
point it makes, and we agree with, is that a stage is **earned by what the village has become, not by a head count**. Concretely:

1. **Pioneer.** Anyone. Gathers, donates, fulfils requests. Reputation grows.
2. **Established village.** A village state, not a player one: no famine in the last N days, food above its aim, a registered treasury,
   at least a few buildings of each need tier. The village can say so (a history line, the dashboard).
3. **Steward.** A player whose reputation in that village passes a threshold *while the village is established*. Granted a role, not
   ownership: it can be taken back by an admin, and lapses if the player is absent for a long time (the village appoints its elder instead).
4. **City-state and colonist.** Later, and only once trade between settlements (R6.5) exists: a steward funds a new settlement from an
   allocation, and the two trade.

Reputation must be hard to farm: weight by the *value* of a donation (the R3.3 values), cap per day, and decay slowly, so dumping a
chest of dirt once does not make a steward and a long record of real help does.

## The treasury: three pools, one rule

The proposal's three pools map onto what we have:

- **Personal inventory**: the player's own. Not ours.
- **Public stores and treasury**: the ledger. The village spends it on the planner's needs first, always.
- **Development allocation**: *new*. A share of the **surplus only**, where surplus means stock above the protected reserve
  (the village's `wantedLevel` plus a famine margin, per resource).

The rule that makes this safe: **a steward never withdraws from the stores; a steward approves a project, and the project draws on the
allocation as it is built**, through the same `Construction.charge` path a village's own builders use. So a manor is paid for block by
block, can be stopped by running out of surplus, and leaves the granary alone. The dashboard's blockers already say why something is not
available ("food 187 of the 200 it aims for"); an allocation screen would show the same numbers as *reserve* and *available*.

The allocation can grow as the village invests (production, trade, storage and administrative buildings), which is the engine the proposal
describes. It must stay bounded by real production: nothing in the loop may create resources, so the existing costs (food eaten, tools
wearing out, builders' wages) are what keep it honest.

## Ownership without a popularity contest

- **Founder** is recorded when a player founds a village (R8.5) and is a fact, not a power.
- **Steward** is a role with explicit permissions: set one or two policies (a focus: food, defence or growth; the tax rate of R6.4), rank
  the village's wants, post a request, approve an allocation, name the village. These *influence* what the planner does.
- **What stays admin-only** (the dashboard's plan overrides: keep a lot empty, forbid a building, order one next, warp, resettle, set a
  job): they command the village, and a village that is commanded stops being a community with its own interests.
- Everything a steward does is written to the village's history and the audit log, and an admin can revoke the role.
- Visibility and authority stay separate: any player may see a village's public status; only a role may act.

## Player menus

`docs/GUI_PLAN.md` already compares the options. The shape that fits all of the above:

- **In game, for everyone:** talk to residents (exists), the request board (a lectern with a book of what the village asks, refreshed
  from the same data), donations (exists).
- **In game, for a steward:** a menu (a chest GUI or a book, both work for Bedrock through Geyser) with three pages: the village's
  health (the blockers), the allocation (reserve and available per resource), and the policies. The same data as the dashboard, laid out
  small.
- **In the browser, for admins and, later, stewards:** the dashboard. A player would link their account with a short code from
  `/settlement link`, so the page can tell who they are without a password of its own; viewing and acting stay separate permissions.

## Foundations to build first (all useful on their own)

1. **Every ledger movement has a source and a destination** in the record (donation, wage, construction, sale, spoilage). Mostly there; a
   gap list is the first task.
2. **A recorded founder and a contribution record** per player per village, separate from reputation (which can fall): donated value,
   requests fulfilled, public buildings paid for.
3. **Reserve and surplus** as first-class numbers (`Ledger` or a small `Reserve` class), shown on the dashboard.
4. **An established-village test** that the village can state in its history ("Foxhaven is now an established village").

Possible roadmap items, for discussion before any are added: R6.7 founder and contributions, R6.8 reserve and surplus, R6.9 the steward
role, R6.10 the allocation and player projects, R6.11 the steward's menu, R6.12 account linking for the dashboard.

## Risks to design against

- **Stripping a town** for a personal build: prevented by the surplus-only rule, and by drawing as the project is built.
- **Concurrency and duplicate spending**: everything runs on the main thread through the closed action list, with the audit log.
- **A steward who leaves**: the role lapses and the village's elder (R6.4) carries on; the allocation stops.
- **Griefing and claim-jumping**: the first player to arrive gains nothing; the role is earned and revocable.
- **Infinite resources**: no rule may add stock; only production does.
