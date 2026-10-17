# Visible stores (R4.55)

The ledger is the only truth. A barrel in a village's building is a **picture** of one category of the ledger, redrawn from it; nothing a
player does to a barrel is ever an item that appears from nowhere.

## What is drawn

- One barrel per category of stores (food, wood, stone, metal, fuel, tools, goods) beside the sign of the village's building that fits:
  food in the granary (else the farm, smokehouse), wood in the sawmill, stone in the masons' yard (else the mine), metal in the mine
  (else the forge), fuel in the smithy (else the forge), tools in the smithy, goods in the shop (else the trading post). A village without
  that building draws nothing: there is no barrel without a place for it.
- Each commodity in the category is a representative item (cobblestone for cobblestone, a stone pickaxe for stone tools...) in the count the
  ledger holds, at most two stacks of a commodity, so a heap reads as plenty and a thin one as thin. Spending (builders, smiths, the
  merchant, eating) changes the ledger, and the barrel follows a few seconds later while a player is near.
- The barrel carries a persistent-data mark (village id and category); only marked barrels are the village's. A player's own barrels are
  never touched. It is only drawn on loaded ground near a player, and drawn again if broken.
- The treasury (emeralds) is not drawn: taking from it is R5.4's break-in.

## What a player may do

| Action | Result |
|---|---|
| Open and look | Allowed. |
| Take items out | **A theft.** The ledger loses the items' worth (rounded up, so a split gains nothing), the player keeps the items, which are marked as taken from the stores. History records it by name, and the player's reputation falls (at least 1, one point per emerald's worth, at most 30). Refused if the ledger holds less than the barrel shows. |
| Put items in | **A donation**, as `/settlement donate`: valued, limited by the room left, credited, reputation gained, a matching request paid. Whatever does not fit or is worth nothing stays with the player. With a treasury building the counter rule (R2.7) applies, so a deposit away from it is refused with the same message. |
| Put back what was taken | Items marked as taken from the stores are credited back to the ledger and pay nothing and earn no reputation, so taking stock and handing it in for a request's reward is not a trick. |
| Hoppers, droppers | Refused: an automatic drain could not be blamed on anyone. |
| Break the barrel | Allowed, but nothing drops (the ledger is unchanged), history records vandalism, reputation falls by 5, and the barrel is drawn again. |
| Explosions, pistons | The barrel is left standing. |

## Thieves

A player who took from the village's stores is not taken for a giver for ten village days: gifts (at a barrel or by `/settlement donate`) are
refused, so stock cannot be taken, changed (smelted, crafted) and handed in for a request's reward. Stock marked as taken carries the id of the
village it came from and can only go back to that village's stores. Theft lines in the history are merged (one per thief and store for three days).

## Known limits

- The game makes a barrel a fisherman's job site, so a jobless villager may claim a store barrel (as with the notice board's lectern); the
  simulation still owns the occupation (R4.45). T124 asks you to look out for it.
- Displays are rounded: a thing worth less than a unit does not show.
- Mood and threat do not move on a theft (R5.4 decides that for vaults); only history and reputation do.
