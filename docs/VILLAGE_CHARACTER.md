# Village character: what makes villages different

This is the design for what a village does once its basic needs are met, and why two villages end up different. What is
built is marked **built**; everything else is a proposal, with the roadmap item it belongs to where one exists. The
proposals are not on the roadmap until they are agreed.

The goal is immersion: a player who stumbles on a village after a week away should be able to tell, before talking to
anyone, what kind of place it is, how it has been doing and what it has been through.

## 1. The loop a village runs

```
 land, animals ──► leaning ──► trades ──► materials ──► processing ──► goods, tools, golems ──► surplus ──► emeralds
       ▲                         ▲                                                                            │
       │                         └──────────── skill (levels) ◄── work done ◄──────────────────────────────┐  │
       └── marks on the land (felled trees, quarries) ◄── buildings, walls, districts ◄── needs, then wants ◄─┴──┘
```

A village is first about **needs** (food, a mine for stone and metal, shelter, safety), then about **wants**. Wants are
where character comes from, and they are shaped by four things: the land, the village's history, its people and its size.

## 2. What triggers each building today (built)

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

Emeralds come from a merchant selling surplus (R3.9), from what visitors pay villagers (R3.15), from donations, and later
from trade between villages (R6.5). Villagers' own trades mint emeralds in the game and never reach the treasury.

## 3. Character: the four sources

A village's character is not one setting. It is several traits, each read from something real, each shown to the player.

### 3.1 Land: the leaning (R8.11)

When a village is founded or adopted, its surroundings are surveyed once (the R8.2 survey, extended) and kept:

| Read from the land | Leaning | Trades it staffs first | What it sells |
|---|---|---|---|
| Forest cover | Timber | lumberjacks, carpenters | wood, charcoal |
| Exposed stone, hills, ore seen in cliffs | Mining | miners, smiths | stone, metal, tools |
| Flat grass, rivers | Farming | farmers, millers | food, hay |
| Coast, lakes | Fishing | fishermen | fish, kelp |
| Sheep, cattle, horses nearby | Pastoral | shepherds, herders, horse trainers | wool, leather, horses |
| Sand, clay | Craft | glassblowers, potters | glass, bricks |
| Several good scores, on a road or river | Trading | merchants | whatever it can buy cheap |

A village can have a strong leaning, a mixed one or none ("all-round"). The leaning is a bias, not a cage: needs still
come first, and a mining town still farms. Over years the leaning can drift with what the village actually does (a
farming village whose fields are poor and whose hills are rich becomes a mining town), and each drift goes in the history.

### 3.2 History: temperament

What happens to a village changes how it behaves. Each is a slow-moving score, recorded with its reason:

| Temperament | Raised by | What it changes |
|---|---|---|
| **Martial** | attacks survived, guards called up, raids won | builds walls sooner and higher, keeps guards in peace, more golems |
| **Welcoming** | newcomers settled, players with good reputation, migrants taken in | newcomers come faster, better prices for visitors |
| **Wary** | thefts (R5.4), players who harmed villagers, raids lost | worse prices for strangers, gates kept narrow, fewer newcomers |
| **Prosperous** | full stores, a full treasury, trade | builds grander tiers, decorates (gardens, banners, lamps) |
| **Hard-pressed** | famines, shortages, deaths | builds plainly, shrinks its ambitions, people leave |

### 3.3 People: the founders and their children

Residents already have traits (work ethic, sociability, ambition, bravery). A village's average sets its tone: an
ambitious village upgrades sooner, a sociable one builds a tavern and a market before a second farm, a brave one calls
guards readily. Families (R6.1) pass trades down (R4.10 already does this for jobs), so a village of smiths stays a
village of smiths. An elder (R6.4) can tilt policy.

### 3.4 Size: stage

| Stage | Roughly | What it unlocks |
|---|---|---|
| Hamlet | under 8 people | needs only; a fence |
| Village | 8 to 20 | its leaning's first building; a palisade; a market day |
| Town | 20 to 50 | a second leaning building; a stone wall with towers; districts |
| City | 50 and more | a keep or hall, a cathedral or guildhall; walls with gatehouses; several districts |

Stage can fall as well as rise (R8.9), and the history says when it changes.

## 4. How character shows

Character the player cannot see does not exist. Every trait has to be visible somewhere:

1. **What gets built.** Signature buildings per leaning (R8.12): a sawmill and log yard for a timber town, a quarry,
   forge and spoil heaps for a mining town, a granary, windmill and market for a farming town, a harbour and drying racks
   for a fishing town, pens, a shearing barn and stables for a pastoral one, a trading post and warehouses for traders.
2. **What it is built of.** The palette follows the leaning and wealth within the biome: a mining town builds in stone
   and cobble, a timber town in logs and planks, a prosperous one in bricks and glass. This is a substitution step on
   templates, like the biome one that already exists (BiomeSet).
3. **What it does to the land.** Felled stands and replanted saplings round a timber town, a quarry pit and tunnels at a
   mining town, fields spreading out from a farming one (R4.25).
4. **How it is defended.** From fence posts to log palisade to stone wall with towers (section 6), sooner and higher in a
   martial village.
5. **What its people say.** Dialogue already reads the ledger; it should also read the leaning and temperament ("We're
   miners here, always have been", "Since the raid we don't open the gate after dark").
6. **What it trades.** Villagers' trades lean to the village's goods, and prices reflect plenty and want (R3.1 does part
   of this).
7. **Its name and its record.** The history book reads as a chronicle, and `/settlement` names the leaning, temperament
   and stage with a line of why.

## 5. Materials: a finer ledger (proposal)

### What is wrong with today's ledger

Today a village counts six things: food, wood, stone, metal, tools and goods. That was deliberate (it kept the first
economy simple), but it is now too coarse:

- Coal is not tracked at all, so there is no fuel for smelting, smoking or glass.
- Iron, copper and gold are one "metal". Diamonds, lapis and redstone are not tracked.
- Wool, leather, glass, paper and string are one "goods".
- Tools are one count, with no tier.
- Nothing can be processed. Raw iron and an ingot are the same thing, and charcoal cannot be made from wood.

So golems (36 iron), smithing, glassmaking, profession workstations and anything that needs a specific item cannot be
modelled honestly.

### The proposal: commodities under categories

Keep the six categories as they are (storage limits, needs, the planner and the tests all read them), and track
**commodities** inside each. A category's amount is the sum of its commodities, so existing rules keep working.

| Category | Commodities |
|---|---|
| Food | grain, bread, vegetables, meat, fish, cooked meat, hay |
| Wood | logs, planks, charcoal |
| Stone | cobblestone, stone, stone bricks, sand, gravel, clay, bricks, glass |
| Metal | raw iron, iron, raw copper, copper, raw gold, gold, coal, diamond, lapis, redstone |
| Tools | stone tools, iron tools, diamond tools, weapons, armour |
| Goods | wool, leather, string, paper, books, candles, horses |

Coal could equally be its own Fuel category; that is a choice to make when the design is agreed.

**Processes** turn commodities into others, each done by a trade at a building:

| Process | Who, where | In | Out |
|---|---|---|---|
| Smelt | smith, smithy | raw iron + coal | iron |
| Burn charcoal | lumberjack (level 2+), charcoal pit | logs | charcoal (fuel) |
| Make glass | glassblower, workshop | sand + fuel | glass |
| Fire bricks | mason, kiln | clay + fuel | bricks |
| Forge tools | smith, smithy | iron + planks | iron tools |
| Forge a golem | smith, smithy | 36 iron + a pumpkin | an iron golem at the guard post |
| Smoke | butcher, smokehouse | meat + fuel | cooked meat (keeps longer) |

**Yields are 1:1 with the world where the world is loaded.** When a miner digs while its chunks are loaded, the blocks it
breaks are what goes into the stores, as the game would drop them (stone gives cobblestone, coal ore gives coal, iron
ore gives raw iron, a diamond is a diamond). When the chunks are not loaded, the simulation produces at the mine's
average rate, which comes from the ore actually seen in it (sampled when it was last loaded), and it runs up a **dig
debt**. The next time the area is loaded, the miner digs that debt out of the world, and what that digging finds settles
the account: more coal than the average makes a small bonus, less makes a small shortfall. Lumberjacks work the same way
with trees. So the ledger and the world agree over time, and the history is never a fiction.

**Migration:** a save-format step converts the old categories into default commodities (wood to planks, stone to
cobblestone, metal to iron, tools to iron tools, goods to wool). Donations already read the real item, so they map
straight onto commodities.

## 6. Defence that grows (proposal; R5.6 is built for its first tier)

### The wall moves out as the village grows

The ring follows the plan. When the plan grows (the village outgrows its lots and the plan adds a stage), the ring is
recomputed. The new ring is built first. Then the old sections that are now inside it are taken down, and their posts
go back to the stores. Only posts the village placed are removed (they are on record). Later, at town size, inner rings
become district boundaries: an old town and a new quarter.

### The wall upgrades in tiers

| Tier | Wall | Gates | Towers |
|---|---|---|---|
| 1 (built) | fence posts | open gaps | none |
| 2 | log palisade, 3 high, sharpened tops | gaps with a gate frame | log watch platforms at corners |
| 3 | stone wall, 4 high, walkway | gatehouse at each main street | stone towers at corners and every 32 blocks |
| 4 | stone wall with battlements | gatehouse with portcullis | towers with roofs, a keep |

The wall itself is generated in core, because its length varies: a repeating segment along the ring, with corner and
gate pieces. Towers and gatehouses are templates. They are generated by default and can be replaced by captured builds
(`/settlement admin capture`, R4.18), so you can design them in game and the villages use your designs. Upgrades follow
temperament and stage: a martial village goes up a tier sooner, a hamlet never goes past tier 2.

One open question for play: villagers cannot open fence gates, and zombies cannot either. Gaps let monsters through, but
gates would trap villagers inside. A gatehouse with a guard is the honest answer. Until then the gaps stay.

## 7. Trades and skill (proposal)

### Every trade has a source

| Trade | Needs (a building, or something in the land) | Notes |
|---|---|---|
| Farmer | farm, farmland | built |
| Lumberjack | forest within reach | built (no building needed) |
| Miner | mine | built |
| Fisherman | water within reach, a dock | today only from the vanilla barrel |
| Shepherd | sheep within reach, pens | wool from real shearing when loaded |
| Herder / butcher | cattle, pigs, chickens, a smokehouse | |
| Horse trainer | horses within reach, stables | new; tames and breeds horses, later mounts for guards |
| Beekeeper | bees nearby | honey and candles |
| Smith | smithy | smelting, tools, golems |
| Mason | quarry or kiln | bricks, stone bricks |
| Glassblower | workshop, sand | glass |
| Librarian, cleric, cartographer | library, chapel, map room | goods and services; later, learning and healing |

Today, the vanilla professions other than farmer, miner and lumberjack appear only when a villager claims a job-site
block it happens to find. The simulation never builds one. The proposal: each trade's building includes its vanilla
workstation (a lectern in the library, a smoker in the smokehouse), paid for from commodities (a lectern needs planks
and a book). Each sim-only trade (lumberjack, miner, guard, builder) is paired with a block that marks its place (R4.13).

### Levels for every trade

The builder's count (R4.21) generalises: every resident has experience in their trade, and levels 1 to 5 (matching
vanilla's novice to master, so their vanilla trades unlock as they rise).

| Trade | What a level gives |
|---|---|
| Builder | blocks a second (built), then harder tiers: a master can build a stone wall or a gatehouse |
| Lumberjack | more wood a day, faster walking (R4.17), charcoal at level 2 |
| Miner | more ore a day, deeper digging, smelting raw ore on site at level 3 |
| Farmer | bigger harvests, then breeding seed for a better yield |
| Smith | better tools, armour, then golems at level 3 |
| Guard | health, speed and damage (attributes on the villager), better arms from the stores |
| Merchant | more batches a day, better prices |

Speed and health can be set on a villager with the game's own attributes. Fighting needs the brain module (R9.3).

## 8. People and houses (proposal)

The game's small houses have one bed each, which is why a village needs so many. Two steps:

1. **Count beds, don't guess.** The planner assumes three to a house. Instead it should count the beds in the design it
   is about to build (they are in the blueprint), and prefer the design with the most beds for its cost. Upgrades add
   beds.
2. **Households** (R6.1): couples and children share a house, and an upgraded house is where a family grows. Births
   today only happen through vanilla breeding near a player. For villages left alone for years, the simulation needs
   births of its own, or the population only grows by newcomers.

## 9. Time and unattended play (built, R4.22)

- `pause-when-empty-seconds=-1` in `server.properties` and `construction.unattended: true`.
- A village loads only the chunks it needs, while it decides (once a village day) and while a project is open.
- `construction.speed` scales builders. `/settlement admin warp [name] <days>` runs a village up to 60 days ahead, once
  per command, and the village keeps that lead.

Proposed: a **fast-forward** (`/settlement admin fastforward <name|all> <speed>` and `... stop`) that runs villages at 1
to 100 times game speed until stopped. The simulation is cheap: a village day costs a fraction of a millisecond. The
limit is building, so in fast-forward builders work in large batches, and the tick budget per second is capped so the
server stays responsive. At 100 times, a game day passes in 12 seconds, a game year in about 73 minutes, and a decade
in about 12 hours.

The game's own `/tick rate` and `/tick sprint` speed up the whole world instead, mobs and crops included. That is
heavier on the server.
