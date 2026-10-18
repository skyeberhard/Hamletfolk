# Profession levels: what they do, and how they could matter more

## What exists (R4.29, `TradeLevel`)

A resident's level comes from the days they have worked at their current trade: 1 at the start, 2 at 10 days, 3 at 30, 4 at 60, 5 at 100
(novice, apprentice, journeyman, expert, master). Working at a different trade starts the count again; a spell as a builder or guard keeps it.
For each level above the first:

| Effect | Per level |
|---|---|
| Gatherers and craftsmen's daily output | +10% (a quantity, not a quality) |
| A smith's smelting | +2 ore a day |
| A merchant's sales | one extra batch a day for every two levels |
| A guard's weight on duty | 1, then +1 at level 3 and again at 5; also +4 health a level |
| Walking speed | +3% |
| The villager entity | its vanilla level is raised to match (never lowered) for a vanilla trade, which unlocks the game's own trade offers of that tier for players; the level badge on the villager shows it |

That is all. A villager's output is a quantity added to the village's stores, so there is no item quality and no recipe to learn; the only way a
player sees a higher level is the game's own higher trade tiers (and standing-based prices, R3.4) and the badge.

## Built: ideas 1 and 2 (R4.56, R4.57)

- **Guild (R4.56).** With `construction.guild: true` (the default) a building is only improved to a tier someone in the village has the skill for: tier 2 needs an
  apprentice (level 2), tier 3 a journeyman, tier 4 an expert, tier 5 a master, of the building's own trade (`Guild`): a smithy's smiths, a shop's merchants, a farm's farmers,
  a guard post's guards, and for a house the builders (their rank by buildings finished: 3, 6 and 10 buildings for the ranks, and a veteran of 20 for a tier 5). A village that has lost its builders, or was adopted and has none with a record, can only raise first-tier houses until its builders have finished some buildings. A first tier is never held back. The dashboard's Selected building panel, Buildings tab and
  Upgrade refusal say what is missing. The Buildings tab's tooltip on the "no" is the reason.
- **Better goods (R4.57).** An expert smith forges diamond tools from three diamonds a batch (keeping three in reserve) once the village has the tools it wants; four diamond tools in store make gatherers work
  a tenth better (they wear out last); journeyman and master farmers bake one more grain into bread a day each.

## Ideas, in the order I would do them

1. **Levels gate buildings and tiers (a guild).** A higher-tier building asks for someone of the right skill in the village: a tier 2 smithy needs
   a journeyman smith (or toolsmith) to be living there, a tier 3 house or a forge needs a journeyman builder, a library's second tier a journeyman librarian.
   Today a village upgrades the moment it can pay, so levels do nothing for growth. This makes experience a thing the village has to *grow*, ties
   trades to buildings, and gives the dashboard a reason to show levels ("waiting on a journeyman smith"). Small: a check in `Construction.upgrade`, a
   line in the Attention page and the Outlook, no save change.
2. **Better goods from higher levels.** The commodities already have tiers (stone tools, iron tools, diamond tools; bread and cooked food; glass; books). A master
   smith with diamonds makes diamond tools; a journeyman makes iron where a novice made stone; a master glassblower's glass sells for more. It
   gives a village's stores quality without per-item data, because each quality is already its own commodity. Medium: a rule per trade in `SettlementSimulator.work`
   and tests.
3. **Apprentices.** A master (level 4+) takes an apprentice: a jobless adult or a child of their household (R6.1), who then levels 50% faster for as
   long as the master lives and works. Children of a master start with a head start in the same trade. It uses the household and marriage data we
   have and makes families matter. Medium, and needs the births and household code to name the child's trade.
4. **Prices and standing.** A master merchant raises what the village earns from a passing trader or a caravan (R6.5), and a master of a trade
   lowers what the village pays for that trade's goods. Small, and it pays for merchants and shops in trade.
5. **Masterpieces and legacy.** The first time a resident reaches master, the village puts a "masterpiece" in its record (a named line on the notice
   board, an item in the shop's chest); when a master dies, their trade's output dips and the history says who they trained. Mostly flavour; cheap once
   1 and 3 exist.
6. **Players buy village-made goods from a master.** A master's trades sell the village's own surplus (iron tools, books) at fair prices, through
   custom merchant recipes. High reward and high risk: it touches the game's trade UI, balance, and duplication safety (the ledger must follow), so only
   after the stores and donation paths have been played.

## Recommendation

Do 1 and 2 together (levels decide what can be built and what quality of goods is made), then 3 (apprentices) with the household work. They give a
village a visible arc: a hamlet of novices is stuck with huts and stone tools; a generation later it has journeymen, a forge and iron. Each is a
roadmap item of its own and needs its own playtest.
