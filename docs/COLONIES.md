# Daughter villages (M10)

A village that has grown as far as its plan goes sends a party of its own people to found another, and the two stay kin. This page is the
design: what already exists, what is new, the order it is built in, and the numbers. Roadmap items are R10.1 onward in [ROADMAP.md](../ROADMAP.md).

**State today:** the rule is built and tested (R10.1), `/settlement admin colonize [name]` reports on it (R10.2) and `... site` names the best place
for a daughter (R10.3). Nothing founds a village yet and nothing happens by itself.

## What the game already gives us

| Need | What exists |
|---|---|
| Make a village from nothing | Founding by an admin (R8.10) or a player (R8.5): a settlement, founders, a kit, a plan of streets and lots |
| Rate a place | `SiteSurvey`: water, trees, stone and farmland within reach, biome and the surface where it is loaded (R8.24) |
| Move real people between villages | `SettlementRegistry.transfer` and the migration mover (R4.2): the resident changes village on paper, then the Minecraft layer brings their villager over; ids, names, ages, experience and buildings finished all stay |
| Trade between villages | Caravans between villages in one world within 800 blocks (R6.5) |
| A sign that a village is full | `planOutgrown` (R4.38), free beds |
| Keep off ground that is not ours | Exempt zones, WorldGuard (R1.52), `worlds.allow` |
| Leave ruins when a village dies | R8.8 |

## What is new

### The rule (R10.1, built)

`Colonies` in `core`. Everything is a setting under `colonies:` in the config; the defaults are in the table.

| | Default | Why |
|---|---|---|
| Village size to send a party | 30 people | the plan's second stage; enough hands to spare |
| People it must keep | 20 | the parent must not be gutted |
| Party | 4 to 8 people | a founding household or two |
| Between parties from one village | 90 days | stops a runaway |
| Villages in a world | 12 | the real brake: exponential growth otherwise |
| Spacing | 300 blocks | used when the site is chosen (R10.3) |
| Gift from the treasury | 20 emeralds | the daughter's start |
| Kit | 30 food, 30 wood and 15 stone a head | as a founder's (R4.34) |

A village is **ready** when it is not empty, abandoned, in famine or danger, or attacked in the last 30 days; it has 30 people and has waited out its
cooldown; the world is under its cap; it is **full** (two free beds or fewer, or a building found no lot within the last 30 days); and it can spare
the kit and the gift (after the kit the people who stay must still hold 20 food and 6 wood and stone a head). The report lists every reason it is not.

**Who goes** is whole households (a couple and their young children together, or one person alone), always including the household of its most
experienced builder (a village with nobody who has finished buildings cannot improve a house, R4.56), then the households of the trades the
village has most workers in. At least one other resident who can build stays behind, and a child is never sent without an adult. Never a guard, a merchant, an elder, a builder with a project under way, a pinned resident, someone who moved lately,
the last worker of a trade the village lives on, or so many food workers that fewer than two stay. The party is real residents moved over:
their names, ages, experience and families come with them, marriages survive the move, and they lose only their job (the new village assigns
them again).

**The tie** is two conditions, `colonyDaughter:<id>` on the parent and `colonyParent:<id>` on the daughter (the value is the day), so there is no
save-format change. Both histories say what happened.

### Choosing the site (R10.3, built)

The Minecraft layer proposes 48 candidates in a ring from the spacing out to 600 blocks from the parent (`ColonySiteFinder`) and `ColonySites` ranks them:

- only chunks already generated (nothing is generated for a search; on GoodeCraft the worlds are pre-generated), loaded one at a time and let go;
- not within the spacing of any village, not in an exempt zone or a WorldGuard region (R1.52), not over anything a player built, not water or lava;
- scored by `SiteSurvey`, and preferred where it supplies what the parent is short of (stone for a village short of stone);
- the same world as the parent, and a world that is allowed.

If nothing suits, the history says the party found no place and nothing leaves.

### Founding (R10.4, planned)

The caller makes the daughter settlement and calls `Colonies.settle`, which moves the party, takes the kit and the gift, records the tie and writes both
histories. The members are marked `moving:` exactly as a migration marks them, so the existing mover brings their villagers to the new centre
(holding its chunks until they arrive, as resettling does) and the new village is planned like any founded one.

### Failure (R10.5, planned)

A party that cannot reach the site in time turns back (they are not moved on paper until the ground is ready); a daughter that is abandoned
falls to ruins by the existing rule and its survivors may return.

### Kin (R10.6, R10.7, planned)

- Caravans between kin pay no carrier's share, and the parent sends relief to a daughter that is short in its first thirty days.
- Migration and marriage prefer kin villages.
- The dashboard draws a line between kin villages, with an attention item when a daughter is starving and the parent has surplus; the notice board and
  the villagers' talk mention them.

### Automatic founding (R10.8, planned) and the journey (R10.9, planned)

Automatic founding is `colonies.enabled`, off until the admin command has been watched. A visible walk out of the village and a road between kin
villages come last.

## Order of work

1. **R10.1, R10.2** — the rule and the report. Built.
2. **R10.3** — choosing a site (core ranking, Paper candidates).
3. **R10.4, R10.5** — founding in the world, and its failures, with `/settlement admin colonize <village> go` to force one.
4. **R10.6, R10.7** — kin: economy, dashboard, board, dialogue.
5. **R10.8** — automatic, behind the flag.
6. **R10.9** — the journey and the road.

## Limits and cautions

- Every daughter is more chunks to hold, more simulation and more dashboard. The performance test covers 50 villages of 50 people; the world cap is
  how that stays true.
- On a server with players' builds (GoodeCraft), a site must never be one of them. The site check will refuse anything that is not natural ground,
  and R1.52 refuses protected regions, but nothing can see a player's claim made by another plugin.
- Kinship is permanent in the first version. Letting ties fade as a daughter grows is possible (the conditions hold the day) and is left until
  the first version has been watched.

## Decisions taken (change them in the config or say so)

- A party is real residents, not spawned settlers.
- Same world only.
- Admin-triggered first; automatic behind `colonies.enabled`, off.
- Cap 12 villages a world, 300 blocks apart, one party in 90 days from a village.
