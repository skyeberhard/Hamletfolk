# Building library

The checklist for the template library behind R4.6 (and R8.3/R8.6): which buildings a village needs in
each of the five village biomes, which the game already ships, and which still have to be built.

- `- [x]` **Closed**: the piece exists, so there is nothing to author. Vanilla pieces are closed once the
  file is in the game; they still get checked in play when R4.6 places them (the Paper startup check lists
  any key the server cannot find).
- `- [ ]` **Open**: nothing ships, or the biome's ladder stops short. These are the ones to build.

Generated 2026-10-02 from `data/minecraft/structure/village/` in the Paper 26.2 jar, so every vanilla entry
is a real file. Keys look like `minecraft:village/plains/houses/plains_small_house_1`. Tick boxes as you go.

Biomes: plains, desert, savanna, snowy, taiga. Swamp, jungle and the rest have no vanilla village and are
out of scope here (a swamp stilt village is an idea for later).

## 1. What vanilla gives each biome

### Plains

**Housing**

- [x] House, small (tier 1): 8 (`plains_small_house_1`, `plains_small_house_2`, `plains_small_house_3`, `plains_small_house_4`, `plains_small_house_5`, `plains_small_house_6`, `plains_small_house_7`, `plains_small_house_8`)
- [x] House, medium (tier 2): 2 (`plains_medium_house_1`, `plains_medium_house_2`)
- [x] House, big (tier 3): 1 (`plains_big_house_1`)

**Food**

- [x] Farm: 1 (`plains_small_farm_1`)
- [x] Large farm: 1 (`plains_large_farm_1`)
- [x] Animal pen: 3 (`plains_animal_pen_1`, `plains_animal_pen_2`, `plains_animal_pen_3`)
- [x] Fisher cottage: 1 (`plains_fisher_cottage_1`)
- [x] Butcher shop: 2 (`plains_butcher_shop_1`, `plains_butcher_shop_2`)
- [x] Shepherd house: 1 (`plains_shepherds_house_1`)

**Trades**

- [x] Toolsmith: 1 (`plains_tool_smith_1`)
- [x] Weaponsmith: 1 (`plains_weaponsmith_1`)
- [x] Armorer: 1 (`plains_armorer_house_1`)
- [x] Mason: 1 (`plains_masons_house_1`)
- [x] Fletcher: 1 (`plains_fletcher_house_1`)
- [x] Leatherworker (tannery): 1 (`plains_tannery_1`)
- [x] Library: 2 (`plains_library_1`, `plains_library_2`)
- [x] Cartographer: 1 (`plains_cartographer_1`)
- [x] Temple (cleric): 2 (`plains_temple_3`, `plains_temple_4`)
- [x] Stable (optional): 2 (`plains_stable_1`, `plains_stable_2`)

**Village core and extras**

- [x] Town centre / meeting point (the bell): 4 (`plains_fountain_01`, `plains_meeting_point_1`, `plains_meeting_point_2`, `plains_meeting_point_3`)
- [x] Streets: 16
- [x] Street terminators: 4
- [x] Lamp: `plains_lamp_1`
- [x] Ruined ("zombie") versions of the above: 40 (for R8.8 ruins)


### Desert

**Housing**

- [x] House, small (tier 1): 8 (`desert_small_house_1`, `desert_small_house_2`, `desert_small_house_3`, `desert_small_house_4`, `desert_small_house_5`, `desert_small_house_6`, `desert_small_house_7`, `desert_small_house_8`)
- [x] House, medium (tier 2): 2 (`desert_medium_house_1`, `desert_medium_house_2`)
- [ ] House, big (tier 3): **none in vanilla for desert**

**Food**

- [x] Farm: 2 (`desert_farm_1`, `desert_farm_2`)
- [x] Large farm: 1 (`desert_large_farm_1`)
- [x] Animal pen: 2 (`desert_animal_pen_1`, `desert_animal_pen_2`)
- [x] Fisher cottage: 1 (`desert_fisher_1`)
- [x] Butcher shop: 1 (`desert_butcher_shop_1`)
- [x] Shepherd house: 1 (`desert_shepherd_house_1`)

**Trades**

- [x] Toolsmith: 1 (`desert_tool_smith_1`)
- [x] Weaponsmith: 1 (`desert_weaponsmith_1`)
- [x] Armorer: 1 (`desert_armorer_1`)
- [x] Mason: 1 (`desert_mason_1`)
- [x] Fletcher: 1 (`desert_fletcher_house_1`)
- [x] Leatherworker (tannery): 1 (`desert_tannery_1`)
- [x] Library: 1 (`desert_library_1`)
- [x] Cartographer: 1 (`desert_cartographer_house_1`)
- [x] Temple (cleric): 2 (`desert_temple_1`, `desert_temple_2`)
- [ ] Stable (optional): **none in vanilla for desert**

**Village core and extras**

- [x] Town centre / meeting point (the bell): 3 (`desert_meeting_point_1`, `desert_meeting_point_2`, `desert_meeting_point_3`)
- [x] Streets: 11
- [x] Street terminators: 2
- [x] Lamp: `desert_lamp_1`
- [x] Ruined ("zombie") versions of the above: 27 (for R8.8 ruins)


### Savanna

**Housing**

- [x] House, small (tier 1): 8 (`savanna_small_house_1`, `savanna_small_house_2`, `savanna_small_house_3`, `savanna_small_house_4`, `savanna_small_house_5`, `savanna_small_house_6`, `savanna_small_house_7`, `savanna_small_house_8`)
- [x] House, medium (tier 2): 2 (`savanna_medium_house_1`, `savanna_medium_house_2`)
- [ ] House, big (tier 3): **none in vanilla for savanna**

**Food**

- [x] Farm: 1 (`savanna_small_farm`)
- [x] Large farm: 2 (`savanna_large_farm_1`, `savanna_large_farm_2`)
- [x] Animal pen: 3 (`savanna_animal_pen_1`, `savanna_animal_pen_2`, `savanna_animal_pen_3`)
- [x] Fisher cottage: 1 (`savanna_fisher_cottage_1`)
- [x] Butcher shop: 2 (`savanna_butchers_shop_1`, `savanna_butchers_shop_2`)
- [x] Shepherd house: 1 (`savanna_shepherd_1`)

**Trades**

- [x] Toolsmith: 1 (`savanna_tool_smith_1`)
- [x] Weaponsmith: 2 (`savanna_weaponsmith_1`, `savanna_weaponsmith_2`)
- [x] Armorer: 1 (`savanna_armorer_1`)
- [x] Mason: 1 (`savanna_mason_1`)
- [x] Fletcher: 1 (`savanna_fletcher_house_1`)
- [x] Leatherworker (tannery): 1 (`savanna_tannery_1`)
- [x] Library: 1 (`savanna_library_1`)
- [x] Cartographer: 1 (`savanna_cartographer_1`)
- [x] Temple (cleric): 2 (`savanna_temple_1`, `savanna_temple_2`)
- [ ] Stable (optional): **none in vanilla for savanna**

**Village core and extras**

- [x] Town centre / meeting point (the bell): 4 (`savanna_meeting_point_1`, `savanna_meeting_point_2`, `savanna_meeting_point_3`, `savanna_meeting_point_4`)
- [x] Streets: 19
- [x] Street terminators: 1
- [x] Lamp: `savanna_lamp_post_01`
- [x] Ruined ("zombie") versions of the above: 39 (for R8.8 ruins)


### Snowy

**Housing**

- [x] House, small (tier 1): 8 (`snowy_small_house_1`, `snowy_small_house_2`, `snowy_small_house_3`, `snowy_small_house_4`, `snowy_small_house_5`, `snowy_small_house_6`, `snowy_small_house_7`, `snowy_small_house_8`)
- [x] House, medium (tier 2): 3 (`snowy_medium_house_1`, `snowy_medium_house_2`, `snowy_medium_house_3`)
- [ ] House, big (tier 3): **none in vanilla for snowy**

**Food**

- [x] Farm: 2 (`snowy_farm_1`, `snowy_farm_2`)
- [ ] Large farm: **none in vanilla for snowy**
- [x] Animal pen: 2 (`snowy_animal_pen_1`, `snowy_animal_pen_2`)
- [x] Fisher cottage: 1 (`snowy_fisher_cottage`)
- [x] Butcher shop: 2 (`snowy_butchers_shop_1`, `snowy_butchers_shop_2`)
- [x] Shepherd house: 1 (`snowy_shepherds_house_1`)

**Trades**

- [x] Toolsmith: 1 (`snowy_tool_smith_1`)
- [x] Weaponsmith: 1 (`snowy_weapon_smith_1`)
- [x] Armorer: 2 (`snowy_armorer_house_1`, `snowy_armorer_house_2`)
- [x] Mason: 2 (`snowy_masons_house_1`, `snowy_masons_house_2`)
- [x] Fletcher: 1 (`snowy_fletcher_house_1`)
- [x] Leatherworker (tannery): 1 (`snowy_tannery_1`)
- [x] Library: 1 (`snowy_library_1`)
- [x] Cartographer: 1 (`snowy_cartographer_house_1`)
- [x] Temple (cleric): 1 (`snowy_temple_1`)
- [ ] Stable (optional): **none in vanilla for snowy**

**Village core and extras**

- [x] Town centre / meeting point (the bell): 3 (`snowy_meeting_point_1`, `snowy_meeting_point_2`, `snowy_meeting_point_3`)
- [x] Streets: 17
- [ ] Street terminators: none (only matters if we place with jigsaw; skip otherwise)
- [x] Lamp: `snowy_lamp_post_01`, `snowy_lamp_post_02`, `snowy_lamp_post_03`
- [x] Ruined ("zombie") versions of the above: 33 (for R8.8 ruins)


### Taiga

**Housing**

- [x] House, small (tier 1): 5 (`taiga_small_house_1`, `taiga_small_house_2`, `taiga_small_house_3`, `taiga_small_house_4`, `taiga_small_house_5`)
- [x] House, medium (tier 2): 4 (`taiga_medium_house_1`, `taiga_medium_house_2`, `taiga_medium_house_3`, `taiga_medium_house_4`)
- [ ] House, big (tier 3): **none in vanilla for taiga**

**Food**

- [x] Farm: 1 (`taiga_small_farm_1`)
- [x] Large farm: 2 (`taiga_large_farm_1`, `taiga_large_farm_2`)
- [x] Animal pen: 1 (`taiga_animal_pen_1`)
- [x] Fisher cottage: 1 (`taiga_fisher_cottage_1`)
- [x] Butcher shop: 1 (`taiga_butcher_shop_1`)
- [x] Shepherd house: 1 (`taiga_shepherds_house_1`)

**Trades**

- [x] Toolsmith: 1 (`taiga_tool_smith_1`)
- [x] Weaponsmith: 2 (`taiga_weaponsmith_1`, `taiga_weaponsmith_2`)
- [x] Armorer: 2 (`taiga_armorer_2`, `taiga_armorer_house_1`)
- [x] Mason: 1 (`taiga_masons_house_1`)
- [x] Fletcher: 1 (`taiga_fletcher_house_1`)
- [x] Leatherworker (tannery): 1 (`taiga_tannery_1`)
- [x] Library: 1 (`taiga_library_1`)
- [x] Cartographer: 1 (`taiga_cartographer_house_1`)
- [x] Temple (cleric): 1 (`taiga_temple_1`)
- [ ] Stable (optional): **none in vanilla for taiga**

**Village core and extras**

- [x] Town centre / meeting point (the bell): 2 (`taiga_meeting_point_1`, `taiga_meeting_point_2`)
- [x] Streets: 16
- [ ] Street terminators: none (only matters if we place with jigsaw; skip otherwise)
- [x] Lamp: `taiga_lamp_post_1`
- [x] Decoration: 6
- [x] Ruined ("zombie") versions of the above: 37 (for R8.8 ruins)


Shared by all biomes (`village/common`): the well (`well_bottom`), an iron golem, and animals (cats,
cows, horses, pigs, sheep): all closed.

## 2. What vanilla does not have: to author

None of these ship with the game in any biome. Author each **once, in the plains palette**, and the
plugin substitutes materials per biome (section 3), so each is one build, not five. Two tiers each:
**T1** crude, **T2** solid. Naming for the saved file: `hamletfolk:<type>/<biome>/t<tier>[_<variant>]`, for
example `hamletfolk:mine/plains/t1_mountain`.

Size guide: build it on a flat test world in plain materials; keep the footprint within the lot sizes of
the vanilla houses (small is about 5x5 to 7x7, medium about 9x9) so a lot fits either.

### Needed by something already built or planned

- [ ] **Mine**: R2.3: miners, stone and metal so smiths can work. **Four pieces**: T1 and T2, each as a mountainside and a flatland variant (from the original mine-placement idea).
  - [ ] `hamletfolk:mine/plains/t1_mountain`
  - [ ] `hamletfolk:mine/plains/t2_mountain`
  - [ ] `hamletfolk:mine/plains/t1_flat`
  - [ ] `hamletfolk:mine/plains/t2_flat`
- [ ] **Guard post**: R5.1: guards. A small watch house.
  - [ ] `hamletfolk:guardpost/plains/t1`
  - [ ] `hamletfolk:guardpost/plains/t2`
- [ ] **Storefront / market stall**: R2.5: the merchant's workstation. A stall or small shop with a counter.
  - [ ] `hamletfolk:storefront/plains/t1`
  - [ ] `hamletfolk:storefront/plains/t2`
- [ ] **Treasury / counting house**: R2.6 and R2.7: where the bank counter is. Should look sturdy.
  - [ ] `hamletfolk:treasury/plains/t1`
  - [ ] `hamletfolk:treasury/plains/t2`
- [ ] **Storage (granary / warehouse)**: R3.10 limits are raised by storage buildings; a barn-like piece.
  - [ ] `hamletfolk:storage/plains/t1`
  - [ ] `hamletfolk:storage/plains/t2`
- [ ] **Lumberjack hut / sawmill**: The LUMBERJACK occupation has no vanilla workstation (R4.13 pairs it with a stonecutter).
  - [ ] `hamletfolk:lumberjack/plains/t1`
  - [ ] `hamletfolk:lumberjack/plains/t2`
- [ ] **Town hall (elder / steward office)**: R6.4 and R6.6.
  - [ ] `hamletfolk:townhall/plains/t1`
  - [ ] `hamletfolk:townhall/plains/t2`

### Needed for defense, water and growth beyond vanilla

- [ ] **Wall segment, gate and watchtower** (R5.2): `hamletfolk:wall/plains/t1`, `.../t2`, `gate`, `watchtower`
- [ ] **Dock / pier** for villages on water (R8.2 site scoring): `hamletfolk:dock/plains/t1`
- [ ] **Market square** upgrade for the town centre as the village grows (R8.3 stage 2)
- [ ] **Row house / terrace** for dense town stages (later; vanilla stops at the big house)

### Gaps in the vanilla house ladder

- [ ] House, big (tier 3) for **desert** (build it, or reuse another biome's with this biome's palette)
- [ ] Stable (optional) for **desert** (build it, or reuse another biome's with this biome's palette)
- [ ] House, big (tier 3) for **savanna** (build it, or reuse another biome's with this biome's palette)
- [ ] Stable (optional) for **savanna** (build it, or reuse another biome's with this biome's palette)
- [ ] House, big (tier 3) for **snowy** (build it, or reuse another biome's with this biome's palette)
- [ ] Large farm for **snowy** (build it, or reuse another biome's with this biome's palette)
- [ ] Stable (optional) for **snowy** (build it, or reuse another biome's with this biome's palette)
- [ ] House, big (tier 3) for **taiga** (build it, or reuse another biome's with this biome's palette)
- [ ] Stable (optional) for **taiga** (build it, or reuse another biome's with this biome's palette)

## 3. Palette map per biome

One authored piece becomes five by swapping block families at placement. These are **proposals**: tune by
eye when you see them. Mark a biome closed once its map looks right on a test build (edit the Status column).

| Biome | Walls / planks | Logs / frame | Roof | Floor / base | Status |
|---|---|---|---|---|---|
| plains (the authoring palette) | oak planks | oak logs | oak or cobblestone stairs | cobblestone | closed (the source) |
| desert | cut / smooth sandstone | sandstone pillars | sandstone stairs and slabs | sandstone | open |
| savanna | acacia planks | acacia logs | acacia stairs | stone bricks / terracotta | open |
| snowy | spruce planks | spruce logs | spruce stairs with snow layers | stone / packed ice | open |
| taiga | spruce planks | spruce logs | spruce stairs | cobblestone (mossy accents) | open |

## 4. Order to build in

1. **Mine, plains, T1 mountainside and flatland** (R4.6 v1 scope: one authored building).
2. **Mine T2**, then the **plains palette map** proven end to end on that one building.
3. Guard post, storefront, treasury and storage in plains (they block R2.5, R2.6, R5.1).
4. The remaining biomes' palette maps, then the gaps in section 2.

Vanilla pieces need no building at all; only R4.6's catalog and the Paper startup check.
