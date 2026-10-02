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
| 1 | **R3.1** Prices follow supply (#28) | Built and reviewed; held only for an unexplained 1-in-100 test failure | none | Next: find the flake, then commit |
| 2 | **R1.15** Admin and abandonment land before M2 (#52) | R1.4 and R1.5 are Done, so the condition is already met: bookkeeping only | none | Planned |
| 3 | **R3.11** Donations respect storage limits (#81) | A 64-log stack is now 256 units and is mostly wasted in a small village | none | Planned |
| 4 | **R2.1** Sign registration (#24) | Core `Building` model + registry + save (format 9); a thin sign listener. Everything in M2 builds on it | none | Planned |
| 5 | **R2.2** Housing capacity (#25) | Replaces the Paper bed count R4.1 uses with a core capacity | R2.1 | Planned |
| 6 | **R2.3** Buildings affect output (#26) | Drives `SettlementSimulator.workstationFree` from registered buildings, adds MINER, so R4.3 job assignment stops being dormant; turn on `toollessPenalty` and finish **R3.6** (#67) | R2.1 | Planned |
| 7 | **R3.4** Player reputation (#31) | Per-player standing from donations, requests and harm; feeds prices and dialogue | none | Planned |
| 8 | **R3.2** Trades feed the ledger (#29) | Core hook for trades; revisit price arbitrage (R3.1 review note) | R3.1 | Planned |
| 9 | **R3.5** Resident wealth and wages (#32) | Residents earn and spend; wealth shows in dialogue | R3.9 done | Planned |
| 10 | **R4.2** Migration (#34) | Unemployed or unhappy residents leave for a better-off settlement; core moves the record, Paper moves the villager later | none | Planned |
| 11 | **R1.8** Membership follows residents (#23) | A resident in another settlement's area for 3 days moves there | R4.2 (shares the move) | Planned |
| 12 | **R2.5, R2.6, R2.7** Storefront, treasury building, bank counter (#85, #86, #88) | Building-dependent economy rules | R2.1, R2.3 | Planned |
| 13 | **R5.1** Guards (#36) | Sustained threat makes a guard that consumes tools and food | R2.3 | Planned |
| 14 | **R6.1** Households and marriage (#39) | Builds on genders, parents, ages | none | Planned |
| 15 | **R6.4** Village leadership (#42), then **R6.6** Steward (#90) | Elder sets a policy; steward from town size | R6.1 | Planned |
| 16 | **R6.5** Trade between settlements (#43) | Surplus for shortage along routes; the merchant bridges | R3.2, R3.9 | Planned |
| 17 | **R6.2** Businesses (#40), **R6.3** Player investment (#41) | Owned buildings with wages | R2.1, R3.5, R6.1 | Planned |
| 18 | **R5.2** Defenses reduce threat (#37), **R5.4** Vault break-ins (#89) | Needs registered buildings and a treasury building | R2.1, R2.6 | Planned |
| 19 | **R7.1** Pluggable dialogue provider (#44) | A core interface; the template provider stays the default | none | Planned |
| 20 | **R1.27** Settlements anchored to a village's area (#76), core merge | Merging overlapping settlements is core; detecting generated villages is Tier 2 | none | Planned |
| 21 | **R4.6** Building templates with tiers (#56), core diff | Choosing the best affordable tier and the block diff is core | R2.1 | Planned |

## Tier 2: Paper-heavy (write without a playtest, verify in game)

Write the core part and the thin wiring now; each needs a `docs/TESTING.md` scenario and stays
"verified in core only" until played.

| Item | What is Paper-only |
|---|---|
| **R2.4** Buildings inferred from blocks (#27) | block-change analysis |
| **R4.7, R4.8** Construction projects, builder occupation (#57, #58) | placing blocks over time, NBT templates |
| **R4.11, R4.12, R4.13** Visible personality, overheard lines and mood, workstation pairing (#77, #78, #79) | villager AI memories, particles, name display |
| **R1.12** Paper-layer performance measurement (#49) | an admin command timing real ticks |
| **R1.27** (detection half) | locating a village's real area |
| **R5.3** Calls for help (#38) | chat and player interaction |
| **R7.2** Local model provider (#45) | an optional HTTP provider, off by default |

## Tier 3: needs a person in game

- **R1.1** First local playtest (#16): partly done 2026-10-01. Remaining scenarios are in `docs/TESTING.md`
  (T9–T19, T21, T22, T25, T26, T28, T29, T30, merchants).
- **R1.11** Bedrock (Geyser) playtest (#48).
- **R1.7** Match the server's version (#22): `minecraftVersion` now matches 26.2; the remaining
  condition is one session on a copy of the server world.

## Decisions waiting on you

| Question | My recommendation |
|---|---|
| **R1.24** (#66): unemployed foraging 1 food a day. Intended or removed? | Document it as intended: it is what keeps a famine village from going to zero, and R4.3 already keeps foragers foraging while food is short. |
| Default for `aging.old-age-deaths` (off) | Keep off until a playtest shows how villages hold up with a ~month-long life. |
| R2.3: turn the toolless penalty on once mines exist | Yes, in the same item, as `CLAUDE.md` already says. |

## Log

- 2026-10-02: plan written. R3.1 built and reviewed, uncommitted, waiting on a flake hunt (a
  1-in-100 `:core:test` failure not yet identified). R3.13 committed (`c86a28a`).
