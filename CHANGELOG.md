# Changelog

Each entry lists the roadmap items it delivers. See [ROADMAP.md](ROADMAP.md).

## Unreleased

- R1.23: If the world clock goes backwards (e.g. `/time set`), new residents and history entries
  use the settlement's own day, so nothing is filed before days already simulated.
- R1.22: When catch-up skips days beyond `max-catch-up-days`, the settlement's history now
  records how many were skipped ("990 days passed unrecorded while Oakvale went unvisited.").
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
