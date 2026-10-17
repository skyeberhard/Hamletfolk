# Playtest checklist (T112 to T131)

About twenty features are coded and unit tested but have never been seen by a player. This is the route through them, grouped by where you stand, with
what to look for. The full scenarios are in [TESTING.md](TESTING.md) under the T numbers. Tick as you go; for each failure write down the village, what you
did and what you saw (a screenshot helps). Whatever you find goes back as bugs, and a feature stays `Planned` in the roadmap until it has passed here.

Rough time: 90 to 120 minutes in all. Each part stands alone, so you can stop after any.

## 0. Before you start (10 minutes)

- [ ] **Back up the world and the villages.** Stop the server, copy `plugins/Hamletfolk/settlements.json` and the world folders, or use a copy of the server
      (`./gradlew runServer` uses `paper/run`). Save format is now 30: the first start migrates the saves, and an older plugin cannot read them again.
- [ ] Build and start: `./gradlew build`, then `./gradlew runServer`. In the console the plugin should report no errors.
- [ ] In `plugins/Hamletfolk/config.yml`: `dashboard.enabled: true`, `dashboard.allow-actions: true`, `brain.enabled: true` (for part 3), and keep the new switches at their
      defaults (`noticeboard.enabled`, `lights.lamps`, `stores.visible`, `homes.signs`, `trade.enabled` are all on).
- [ ] Op yourself, set creative or give yourself a night's worth of wood, stone and bread. Have a pickaxe, an axe, a hopper, and an iron ingot.
- [ ] Open the dashboard (`http://127.0.0.1:8123/`, or the port in the config) in a second window.
- [ ] Useful commands: `/settlement` (info), `/settlement residents`, `/settlement households`, `/settlement history`, `/settlement plan`, `/settlement lots`,
      `/settlement survey`, `/settlement admin warp <village> <days>`, `/settlement admin fastforward <village> <speed|stop>`, `/settlement admin noticeboard|stores|homes|trade <village>`,
      `/settlement admin regrade streets <village>`, `/settlement admin replan <village>`, `/time set <ticks>`, `/gamerule advance_time false`.

## 1. At the dashboard, no walking (20 minutes)

- [ ] **T126, T127 worlds.** The tray lists your worlds with lights; click one: its page, Players, Villages, Feed and Time tabs. Walk into and out of a village and watch the 👤 chip and the Players tab change.
- [ ] **T129 world time.** On a world's Time tab save a speed of 3, watch a village's day count move, restart the server, check it is still 3, then set it back to 1. Warp now with 2 days.
- [ ] **T117 warp all.** Overview, Warp with 2 days (confirm). Every village moves 2 days; the console says how many; 31 days is refused.
- [ ] **T112 plan overrides.** A village's Plan tab: keep a lot empty, open it again, set an unbuilt lot aside for another kind, forbid then allow a kind, order a house next.
- [ ] **T119 outlook.** A village's Outlook tab and its stat cards: days left, net a day, and the chips on the overview. `/settlement` in a village short of tools: the status line says why.
- [ ] **T128 books.** A village's Treasury, Stores and Quests tabs: balance and room, each store with its barrel, open requests.

## 2. At one village with a square and a bell (30 minutes: Foxhaven or Stonehollow)

- [ ] **T122 notice board.** Within 64 blocks for ten seconds: a lectern beside the bell, facing it, with a "Notice Board" book (cover, WANTED, BUILDING, NEWS). Take the book out and wait: it comes back. A jobless villager may claim the lectern as a librarian: tell me if one does.
- [ ] **T124 stores.** Barrels beside the granary, smithy and so on with the right items. Open one (a notice that taking is theft). Take a stack of bread (standing falls, history says so, `/settlement info` stock drops). Put an iron ingot in the tools barrel (refused) and the metal one (accepted, a donation). Put the bread back (credited, no payout). Try a hopper under a barrel, and breaking one (nothing drops, standing -5). `/settlement donate` the marked bread (refused). Look for ghost items on your cursor after a shift-click.
- [ ] **T130 house signs.** The sign by each house door shows `<Family> house` and who lives there on lines 3 and 4. Write your own words on one sign: it stays.
- [ ] **T125 marriages.** `/settlement admin fastforward <village> 20`, then stop it; `/settlement residents`, `/settlement households`, `/settlement history`. Pairs are married; households under the wife's family name; a married villager mentions their spouse when you talk to them.
- [ ] **T123 lamps.** A village with street lights (or one that has been attacked): copper bulbs on the posts, dark by day; `/time set 13000` and they light; `/time set 1000` and they go out; rain keeps them lit.
- [ ] **T121, T114 signs.** A shop or smithy has a hanging sign over its door reading `[Shop]` and a name on two lines, both sides alike; a farm has a sign on a fence post; a house has no `[House]` sign but is still listed by `/settlement buildings`.
- [ ] **T115 visible work** (brain on). In the morning (`/time set 2000`), stand within 48 blocks of a farm, a mine or a smithy: a resident walks to the building and swings. `/settlement brain debug on`, then `inspect` one. `/time set 13000`: they go home.

## 3. Terrain: slopes, cliffs, gaps (20 to 30 minutes)

- [ ] **T120 terrace** (Hollowgate, Oakford or any steep village). `/settlement admin replan`, open the Plan tab (rough lots are dashed), order a house or smithy next on a rough lot, and watch: a level cut-out, a stone-brick retaining wall on the filled edge, the cut face armoured, nothing beyond a block or two changed. `construction.speed: 0.2` slows it so you can watch.
- [ ] **T118 street stairs.** In a village with streets over a slope: Admin tab, Check streets (or `/settlement admin regrade streets <village>`). The history says how many blocks; a minute later it has finished; a staircase of cobblestone stairs with no jump, hollows filled, a narrow stream bridged. A second press while it runs is refused.
- [ ] **T113 bridges.** A village whose street meets a gorge or river: give it something to build nearby; a plank deck at bank height with rails and a lamp at each end. No deck over a gap wider than 16.
- [ ] **T116 survey.** `/settlement survey` where you stand in a desert, a swamp, hills, a plain, a forest, by a lake, on the coast and at sea. Plains and forest 60 to 70s; a desert or open sea under 30; stone and ore high on land and nil at sea.

- [ ] **T132 map layers.** A village's Plan tab: switch on Slope (a hillside village: its rough lots sit on orange and red), Districts (named outlines with lots built) and Homes (a ring per house in its family's colour; hover a house for who lives there). Check a village you are not near: no slope yet, then some after you visit.

## 4. Trade (10 to 15 minutes)

- [ ] **T131.** Two villages within 800 blocks. Let one build a surplus and the other run short (give one a lot of bread, the other none). `/settlement admin trade <village>`; read both histories. Without a merchant a fifth is lost on the road; starve one: the other sends food unpaid; only the difference in emeralds moves. Try `trade.enabled: false` and restart: no caravans by themselves.

## What to send back

For each failure: the T number, the village, the exact steps, what you saw against what the table says should happen, and the server log lines around it (`logs/latest.log`).
Also worth saying even when it works: anything that looks odd, reads badly (names, sign text, the notice board), is in a bad place (a barrel blocking a door, a lectern on a roof),
or is slow (a stall when you press Warp, lag near the terraces).

When something passes, tell me and I will set its row to Done in the roadmap.
