# Trade between villages (R6.5)

Status: built (core `Exchange`, run from `SettlementService`), unplayed (T131).

## The rules

A village with more of something than it keeps sends it by caravan to a neighbour that is short of it. The neighbour sends back what it can spare
that the first lacks. Both loads are valued at the fair price (what the village's own merchants get: `SettlementSimulator.unitsPerEmerald`), and
**only the difference is paid, in emeralds**, from the debtor's treasury to the creditor's.

- **Who:** two living villages in the same world, within `trade.range` blocks (default 800) of each other. A pair trades at most once every 5 village days.
- **What:** per direction, the one category the neighbour is most short of (below `wantedLevel`) that the sender can spare (above `surplus`, what it keeps),
  at most 60 units, never more than the neighbour's stores have room for. The units move commodity by commodity (bread stays bread).
- **Merchants:** a merchant living in either village keeps the load whole. Without one, the carriers keep a fifth on the road (only 80% arrives, and
  is paid for), so merchants, and the shop that creates them, make trade worth more.
- **Money:** the debtor pays as much of the difference as it holds and the creditor's treasury can bank (R2.6). If that is less than the difference the larger
  load is cut back until it is covered. A village with no treasury can still swap goods for goods of equal worth.
- **Relief:** a neighbour in a famine is sent food whether it can pay or not, if the sender can spare it (a relief load is worth 0 and unpaid).
- **History:** both villages record the load (and the settlement). Nothing else is saved except the day of the last trade, in each village's conditions.

## What it does not do

- Carry goods across worlds (that needs the portal links; see GOODECRAFT_INTEGRATION.md) or by a visible caravan (the load moves in the ledger, not in the world).
- Set prices by supply and demand (a steward does that later: R6.6).
- Make a village send what it needs: it only sends stock above what it keeps, and never to a village that is not short.

## Tuning and testing

`trade.enabled` and `trade.range` in the config. `/settlement admin trade [village]` forgets the village's last trades and runs a caravan now, and says why
nothing trades if nothing does (distance, merchants, what each has spare and lacks, treasuries). See T131.
