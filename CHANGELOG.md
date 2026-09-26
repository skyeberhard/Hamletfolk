# Changelog

Each entry lists the roadmap items it delivers. See [ROADMAP.md](ROADMAP.md).

## Unreleased

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
