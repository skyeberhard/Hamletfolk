# Building requirements

What each building needs, gives and costs: size, beds, workers, when the village asks for it. The numbers are read from the
code and from the Paper 26.2 jar (sizes and beds with `python tools/piece_sizes.py`), not from memory. Where this page and the
code disagree, the code wins and this page is wrong: say so. Companion pages: [BUILDING_LIBRARY.md](BUILDING_LIBRARY.md) (which
pieces each biome has), [VILLAGE_PLANNING.md](VILLAGE_PLANNING.md) (how the plan is laid out), [GROWTH_BACKLOG.md](GROWTH_BACKLOG.md).

## 1. The rules that apply to every building

- **A building must fit inside its lot**, in either orientation (`Construction.orient`). A template bigger than the lot is passed over
  ("its lot is too small for the plainest design"). The lot sizes are fixed per kind (section 2), not per biome.
- **The ladder.** Each kind has an ordered ladder of tiers, one template each. The village builds the *plainest* tier it can pay
  for, and a house is chosen for the most beds it can pay for. Better tiers come later as upgrades (built over the old one,
  centred on it, and also inside the lot).
- **One project at a time**, one new decision a day, and only once the village has 3 people (`Construction.MIN_POPULATION`).
  A builder is a resident who takes the job; a project nobody can staff for 20 days is given up (R4.43).
- **Price.** Every block of the template is priced in the village's materials (planks and logs are wood, cobblestone and stone
  are stone, iron is metal, and so on: `ResourceMapper`, `Blueprint.cost`), and the village pays `construction.cost-percent`
  (35 in the live config) of that, block by block as it builds. Doors, torches, chests and crops are free. A village's first
  mine costs no stone (R4.39). Cutting rock and felling trees while levelling the ground *adds* stone and wood (R8.20).
- **Levelling is free.** The ground under the building is graded to the median height: up to 8 blocks cut or filled, shallow
  water (3 deep) filled, a blended edge 2 blocks wide (`TerrainPad`).
- **Registration.** A finished building gets a sign (`[Farm]`, `[Smithy]` and so on) and is registered; a sign you place yourself
  registers a building the same way, without checking its blocks (R2.4 is not built). Buildings already standing in an adopted
  village are found without signs (R2.8). At most 200 buildings are registered per village.
- **Places.** A work building gives 4 places (a shop 1), and 2 more for each tier above the first (a shop 1 more); an armoury
  gives places to two trades. A job needs a free place, as a farmer needs a farm.

## 2. Sizes: the lot, and the biggest piece that fits

Lot sizes (`PlanGenerator.size`), width by depth in blocks:

| Building | Lot | Notes |
|---|---|---|
| House, guard post | 9 by 9 | |
| Farm | 17 by 13 | |
| Smithy, mine | 13 by 13 | the game's smithies are up to 9 by 12 |
| Shop, sawmill, granary, and every trade building (harbour, pens, smokehouse, tannery, stable, apiary, map room, library, glassworks, trading post, bowyer, masons yard, armoury, chapel) | 11 by 9 | |
| Treasury, forge | 11 by 11 | |
| Town square | 15 by 15 | |
| Street lights, palisade, rampart | no lot | laid on the plan |

**Generated buildings** (mine, guard post, shop, treasury, sawmill, forge, granary and all the trade buildings): two tiers, a
5 by 5 hut of wood (tier 1) and a 7 by 7 one of stone (tier 2), each with a doorway and a sign strip. They hold no beds.

**Vanilla pieces** the catalog uses (size is x by z, height in brackets; from the jar):

| Style | House 1 (small) | House 2 (medium) | House 3 (big) | Farm | Large farm | Smithy | Town centre |
|---|---|---|---|---|---|---|---|
| Plains | 7x7 (7), 1 bed | 13x11 (8), 2 beds | 7x11 (11), 4 beds | 7x9 | 13x9 | 9x12 | 9x9 / 11x11 |
| Desert | 6x5 (6), 1 bed | 6x7 (6), 2 beds | none | 5x7 | 11x13 | 9x9 | 12x12 / 15x15 |
| Savanna | 7x7 (7), 1 bed | 8x15 (7), 2 beds | none | 6x9 | 9x9 | 7x11 | 9x9 / 11x11 |
| Snowy | 7x6 (5), 1 bed | 7x8 (6), 2 beds | none | 6x7 | none | 8x7 | 7x7 / 11x9 |
| Taiga | 7x9 (8), 1 bed | 8x7 (11), 2 beds | none | 7x8 | 10x10 | 11x8 | 9x9 |

Every vanilla small house variant in the jar has exactly one bed (the catalog uses variant 1).

## 3. Beds: what a house lot can hold

A resident needs a bed; a village grows only while it has a free one (a newcomer needs one, and a birth needs one). Housing is
counted from the beds the game knows of (points of interest), not from the houses.

Which house fits a 9 by 9 lot (the whole template inside the lot):

| Style | Small (1 bed) | Medium (2 beds) | Big (4 beds) | Beds per house lot |
|---|---|---|---|---|
| Plains | fits | **does not fit** (13x11) | **does not fit** (7x11) | **1** |
| Desert | fits | fits (6x7) | none | 2 |
| Savanna | fits | **does not fit** (8x15) | none | **1** |
| Snowy | fits | fits (7x8) | none | 2 |
| Taiga | fits | fits (8x7) | none | 2 |

So in the two biggest village biomes a new house lot adds one bed, and the medium and big houses the catalog lists are never
built there. A stage 1 plan has at most 7 house lots, stage 2 six more, stage 3 five and stage 4 six (`PlanGenerator`; lots on bad ground are
lost): with 1-bed houses that is 7 beds from new houses at stage 1 and 24 at the most with all four stages, which is why a village
stops growing at about its houses. This is logged as R4.48 in the roadmap. Beds
already in an adopted village (vanilla houses, found by R2.8) count in full.

## 4. What each building is for, and when the village asks for it

"Asks" means the planner (`Planner`) puts it on the list; the village builds it when it can pay and a builder is free. A need
(food, shelter, safety, supply) comes before a want (growth).

| Building | Tier | Gives | The village asks for it when |
|---|---|---|---|
| Farm | food | 4 farmer places (+2 a tier); food | food is short and it has no farm |
| House | shelter, growth | beds (section 3) | residents outnumber beds; or no bed is free (a want) |
| Mine | supply | 4 miner places; stone and a little metal; a quarry beside it | it has none (once fed) |
| Smithy | supply, food | 4 toolsmith places; tools | tools have run out and there is metal, cobblestone or ore with fuel to work, and it has none |
| Guard post | safety | a place to gather guards | the village is alarmed or worse and has none |
| Town square | first want | a meeting place; the bell | it has none (a bell the game made counts) |
| Street lights | safety | fewer monster spawns | after any attack |
| Palisade | safety | a fence round the plan | after lights, for the plan's stage |
| Rampart | safety | stone wall, gates, towers | a threatened town; tiers 2 and 3 |
| Shop | growth | 1 merchant place; sells surplus | it has a surplus and no shop |
| Treasury | growth | +500 emerald limit | the treasury is over 60% full |
| Sawmill / forge / granary | growth (a leaning) | lumber +25% / 4 more ore smelted / half the food spoilage | its land leaning calls for it, at about village size |
| Trade buildings (harbour, pens, smokehouse, tannery, stable, apiary, map room, library, glassworks, bowyer, masons yard, armoury, chapel) | growth | 4 places for the trade (+2 a tier) | the land opens the trade and it has no building for it |
| Trading post | growth | each merchant sells one more a day | it has merchants and a shop and 12 people |

## 5. Storage and money

- **Stores:** each material holds 100 plus 10 per resident (food 100 plus 40 per resident); anything above is lost, except the
  overflow a passing trader buys each week (R3.18).
- **Treasury:** 200 emeralds, plus 500 for each treasury building.
- **A village wants** 10 food and 3 of each other material per resident, and asks players for help below that (R3.3, R4.40).

## 6. Where this page's numbers live

`PlanGenerator.size` (lots), `PlanGenerator.STAGE_*` (lots per stage), `TemplateCatalog.ladder` (tiers and pieces),
`BuildingGenerator` (generated huts), `BuildingType` (places, jobs), `Planner.growth/safety/shelter/food` (when it asks),
`SettlementSimulator.capacity` and `DEFAULT_TREASURY_BASE`, `Construction` (fit, price, one at a time),
`TerrainPad` (levelling). Sizes and beds: `python tools/piece_sizes.py`.
