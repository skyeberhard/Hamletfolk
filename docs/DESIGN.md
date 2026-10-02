# Design

## Goal

A living-world simulation for vanilla-style Paper servers. MineColonies asks "how do I
build and manage a colony?" This asks "what happens when I leave these people alone?"

The player is not the ruler. They are an unusually capable individual who can trade,
donate, defend and (later) invest, and the village responds to what they do.

## Principles

1. **Simulation first.** Villagers are the visible part of a data model. The
   simulation runs whether or not a villager's chunk is loaded, and costs the same
   either way.
2. **Server-side only.** No client mods. Everything is shown through chat, books,
   names, and vanilla screens, so Java and Bedrock (Geyser) players get the same thing.
3. **Legible.** A simulation the player can't see reads as random. Every important
   change is written into the history and reflected in what residents say.
4. **Deterministic.** Given a settlement and a day, the simulation produces the same
   result. This makes it testable and makes bugs reproducible.
5. **No LLM dependency.** Dialogue comes from templates over real state. An optional
   AI layer may come later, reading the same state.

## Model

```
SettlementRegistry
 └── Settlement            name, world, center, founded day, threat
      ├── Ledger           FOOD WOOD STONE METAL TOOLS GOODS, treasury
      ├── Resident*        name, family, traits, needs, occupation, parents,
      │                    familiarity with each player
      ├── History          (day, kind, text) — permanent
      └── Conditions       famine, shortages, milestones reached
```

- A resident's id is its villager entity's UUID.
- A villager belongs to the nearest settlement within `settlement-radius` blocks.
  If there's none, it founds one.
- **Foraging is intended.** An unemployed adult produces about 1 food a day (`Occupation.UNEMPLOYED`).
  It is the floor under a small village: without it a village with no farmer would starve to zero
  while waiting for a job, and job assignment (R4.3) deliberately leaves foragers foraging while
  food is the scarcest resource. Real food comes from farms.
- The simulator advances one day at a time: work → eat → threat decays → needs update →
  conditions change, and each condition change writes one history entry.

## Roadmap

See [ROADMAP.md](../ROADMAP.md).
