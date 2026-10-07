# Village character: what makes villages different

This is the design for what a village does once its basic needs are met, and why two villages end up different. It
covers R4.20 to R4.25, R5.6, R5.7, R3.15, R8.11 and R8.12 in [ROADMAP.md](../ROADMAP.md). What is built is marked **built**;
the rest is planned.

## The loop a village runs

```
land ──► leaning ──► trades ──► stock ──► surplus ──► shop, merchant ──► emeralds ──► treasury, upgrades
  ▲                                │                                                        │
  └────────── buildings & marks ◄──┴──────────── needs (food, shelter, safety) ◄────────────┘
```

A village is first about **needs** (food, then a mine for stone and metal, then shelter, then safety), and only then
about **wants**. Wants are where character comes from.

## What triggers each building (built)

| Building | Triggered by |
|---|---|
| Farm | food under 10 a head, a famine, or every farm place taken |
| Town square | food covered; built once |
| Mine | food covered and no mine yet (R4.20) |
| House | fewer beds than people (shelter); later, every bed taken so newcomers can settle |
| Street lights | the first attack on the village (R5.6) |
| Palisade | the lights are up and the village has been attacked (R5.6) |
| Smithy | no tools in the stores |
| Guard post | the village is alarmed |
| Shop | spare **resources** (stock well above what the village keeps): the shop sells surplus |
| Treasury | **emeralds**: the treasury is 60% full; a treasury building raises the limit |
| Upgrade | a need no new building can meet (R4.19), or, with every need met, a want, one a week |

## Where emeralds come from

Villagers' own trades mint emeralds in the game; they never touch the treasury. So a village earns only by:

1. **A merchant selling surplus** into the treasury (R3.9). This needs a shop and stock above what the village keeps,
   which is why the permanent lumberjack and miner (R4.24) matter: with them the stores fill, there is a surplus, and
   the merchant has something to sell.
2. **Visitors' purchases** (R3.15, built): the emeralds a player pays a villager for goods go into the treasury.
3. **Trade between villages** (R6.5, planned): a village with a surplus sells it to one that is short.

Players can also donate emeralds, and fill requests for a reward paid out of the treasury.

## Suppliers (built, R4.20 and R4.24)

A village keeps one lumberjack from four residents, and one miner once a mine has a free place, whatever it holds. More
are taken on when a material a building needs runs short. A supplier works until the stores are full of what it makes,
then rests (no output, no tool wear) until the stock falls below nine tenths of the limit. So a village is never
caught with an empty woodpile, and does not waste tools on stock it cannot keep.

## Character (planned, R8.11 and R8.12)

Today a village's "direction" (farming, timber, mining, craft, trading) is read from its last week's output, which the
same job rules make alike. Two changes give it character:

1. **The land sets the leaning** (R8.11). When a village is founded or adopted its site profile (`/settlement survey`:
   forest, stone, water, farmland) is kept. Forest makes a timber town, hills a mining town, farmland a farming town,
   water a fishing town. The leaning decides which trades are staffed first, which buildings are improved first and
   what the merchant sells. It is shown by `/settlement` and `/settlement plan`, with the reason.
2. **A leaning unlocks buildings** (R8.12). Once every need is met a village builds one of each building its leaning
   unlocks, and they do something:

   | Leaning | Building | Effect |
   |---|---|---|
   | Timber | Sawmill | more wood from each lumberjack |
   | Mining | Quarry, forge | more stone and metal, better tools |
   | Farming | Granary, market | less spoilage, more selling |
   | Fishing | Harbour | food from water, a place to trade |
   | Trading | Trading post | a bigger treasury, a second merchant |

Together with trade between villages (R6.5) this is what makes a village across the river different from this one: each
has something the other lacks, so each needs the other.

## Marks on the land (planned, R4.25)

A lumberjack fells a real tree near the village for so much wood gathered and plants a sapling; a miner digs a
quarry or tunnel at the `[Mine]`. The ledger is unchanged: the marks show work already counted. They never touch anything
a player built.

## Defence (R5.6, R5.7; the guard rules are R5.1 and R5.5)

After the first attack the village does what any player would:

1. **Light the streets.** A post with a torch every sixth block along every street and at the corners of the square.
   Monsters spawn in the dark, so this does the most for the least wood.
2. **Raise a palisade** once the lights stand: a one-block fence in a ring round the village, a gap three wide where
   each street leaves it. Zombies cannot jump a fence. A village that cannot yet pay for these saves up for them and
   takes on a lumberjack, rather than starting a shop or the town square meanwhile.
3. **Guards** as before (R5.1, R5.5): one per fifteen residents when wary, up to one per six under siege, armed from the
   tools in the stores.
4. **No newcomers into danger** (R5.7): a village attacked in the last week, or with danger of 30 or more, takes in no
   newcomers and no migrants, so a village in trouble does not keep losing new arrivals.

A raid is the vanilla game's: Bad Omen (from killing a patrol captain) starts one in the village the player enters.
The simulation cannot stop a raid, only prepare for it; iron golems (an idea, not yet on the roadmap) are the answer to
raiders that a fence does not stop.

## Time and unattended play (built, R4.22)

For a server left running with nobody online:

- `pause-when-empty-seconds=-1` in `server.properties` (so the server does not freeze), and `construction.unattended: true`.
- A village loads only the chunks it needs, only while it decides (once a game day) and while a project is open, and
  lets them go. Nothing is force-loaded for good.
- `construction.speed` scales how fast builders work; `/settlement admin warp [name] <days>` runs a village up to 60 days
  ahead at once, for testing or to bring one on.
