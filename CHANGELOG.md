# Changelog

Each entry lists the roadmap items it delivers. See [ROADMAP.md](ROADMAP.md).

## Unreleased

- R3.12: Donations are valued by what the item is made of. Wood counts in planks: a log, wood
  block or stem is 4, a plank 1, a stick half and a bamboo stalk a quarter, so the same timber is
  worth the same in any form and a request (R3.3) pays the same for a log, four planks or eight
  sticks. A stack is valued as a whole and rounded down, so splitting a donation gains nothing.
  A tool is worth its tier in proportion to its remaining durability, down to nothing when worn
  out. `/settlement donate` no longer takes a donation worth less than one unit (a lone stick, a
  worn-out tool): it says so and keeps the items. Logs are now worth four times what they were,
  so wood donations count for more than before, and a big stack can overflow a small
  village's storage limit and be wasted (R3.11). A mushroom stem is no longer counted as timber,
  and `/settlement donate` only takes the items its credit pays for, so an odd leftover stick
  stays with the player. Reading a tool's wear is Paper-only and unverified until a playtest (T28).
- R3.3: Village requests. When a resource (food, wood, stone, metal or tools) runs short and the
  treasury can pay for it, the village posts a request for it (at least 8 units, up to double what
  it wants), shown in `/settlement` and mentioned by residents, and the history records it. The
  reward is taken out of the treasury and held when the request is posted, at twice the
  merchant's rate, so it can always be paid. `/settlement donate` of the requested resource pays
  the donor in emeralds in proportion to what they hand over, to the last one, and anything beyond
  what is asked for is an ordinary donation. A request closes when the stores recover or after
  30 days and the unpaid reward goes back to the treasury; the same resource then waits 7 days
  before asking again. At most 3 are open. Save format is now 8 (open requests); an older save
  has none. Paying the donor is Paper-only and unverified until a playtest (T27).
- R3.9: New MERCHANT occupation. A merchant makes nothing but sells the village's surplus for
  emeralds into the treasury: up to 4 whole batches a day (fewer for an elder or an unhappy
  merchant), each earning one emerald, always starting with whichever resource has the most
  surplus worth. Rates are 10 food, 6 wood, 5 stone, 2 metal, 2 goods or 1 tool per emerald.
  Stock is only sold above what the village keeps: 6 a resident of most things, 20 of tools and
  metal (smiths and wear need them), and 24 of food, which leaves 20 a head after the day's meal
  and spoilage so merchants never stop newcomers (R4.1). That is well above what R4.3 counts as
  short. An unemployed resident becomes the merchant when something can be sold, food is
  comfortable and nothing else is short, one merchant per 15 residents; a merchant with nothing
  left to sell goes back to unemployed when something is short. A merchant's child follows the
  trade only when nothing is short. The treasury has no spending yet (R3.3, R4.8, R5.1).
- R4.16: Every tracked villager now carries `hamletfolk:gender`, `hamletfolk:occupation` and
  `hamletfolk:life_stage` in its persistent data. That data stays on the server (datapacks, command
  selectors and other plugins can use it); clients never see it. New setting `appearance.villager-type` (off by default) also sets the villager's vanilla type
  from gender and life stage (female plains/snow, male desert/taiga;
  children share the adult's, jungle, savanna and swamp are never used), so a plain resource pack that redraws those
  types can reskin villagers with no client mod; turning it off restores each villager's original type. The keys and mapping are in docs/APPEARANCE.md and
  the mapping is in core with a unit test. Unverified until a playtest (T26): how clients and
  Bedrock see it.
- R4.15: Residents have an age in days and a life stage (child, adult, elder at 60 days). Elders
  produce 60% of normal output. Each resident has their own maximum age (90 to 110 days) and, with
  `aging.old-age-deaths: true` (off by default), dies of old age past it, recorded in history; the
  Paper layer then removes their villager permanently, and if it was unloaded removes it when it
  loads, so it isn't enrolled again as a stranger. It is off by default because villages no player
  visits can empty out if nothing replaces the dead. A cured zombie keeps their lifespan and gets
  at least 20 days. Anyone already grown
  when first seen (founders, arrivals) is given an age of 12 to 51 days, so no one starts old.
  `/settlement residents` shows age, and elders may talk about their years. Save format is now 6:
  `departed` ids are saved, and residents in an older save whose recorded age is beyond
  that range are rebased (never made older), so no one in an old save dies the moment it loads.
  The ages are in-game days (about 20 real minutes each): lifespans are roughly a day and a half
  of real time at the base numbers. `aging.lifespan-scale` (default 20, about a month of real time
  for a life: elder at 1200 days, death at 1800 to 2200) multiplies every age together, including
  the starting ages of the first residents; it applies on restart and to existing residents.
- R4.14: Residents have a gender (female or male), drawn when they are enrolled and saved. Given
  names come from per-gender lists (the old list split in two, with more masculine names added),
  so name and gender agree. `/settlement residents` shows pronouns, and a resident may mention a
  parent as mother or father with matching pronouns. Save format is now 7: an old save's residents
  get the gender their name belongs to, a name on no list gets one derived from their id, and a
  resident an earlier build saved as nonbinary is given one the same way. No one is renamed.
  (An earlier version of this item had a third, nonbinary gender; it was dropped as not fitting the
  setting. Choosing not to marry is left to R6.1 households, as a choice any resident can make.)
- R4.1: A settlement with a food surplus (20 food per resident in store) and a free bed gets a
  newcomer (never on the settlement's founding day), at most one every 3 days; the arrival is recorded in history like any other settler.
  The rule is in core (`SettlementSimulator.newcomerDue`, unit tested). The Paper layer counts
  beds in loaded chunks (R2.2 will replace this) and spawns the villager, so it is unverified
  until a playtest (T22).
- R4.10: A grown child (one with a parent on record) who is still unemployed takes the occupation
  the village is shortest of, preferring a parent's trade when it is also short, and the history
  records the apprenticeship. Uses R4.3's job assignment, so until M2 buildings exist only
  occupations with no vanilla profession are open. A child who claims a vanilla workstation before
  the simulation sees them grown keeps that job and is not apprenticed. (Scenario T21.)
- R4.9: A resident's worst need (food, safety or purpose) now scales their output: at or above 50 it
  costs nothing, below that it falls linearly to half output at zero. A starving or terrified
  workforce produces noticeably less, but never nothing. Food need now holds steady while at
  least half the demand is met and recovers above that (it used to recover only when everyone was
  fully fed), so a village that can feed itself at its lowest output always climbs back.
- R3.10: Each resource has a storage limit (100 plus 10 per resident, 40 per resident for food) and
  anything over it is wasted at the end of the day. Food also spoils, 2% of the stock a day. A
  surplus can now end, so the "granaries full" milestone can clear and recur. Nothing is saved for
  this, so no format change; a stockpile above its limit (e.g. from an old save or a big donation)
  is trimmed the next day it is simulated.
- R4.3: The simulation now owns a resident's occupation. An unemployed adult takes the occupation
  the village is shortest of (at most one a day). Until buildings exist (M2) only occupations with
  no vanilla profession (lumberjack) count as having a free workstation. A villager's vanilla
  profession only fills in a missing occupation (`Resident.seedOccupation`), so newborns still pick
  up a vanilla job, but `SettlementService.track()` and the career-change handler can no longer
  overwrite an assigned one.
- R1.28: Added a read-only `reviewer` subagent (Opus) in `.claude/agents/` that checks diffs
  against this repo's known traps and labels findings verified or unverified. `CLAUDE.md` says
  when to use it.
- R3.8: Donations are credited by value: storage blocks count as their contents (iron, gold,
  copper and their raw blocks, hay, melon, dried kelp, emerald) and tools by material tier.
  `/settlement donate` now needs an amount or `all`; on its own it just says what you're holding
  is worth. Gold and copper blocks, melons and dried kelp blocks are newly accepted.
- R1.26: Playtest scenario T6 now notes that a starving village gives the famine line before the
  "no metal" one, needs a smithing table for the toolsmith, and gains T6b for the metal line once fed.
- R1.25: Shared `.claude/settings.json` allows Gradle, `git add`/`git commit` and `git push` to the working branch without
  prompting, so local Claude Code runs can go unattended. `.claude/settings.local.json` is
  git-ignored for personal overrides.
- R1.19: Periodic backups. Besides the startup backup, `settlements.json` is saved and copied
  into `backups/` every `backups.interval-hours` (default 24; 0 turns it off) while the server
  runs, with the same rotation; `backups.keep` (default 5) sets how many are kept.
- R1.10: `worlds.allow` and `worlds.deny` in config.yml (names or `*` patterns, case-insensitive)
  decide where settlements are tracked. An excluded world is ignored: villagers there are not
  enrolled, `/settlement` reports no settlement, and existing settlements in it stop simulating
  but are kept. Empty lists mean every world, as before.
- R1.4: `/settlement admin list|inspect|rename|save`, gated by the new `hamletfolk.admin`
  permission (ops by default) and usable from the server console, so state can be checked
  without a player in game. Settlements can be named by name or by id prefix.
- R3.6 (partial): Tool wear. Farmers, fishers, lumberjacks and masons wear out a tool on about
  15% of working days, which gives TOOLS a sink. The slowdown and shortage record while a
  village has no tools are built and tested but switched off until R2.3 gives METAL a source
  (smiths need metal to make tools, so every village would otherwise stay toolless for good).
- R3.7: Each settlement keeps a rolling 7-day total of what it produced and consumed per
  resource (work, inputs and eating), saved with the settlement (format 4) and shown in
  `/settlement` as e.g. "food +12/day made, -15/day eaten".
- R1.23: If the world clock goes backwards (e.g. `/time set`), new residents and history entries
  use the settlement's own day, so nothing is filed before days already simulated.
- R1.22: When catch-up skips days beyond `max-catch-up-days`, the settlement's history now
  records how many were skipped ("990 days passed that no one in Oakvale wrote down."), except
  in an abandoned settlement.
- R1.21: History is bounded. A player's repeated donations within a week merge into one line
  ("Skye made 14 donations this week"), and each settlement keeps at most 500 events, dropping
  minor ones (donations, shortages, births, arrivals) before major ones (founding, deaths, cures,
  raids, famines, abandonment). Save format is now 3 (optional `count`/`actor` on events).
- R1.17: Saves are numbered when snapshotted; a slow background autosave can no longer
  overwrite a newer save (e.g. the one made at shutdown).
- R1.18: A donation triggers a save within about 2 seconds instead of waiting for the next
  autosave.
- R1.20: When an input runs short, whoever went without most recently gets first claim the
  next day, so the shortfall rotates round-robin instead of always hitting the newest worker.
- R1.16: docs/DEPLOYING.md documents the pin-and-verify process; PRODUCTION.md tracks
  which commit is live versus last verified.
- R4.4/R4.5: Added a LUMBERJACK occupation as wood's real source, and FLETCHER now
  consumes wood to produce goods (arrows) instead of producing wood directly.
- R1.13: `settlements.json` now enforces its schema version on load: an older save
  migrates automatically (format 1, from before R1.2, gets its missing `turned` field), and
  a save from a newer plugin build is refused rather than silently misread. README now
  recommends off-machine backup coverage for `plugins/Hamletfolk/`.
- R1.5: A settlement empty for 10 days straight is marked abandoned and stops being
  simulated (cheap to keep around indefinitely); its history is untouched, and it un-abandons
  the moment someone lives there again.
- R1.14: `plugin.yml` api-version corrected from `1.21` to `26.2`, matching the Minecraft
  version this build actually targets since R1.7 (confirmed against PaperMC's docs).
- R1.2: A villager infected by zombies and then cured comes back as the same person, with the
  same name, family, traits and memory of players. History records who cured them.
  `/settlement` shows how many residents are zombies who could still be cured.
- R1.3: On startup, the previous `settlements.json` is copied to `plugins/Hamletfolk/backups/`;
  the newest 5 copies are kept.
- R1.6: Performance test: 50 settlements of 50 residents simulate a day in a median 0.8 ms
  (budget: 5 ms).
- R1.7: Targets Minecraft 26.2 (the current stable Paper release) and Java 25.
- R1.9: Renamed to Hamletfolk. The plugin is now `Hamletfolk`, its data lives in
  `plugins/Hamletfolk/`, and the permission is `hamletfolk.use`.
- R0.7: `./gradlew runServer` starts a local Paper test server with the plugin installed.
- Build: Gradle 9.8.0; the Minecraft version is now set in one place (`minecraftVersion`).

## 0.1.0

- R0.1: Residents with persistent names, families, traits and needs; settlements with a shared ledger.
- R0.2: Daily production and consumption, famine, shortages, population milestones.
- R0.3: Settlement history book (`/settlement history`).
- R0.4: Residents talk about what's actually happening (sneak + right-click).
- R0.5: Paper plugin: villager tracking, deaths, births, raids, donations, `/settlement`.
- R0.6: CI builds the plugin on every push.
