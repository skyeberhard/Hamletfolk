# Building guide: how to make a structure the villages will build, and add tiers to it

This is the how-to for authoring buildings: build one in the game, capture it, test it, and add higher tiers. It is read from the code
(`TemplateCommand`, `TemplateLibrary`, `TemplateCatalog`, `Blueprint`, `Construction`), not from memory. Companion pages:
[BUILDING_LIBRARY.md](BUILDING_LIBRARY.md) (what each biome has and what is still to author) and
[BUILDING_REQUIREMENTS.md](BUILDING_REQUIREMENTS.md) (sizes, prices, places, what each kind needs).

## 1. How tiers work

- For each **kind** of building (house, farm, smithy, shop, ...) and each **style** (plains, desert, savanna, snowy, taiga) there is an
  ordered **ladder** of tiers: tier 1 is what a village builds first, and it **upgrades** to tier 2, 3 and so on when it can pay and has
  nothing it needs more (`Construction.upgrade`).
- A rung is one design. Where it comes from:
  - **vanilla**: a piece from the game's own villages (the plains house ladder is small, medium, big: tiers 1 to 3; every other style's house
    ladder has 2, farms have 2 except snowy which has 1);
  - **generated**: a plain 5 by 5 hut (tier 1) and 7 by 7 stone building (tier 2) the plugin makes for kinds the game has no piece for (mine,
    guard post, shop, treasury, and the trade buildings: `BuildingGenerator.TIERS` is 2);
  - **captured**: yours. A captured design **replaces** the rung at its tier, and a capture one tier past the end of the ladder **adds a rung**.
- So "more tiers" is exactly: capture the next tier. Nothing else needs changing. Each village's builders, its upgrade rules, the dashboard's
  "tier N of M" and the Upgrade button all read the ladder as it is.
- Tiers go in order: you can capture tier N only if the ladder already has N-1 rungs (the command says "the next tier to capture is N").
  A rung can be replaced any time by capturing it again, and removed (back to the generated or vanilla one) with `capture remove`.
- Styles are separate: a plains tier 3 house does nothing for a desert village. Capture one for each style that should have it.

## 2. Build it

Build on flat ground (a creative test world is ideal), in the materials you want the villages to use **in that style**. A captured design is
stored in the style's own materials and is not substituted again, so a desert building should be built of sandstone.

Rules the village needs:

1. **Size.** The building must fit its lot in either orientation (the lot sizes are in BUILDING_REQUIREMENTS section 2: a house lot is 11 by 13 in
   plains, 9 by 15 in savanna and 9 by 9 elsewhere; a work building 11 by 9 or 13 by 13). A design bigger than the lot is passed over. A capture
   may be at most 32 blocks on a side and 16,000 blocks in all.
2. **A door.** Put the front door on the side that should face the street. The village turns the building so its door faces the nearest street
   or the square, so the door position is how it knows which way is "front".
3. **A sign.** Give a business, farm, pen, apiary or harbour a **wall sign** (a hanging sign strip over the door for a business, a sign on a
   fence post for a farm): the village writes `[Shop]`, `[Smithy]` and so on on it and registers the building by it. A **house has no sign**: it is
   registered by its bed. A capture without a sign warns you and the village will not know the finished building until a sign is placed.
4. **Beds.** For a house, **count the beds**: the village picks the house with the most beds it can pay for, and an upgrade only goes to more beds.
   (A tier 3 house should have more beds than tier 2.) Workshops and the generated buildings hold no beds.
5. **A workstation** for a trade building: the block its trade uses (a smithing table, a loom, a lectern, a barrel...). It is priced like any block.
6. **Materials are the price.** Every block is priced in the village's materials (planks and logs are wood, cobblestone and stone are stone, iron is
   metal; `ResourceMapper`, `Blueprint.cost`) and the village pays `construction.cost-percent` (35 live) of that as it builds, block by block. Doors,
   torches, chests and crops are free. So a design of gold blocks is a design nobody can afford. Keep each tier clearly dearer than the one below.
7. **Empty inside.** Put nothing in chests, barrels or furnaces: contents are not copied. Furniture (beds, tables, flower pots, item frames) is.
8. **Upgrades are built over the old one**, centred on it, and must also fit the lot. So a higher tier should be at least as big as the one below
   and roughly the same footprint centre; a much larger one will not fit a lot that held the smaller.

## 3. Capture it (step by step)

You need to be an admin, in the game (not the console).

1. Stand **on the floor** at one **bottom corner** of the building, inside the area you want to save (the block under your feet is the corner).
   Run `/settlement admin capture pos1`.
2. Go to the **opposite** corner and run `/settlement admin capture pos2`. The box runs between the two marked blocks in all three directions, and the
   **floor is the height of corner 1**: so corner 2 sets the far walls and the **top**. In creative, fly to the opposite corner at roof height (or higher) and
   stand on a block there, so the roof is inside the box. A corner 2 below the floor includes a shaft (a mine going down) at negative heights. Include the
   walls, roof, door and sign; air is not saved, so a roomy box costs nothing.
3. Run `/settlement admin capture <type> <tier> [style]`.
   - `<type>` is the building: `house`, `farm`, `smithy`, `mine`, `guard_post`, `shop`, `treasury`, `sawmill`, `forge`, `granary`, or a trade building
     (`harbour`, `pens`, `smokehouse`, `tannery`, `stable`, `apiary`, `map_room`, `library`, `glassworks`, `trading_post`, `bowyer`, `masons_yard`,
     `armoury`, `chapel`). A rampart `gatehouse` or `tower` is captured differently (see the command's reply).
   - `<tier>` is the rung: `1` to replace the first, or the next free number to **add** a higher tier.
   - `[style]` is `plains`, `desert`, `savanna`, `snowy` or `taiga`; left out it is the style of the biome you are standing in.
4. The reply says what it saved: the number of blocks, its size (width by depth) and what it costs, and warns if there is no sign. Check the cost
   against the tier below it.
5. It is saved as a text file, `plugins/Hamletfolk/templates/<type>_<style>_<tier>.txt` (for example `house_plains_4.txt`), one block per line, and is
   used at once. Copy the file to another server's `templates` folder to share it. A bad file is logged and skipped at start.

## 4. How it is logged and where to see it

- **On disk:** the file above is the record. There is no other registry: the catalog is rebuilt from the folder at start.
- **In the game:** `/settlement admin capture list` lists every captured template by kind, style and tier (the capture reply itself gives its size and cost).
- **In the dashboard:** a building's "tier N of M" is M = the highest rung of its ladder, so adding a tier raises M for every building of that kind and style at once, and a
  village that can afford it will upgrade to it (or an admin can press Upgrade on the building's page).
- **In a village's history and save:** nothing about the template is saved with a village; a finished project records the building's **tier** only. If you
  capture a replacement for a tier, villages that already built the old one keep it until an upgrade replaces it.

## 5. Test it before villages use it

1. `/settlement admin build <type> [tier] [style]` places the template where you stand (types: house, farm, smithy, mine, guard_post, shop,
   treasury, gatehouse, tower). Look at it from outside and inside. Check the door faces where you expect and the sign reads right.
2. In a creative test village, order the building from the dashboard's Plan tab (Build next) or press Upgrade on a tier below, and watch a builder
   raise it. Check it fits its lot (the dashboard says "its lot is too small" if not) and that the price is one the village can pay.
3. If something is wrong, capture the tier again (it replaces the old one), or `/settlement admin capture remove <type> <tier> [style]`.

## 6. A worked example: a tier 3 desert house

The desert house ladder has 2 rungs, so the next to capture is tier 3.

1. Build a desert house bigger than the medium one in sandstone, with a door on the front, **4 beds**, a few windows and a roof; keep it within the
   9 by 9 lot, since the desert house lot is 9 by 9 (it must fit the lot as the tier 2 house does).
2. `/settlement admin capture pos1` on the floor at one bottom corner, `pos2` at the opposite corner standing at roof height, so the roof is inside the box.
3. `/settlement admin capture house 3 desert`.
4. `/settlement admin capture list` shows `House · desert · tier 3`. A desert village's houses now read "tier 2 of 3" and can upgrade.

## 7. Common mistakes

- **Too big for the lot**: nothing is built ("its lot is too small"). Make it smaller, or ask for a bigger lot size for that kind.
- **No door or sign**: the building is turned the wrong way, or the village cannot register it.
- **A tier no dearer than the one below**: upgrades happen for nothing and the ladder is pointless. Make each tier cost visibly more.
- **Gaps in the tiers**: you cannot capture tier 5 before tier 4.
- **Built in the wrong palette**: it is not substituted, so a plains oak house captured for the desert stays oak.
