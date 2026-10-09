# Changelog

Each entry lists the roadmap items it delivers. See [ROADMAP.md](ROADMAP.md).

## Unreleased

- R4.32 (coded, not yet played: T75): the five remarks that were still one sentence with a tone around it now have lines of their own in each tone: the land (by the village's leaning), the village's mood (by its temperament), its size, a villager's own money (by how much they have put by, with their own words if they are out of work) and a parent still in the village. The fact comes from new vocabulary files (`vocab_land.txt`, `vocab_mood.txt`, `vocab_size.txt`, `vocab_wealth.txt`, like `vocab_trade.txt`; a server file replaces the entry of each key it mentions) and `land.txt`, `mood.txt`, `size.txt`, `wealth.txt` and `parent.txt` say it differently for the warm, the gruff, the anxious, the proud, the dry and the gentle, three or more ways each. If a vocabulary entry or the lines are missing the old plain sentence is said, in a tone wrapper as before. Remarks about being out of work, a builder's or guard's day, and the like stay as tone-wrapped sentences.
- R4.33 (coded, not yet played: T76): street grading knows which way a street runs. The grader guessed a piece of street's direction from its shape (the longer side), and the piece graded when a building is finished is the street clipped to a window round it, which can easily be wider than it is long even for a north-south street: its slices were then cut across the wrong axis and it was levelled across its length instead of along it. Each piece now carries the direction of its street from the plan (and the walkway from a door its own direction), and a short extension of the main street is no longer misread.
- R8.14 (coded, not yet played: T74): the plan grows to four stages, and each is a district. A village's plan lengthened its main street and added two branches and lots once, at 25 residents; it now does the same again at 50 (stage 3) and at 100 (stage 4), each further out, only once the ground it needs is loaded and measured (for stages 3 and 4 a strip 140 or 168 blocks along the main street and 96 to either side, so an ordinary view distance is enough), and the history says when it has outgrown its streets. Each stage is a named district: the old town, the new quarter, and then districts named for what their lots hold (the farm quarter, the market quarter, the mining quarter, the smiths' quarter, the garrison quarter, the housing quarter; a second of the same kind is "the outer ..."). `/settlement plan` lists them with their lots and the wall round each, and `/settlement lots` shows the district you are standing in on the action bar. The wall round an earlier stage stays where it is: a rampart (R5.10) is never taken down, so it is the boundary between districts; a plain fence is still moved out with the ring (R5.8). **Also fixed:** a saved plan that had grown to stage 2 was dropped as damaged when the server next loaded it (the loader insisted on exactly one main-street piece, and a lengthened street has several), so a village would have lost its lots at the first restart after levelling up; the loader now accepts any number of pieces. A short extension of the main street also keeps the street's direction for lights and gates (it was read from its shape), and a fence that a later ring replaced is no longer reported as standing. Plan stages 3 and 4 need no new save format.
- R4.31 (coded, not yet played: T73): voices for the rest of what villagers say. Every remark that had no lines of its own (their trade, the land, the village's history and mood, size, money, parents, being out of work) is now said in the speaker's tone: the sentence is unchanged and the tone wraps it ("Hmph. ...", "... I hope that's all right to say."). Talk about their own trade uses the trade's words, from a vocabulary file (`vocab_trade.txt`: a farmer sows and reaps wheat and carrots, a miner works the seam, 21 trades), and an expert or master (level 4 and up) has lines of their own that count their days at it (only a master, level 5, is now made proud by it; an expert keeps their own tone). When a player talks to a villager in the overworld, they may remark on the weather (clear, rain, thunder) and the time of day, and when the player walks more than eight blocks away (or leaves) the villager says goodbye in their voice. New files: `remark.txt`, `work.txt`, `work_expert.txt`, `weather.txt` and `vocab_trade.txt` in the plugin, replaceable per tone (and per trade) from `plugins/Hamletfolk/dialogue/`; a slot can be written `{Name}` for a capital at the start of a sentence. Still plain statements under a tone wrapper rather than lines of their own: land, mood, size, wealth, parents and the idle remarks.
- R8.15 (coded, not yet played: T72): the rest of the design table's trades open on a real trigger, each at a building that holds its workstation. A fletcher (16 exposed gravel and 3 chickens within reach, village of 8) at a bowyer; a mason (40 exposed stone within reach and a registered mine, village of 8) at a masons' yard; a weaponsmith (a smithy, and an attack in the last three months, village of 8) and an armorer (a smithy, 20 metal in the stores, and a martial village or a town of 20) at an armoury, which employs both; a cleric (a town of 20) at a chapel. The planner asks for each building when its trade opens (an armoury once, for either trade), it costs its workstation's materials (fletching table, stonecutter, grindstone, blast furnace), and the trade works a quarter better there. The survey now also counts exposed gravel and stone. A building can employ more than one trade (`BuildingType.jobs()`). Save format 28. No brewing stand in the chapel (it needs a blaze rod, which only comes from the Nether), so a cleric does not yet need one.
- R8.13 (rest: coded, not yet played: T71): three more leanings, read from what the survey counts and only where the biomes gave nothing that stands out (a forest village stays a timber one): scholarly (16 sugar cane and 4 cattle within reach), craft (60 sand) and trading (four or more kinds of thing within reach: water, sheep, cattle, horses, cane, sand, bees), in that order of precedence. A scholarly village's signature is a map room (and a library once it is a town of 20, as its librarian trade opens), a craft village's a glassworks, a trading village's a trading post; their trades are staffed sooner, the buildings that serve them are improved first, and the history and the villagers' talk mention it. The new leanings are numbered after the existing ones, so saved leanings are unaffected. With this and the part above, every building the issue names exists: harbour, pens, stable, map room, library, glassworks and trading post, each with an effect (a quarter more from its trade, or a merchant's extra sale). The fletcher, mason, weaponsmith, armorer and cleric triggers of the design table are not part of this item and are not built.
- R8.13 (part: coded, not yet played: T70): the land opens trades. Every ten village days, in the chunks that are loaded, the plugin counts what is within 64 blocks of the village's centre: sheep, cattle, pigs, chickens, horses and bees, and the surface sugar cane, sand and water (the highest count ever found is kept, so a survey with unloaded chunks cannot close a trade; `/settlement info` shows it). Enough of any of them, once the village is the size for it, opens a trade: fisherman (40 water), shepherd (4 sheep), butcher (6 cattle, pigs or chickens), leatherworker (4 cattle), horse trainer (3 horses), beekeeper (2 bees), cartographer (16 sugar cane), librarian (8 sugar cane and 2 cattle, in a town of 20) and glassblower (30 sand); the history says so. Each is worked at its own building, which the planner then asks for and the village builds: a harbour, pens, smokehouse, tannery, stable, apiary, map room, library or glassworks, each holding its trade's workstation (barrel, loom, smoker, cauldron, cartography table, lectern) and priced by it. The beekeeper, horse trainer and glassblower are new trades. With its building a trade works a quarter better. A fishing village's signature building is now a harbour, a pastoral one's the pens. A trading post (asked for once a village of 12 has a shop and a merchant) gives each merchant one more sale a day. Save format 27. Still to come under R8.13: the scholarly, craft and trading leanings, and what each trade sells and does beyond making goods.
- R5.11 (coded, not yet played: T69): an admin can give the rampart their own gatehouse and tower. `/settlement admin capture pos1`, `pos2`, then `/settlement admin capture gatehouse|tower [2|3] [style]` saves the build between the corners for rampart tier 2 (planks) or 3 (stone), like any captured building (`remove`, `list` and `build gatehouse|tower [tier]` work too). From then on the village builds that instead of the generated pillars and lintels (a gatehouse at every gate, centred on it, turned so its outside faces away from the village) or the generated corner towers (a tower at each corner, its outer corner on the ring's corner, turned for each corner). Build a gatehouse with its passage north to south and its outside to the south; build a tower as the north-west one. The parts are paid for block by block from the stores like the rest of the wall; a tier with no capture still gets the generated ones; wall columns under a part's footprint are left out; a block goes only on air, growth or the rampart's own earlier work, so a player's building on the ring leaves a gap. Gatehouse and Tower are new building types that exist only as captured templates (a sign cannot register them). Nothing about villager behaviour changes: the parts are just buildings.
- R4.30 (coded, not yet played: T68): villagers speak in their own voices. A voice is worked out each time and never saved: a tone (warm, gruff, anxious, proud, dry or gentle) from the resident's sociability, bravery, ambition and work ethic plus a little that is theirs alone, drawn from their id; shifted by mood (a starving warm villager turns gentle, a gloomy one with a full larder lightens), age (children are excitable), level (an expert or master speaks with authority) and the village's temperament (wary villages are drier, martial ones blunter, welcoming ones warmer); and one habit of speech from their id ("Aye.", "Mark my words.", "Or so they say.") that comes up in about half their lines. Greetings (stranger, known, friend, and the five reputation ones), farewells, hunger, plenty, thin stores, a blocked trade, worn tools, a death, danger, a request, children, elders, memories of a raid, a gift or a hard time, a neighbour by name and trade, and the village's latest plan are now said in that voice, from 23 plain text files with three lines in each of six tones. The files ship in the plugin (`dialogue/*.txt`) and a file of the same name in `plugins/Hamletfolk/dialogue/` replaces the lines of each tone it mentions, or adds to them with a leading `+`; `/settlement admin dialogue` reloads them and lists any bad line. A villager never says the same thing twice running. Wealth, land and temperament remarks, the trade lines and the parent line are still in plain wording. Goodbyes exist (`Dialogue.farewell`) but nothing in the Paper layer calls them yet.
- R5.10 (coded, not yet played: T67): walls grow stronger. A village that has been attacked in the last three months strengthens its fence a tier at a time once the fence stands: a town (20 residents) raises a rampart, planks two high under a fence rail, a gate of two log pillars and a lintel where each street leaves the ring, and a watch tower at each corner (planked walls, a ladder in the hollow up to a platform, a parapet along the two outer edges, a torch, footed on cobblestone where the ground falls away); a city (50) rebuilds it in stone bricks three high under a wall with stone pillars and taller towers. Each tier is built over the last. It is paid for per block (the footings under towers and pillars are free), never builds over anything but what the tier below put there, so a player's blocks on the ring leave a gap, steps aside after twenty days without materials, and does not hold the village's other building up. When the plan grows a stage the fence for the new ring goes up outside and the rampart inside it stays as an inner wall. Save format 26.
- R8.12 (coded, not yet played: T66): a village's leaning calls for a signature building once every need is met and it has eight residents, one of each: a sawmill for a timber village (its lumberjacks make a quarter more wood), a forge for a mining village (each smith smelts four more ore a day), a granary for a farming or pastoral village (food spoils at 1% a day instead of 2%). Each has a generated design in two tiers (a forge of stone, a granary with bales of hay), takes a free lot on the plan, can be registered with a sign like any building, is improved first by a village of its leaning, and is explained by `/settlement plan`. A fishing village has none until the harbour of R8.13. Save format 25.
- R8.11 (coded, not yet played: T65): villages have a character. The land round a village is read once, from the game's biomes (nothing is generated), and gives it a leaning: timber (forest), mining (stone and ore), farming (farmland), fishing (water), pastoral (grazing) or all-round where nothing stands out. The trades of a leaning that a village can staff (lumberjack, miner, mason, farmer) are staffed while the stores are still a quarter above what is wanted, never ahead of a real shortage; a timber or mining village of eight keeps a second lumberjack or miner, and the leaning decides which buildings are improved first, ahead of last week's output. Its last two months give it a temperament, read fresh each time so it fades as they pass: hard-pressed (a famine now or two lately; improves every fortnight), martial (attacks on three different days; one more iron golem), wary (a lost raid; newcomers every six days), prosperous (100 emeralds and 20 food a head; improves every four days), welcoming (three arrivals; newcomers every two days), otherwise steady. Its size gives it a stage (hamlet, village from 8, town from 20, city from 50). `/settlement` and `/settlement plan` name them and say what they change, villagers speak of them, and `/settlement admin replan` reads the land again. Villages from an older save read their land the first time they are simulated. No save-format change.
- R4.29, R5.9, R4.25 (coded, not yet played: T62 to T64). R4.29: every trade has levels from the days worked at it (novice, apprentice at 10, journeyman at 30, expert at 60, master at 100; experience stays with the trade it was earned in, so a master farmer drafted as a builder for a spell is still a master on their return, and working at a different trade starts again). Each level makes a gatherer or craftsman produce a tenth more, a smith smelt two more ore a day, a merchant sell a batch more every two levels and a guard count for more; a loaded villager walks a little faster, a guard has more health, and a vanilla trade's level is raised to match so its trades unlock. `/settlement residents` shows levels; the history records reaching master. Save format 24. R5.9: a village that has been attacked keeps one iron golem per ten residents (at least one), forged by a smith from 36 iron and a pumpkin's worth of produce, at most one every five days; the golem appears at the guard post or centre when it is loaded, defends villagers like the game's own, and is forged again if killed. `/settlement` shows them. R4.25: lumberjacks' wood and miners' stone are owed to the world (capped at 256 each) and paid off where the ground is loaded: whole trees the game grew are felled near the village, outside its plan, and a sapling planted; a fenced quarry five blocks square is dug beside the [Mine] a layer at a time, keeping the ore it finds (coal, raw iron, copper and gold, redstone, lapis, diamonds) on top of the miners' table. Only natural trees: a stripped log, a log with anything built against it, and a tree with no growing leaves at its crown are never felled; nothing is dug or felled beyond what is owed, nothing in the plan or an exempt zone (checked again every pass, so a village that grows over its quarry stops digging), and nothing next to water or lava. A golem that is removed for any reason but being unloaded is forgotten. With `construction.unattended` the quarry's chunks are held loaded while stone is owed. `world-marks.enabled` turns it off.
- R3.16, R3.17 (coded, not yet played: T61): a finer ledger. The stores now hold commodities inside each category (food: produce, fish, meat, cooked food, grain, bread; wood: planks, logs; stone: cobblestone, gravel, sand, clay, stone blocks, bricks; metal: raw and smelted iron, copper and gold, redstone, lapis, diamonds; a new FUEL category: coal and charcoal; tools: stone, iron, diamond; goods: wares, wool, string, leather, paper, glass, books). Category totals are the sums, so every rule reads what it did before. Donations and trades store the item's own commodity (coal, sand, gravel and clay can now be donated); villagers eat produce, fish and meat before grain and bread, spoilage takes fresh food first, tools wear out cheapest first, and iron is the last metal thrown away at the storage limit (or sold, or charged for a building), so the smiths keep theirs. `/settlement` lists what each category holds. Miners bring up coal and ore (iron, copper, gold, now and then redstone, lapis or a diamond); smiths smelt up to 8 raw ore a day for one fuel per eight and make iron tools, or stone tools from cobblestone when there is no iron; a village with a smith, a lumberjack and ore waiting burns logs into charcoal when fuel is short; `/settlement plan` says when a smith has raw iron but no fuel; farmers bake 2 grain a day into bread; a birth eats 6 bread. Vanilla pieces with sand, gravel or clay in them now cost stone for those blocks. Save format 23: an old save's food becomes bread, wood planks, stone cobblestone, metal iron, tools iron tools and goods wool.
- R1.31, R4.26, R4.27, R4.28, R5.8 (coded, not yet played: T57 to T60). R1.31: `/settlement admin job <occupation>`, looking at a villager, sets its trade and pins it (the simulation never drafts, calls up, releases or reassigns a pinned resident) until `/settlement admin job unpin`; `/settlement residents` marks pinned residents. A resident's villager that has stood walled in for 10 minutes in a pit open to the sky (never a roofed room or trading-hall cell, nor one that is asleep, trading, leashed or riding) is moved to the village's centre, and `/settlement admin unstick [name]` does it at once; `villagers.free-stuck: false` turns it off. A pinned resident keeps their trade and pin when their villager settles in another village, and a pinned jobless one is not given work. R4.26: a village builds the house design with the most beds for its cost, and only upgrades a house to one with more beds; the plan says how many beds it is short of. R4.27: `/settlement admin fastforward [name|all] <2-100|stop>` runs villages that many times game speed until stopped or a restart (at 100 times a game day passes in 12 seconds), builders included, capped at 200 blocks a pass. R4.28: a village with 24 food a head, a free bed and a couple has a child every three days at most, near a player or not, for 6 food; the history names the parents, and the child's villager appears at the centre once it is loaded. A child with no villager yet is not a parent, does not migrate, and keeps its age when it gets one. R5.8: when the plan grows a stage the village lights its new streets (paying only for the new lights) and moves its palisade out, then takes down the posts of the ring it replaced (an oak post on that line, with another beside it and no torch on it) for half their wood. Save format unchanged.
- R5.6 (coded, not yet played: T57), from the 2026-10-07 playtest: after the first attack on a village (a villager lost to a monster, or a raid) it wants lights, a fence post with a torch on it every sixth block along its streets and just outside the corners of the square, and once they stand a palisade, a one-block fence ring round its lots and streets with a gap at each end of every street. A work that cannot place its last posts for six village days (an unloaded chunk, an exempt villager) is finished with gaps rather than blocking every other building; its record is kept so the village does not ask for it again. They are built like any building, paid for from wood, ahead of anything the village merely wants (a village that cannot pay yet saves up, takes on a lumberjack and says what it lacks); a growth want (a shop) or the town square no longer takes the wood the lights need. A work has no lot and no sign. Save format 22. R4.22 correction: a warp now puts the village's clock a fixed number of days ahead of the world's, which it then keeps pace with, instead of standing still until the world catches up; unattended building loads a village's plan at most once a village day; a street's grading leaves alone a column with a torch, rail, sign or trunk on it and fills nothing above one it cannot change, the walkway meets the street's height, and the square is levelled too.
- R4.22, R4.23, R4.24, R5.7, R3.15 (coded, not yet played: T53 to T55), from the 2026-10-07 playtest. R4.22: `construction.unattended: true` lets villages build with nobody near: the chunks a village needs are loaded only while it decides (once a game day) and while a project is open, then let go; `construction.speed` multiplies how fast builders work; `/settlement admin warp [name] <days>` runs a village up to 60 days ahead at once (from the console too), its clock then running ahead of the world's. R4.23: streets are graded as well as paved: a hole or dip along a street (up to eight blocks) is filled, a short bump cut, no step is more than one block, and water up to six wide gets a plank bridge; a column with something built on it is left alone. R4.24: a village of four or more keeps a lumberjack, and a miner once a mine has room, whatever it holds; a lumberjack, miner or mason rests once the stores are full of what it makes and goes back to work below nine tenths of the limit (`/settlement residents` shows it). R5.7: a village attacked in the last week, or with danger of 30 or more, takes in no newcomers and no migrants. R3.15: the emeralds a player pays a villager for goods go into the village's treasury. Save format unchanged.
- R4.20, R4.21 (coded, not yet played: T52), from the 2026-10-06 playtest, where two villages waited 50 and 80 days for a house needing 52 wood while holding 32 to 35, with every resident farming. Building now creates work: a material a wanted building lacks counts as short whatever the stock per head, so a jobless resident becomes a lumberjack (or a miner, where a mine has room), and with nobody jobless one worker a day is moved over (an idler first, a food worker only from a full larder, never the only lumberjack, mason, miner or smith, who are now also never called up as guards). An idle builder is the first one moved. Once fed, a village with no mine builds one right after its town square, and gets on with houses if it cannot yet (a mine it has no way to pay for never holds anything else up). A need it cannot pay for is saved up for instead of spending on a wanted upgrade, and a wanted upgrade no longer needs twice its cost when the storage limit is lower than that. Crops on a new farm cost nothing, so a hungry village can plant its first farm, and a famine keeps the farm's builder at work. Builders keep the trade between buildings (back to other work after a week with nothing to build) and get faster: builder at 3 buildings, journeyman at 6, master at 10, placing 4, 6, 8 and 10 blocks a second; `/settlement projects` shows it. Save format 21.
- R4.19 (coded, not yet played: T50): upgrades now mean something. Each tier above the first gives a work building two more places (a shop one more), and a need the village cannot meet with a new building (no lot, or it cannot pay) is met by improving one it already built. Once every need is met it develops a direction from what it makes and trades (farming, timber, mining, craft or trading) and improves the buildings that serve it first, at most one a week; `/settlement plan` says which.
- R4.7 / R8.3 (coded, not yet played: T50): the village now builds its town square (the game's own town-centre piece, with the bell) once its food is covered, then everything else. New buildings use the plainest design the stores can pay for, and a building is only improved to the next tier when nothing else is lacking, it has stood five days, the stores hold twice the cost, and no upgrade was made in the last week. Plans are zoned: shops and the treasury stand near the square, houses a little further, then the smithy, with farms, the mine and the guard post on the outskirts (a village keeps the plan it already has until `/settlement admin replan`). `/settlement lots` draws each kind of lot in its own colour, with a legend, instead of white for everything.
- R8.10 (coded, not yet played: T51): admins can found villages. `/settlement admin found [name] [residents]` creates a village where the admin stands, spawns its founders (1 to 20, six by default), gives it the founding food and a kit of wood and stone, and plans its streets and lots, so the planner and builders take it from there; it refuses near another village (twice the settlement radius), in an excluded world or in an exempt zone. `/settlement admin replan [name]` plans a village again.
- R4.7, R4.8 (coded, not yet played: T50): villages build. When the planner says the village lacks a building and a lot is reserved for it, the village queues a project (the best tier its stores can pay for that fits the lot; a larger tier later replaces a building the village built itself, never one a player made), a jobless adult takes it on as the new BUILDER occupation, and the Paper layer clears the site, then pays for and places about four blocks a second from the ledger while a player is within 128 blocks. The remaining work is worked out from the world each pass, so it survives a restart; payments in flight are saved. A site with something a player built on it is given up, nothing is built next to an exempt villager, and `construction.enabled` turns it off, and `construction.cost-percent` (default 35) is what villagers pay as a share of the crafting cost of the blocks, because the game's own buildings (a smithy is about 280 wood) cost more than a village's stores can hold. `/settlement projects` shows progress; `/settlement admin cancelproject` stops one. Save format 20. A finished building lays path along the planned streets and the square near it and a walkway from its door to the street. Buildings are turned so their entrance faces the nearest street or the square, and set at the median height of their ground, which is levelled (cut and filled with a blended edge, never over anything built) before the first block. Blueprint diffs now compare only the properties a blueprint names, so a finished building reads as complete.
- R4.6 / R4.18 (not yet done): buildings as block lists (`Blueprint`) with a resource cost from their blocks, a block diff against the world, and per-biome material substitution (`BiomeSet`); generated plains-palette mine, guard post, shop and treasury in two tiers (`BuildingGenerator`); and a `TemplateCatalog` of tier ladders per type and style (vanilla keys, generated, or captured builds that replace them) that picks the best affordable tier. The Paper half: a startup check that every vanilla piece the catalog names exists (25 of 25 on 26.2), vanilla pieces read into blueprints, `/settlement admin capture pos1|pos2|<type> [tier] [style]|remove|list` (saved as text files in `plugins/Hamletfolk/templates/`, no save-format change) and `/settlement admin build <type> [tier] [style]` to put a template down and register its sign. Coded and compiled but not played (see T49); nothing builds on its own until R4.7/R4.8.
- R1.30: A sign now exempts villagers. A sign reading `[Exempt]` (or `[Ignore]`) leaves the villagers within its radius
  alone, anywhere, in a village or not: the radius is the number on the second line (24 blocks if none, from 4 to 64).
  An exempt villager is never enrolled in a settlement, named, tagged for appearance, re-priced, refused a sale, migrated
  or re-homed, and does not count toward any population, so trading halls and shop rigs keep working as vanilla. An
  ordinary player's sign only exempts villagers that are not already part of a village (so a sign cannot be used to take a
  village's people out of it); a sign placed by someone with `hamletfolk.ignore.village` (ops by default) exempts everyone
  inside, and releases villagers already in a village (their record is removed, they lose the name this plugin gave them and
  its appearance data, and a name an owner chose stays). Breaking the sign ends the exemption (a sign destroyed any other way is
  noticed). A player may have at most five signs (100 in a world); editing someone else's counts as one of yours. No newcomer is
  spawned into an exempt zone and nobody is migrated into one. `/settlement admin ignore`, run while looking at a villager,
  toggles the exemption for that one villager. The zones are kept in the world's own data. No save format change. The rules are
  in core and tested; the sign and the villager checks are Paper-side, checked by T48.
- R8.4: Terrain pads. For a lot, given the heights of its footprint and a buffer around it, a pure function works out the
  grading as a plan of blocks: a target height (the median of the footprint), the blocks to cut above it, the fill below it
  (soil under the surface, foundation below that) running all the way down to solid ground, through shallow water if the
  lot is wet, so nothing floats, and a blended edge that climbs or falls no more than a block per block from the pad out
  to the natural ground. A lot that needs more than eight blocks of cut or fill, stands in water deeper than three, or is on
  ground nobody measured is refused. Roads get a profile that rises or falls at most one block a step, with stairs for a
  single step and slabs inside a longer slope. Nothing is placed yet: this is the plan that placement (R8.6) will carry out,
  when a building is built and not at founding. No save format change; tested in core only (heights in, blocks out).
- R8.3: Villages now have a plan of streets and lots. Once the ground around a village is loaded (every chunk within
  112 blocks), it is given a 15 by 15 main square at its centre, a main street through it along whichever axis is
  flatter, two branches across the street, and reserved lots along both sides of every street, each tied to a
  building type (houses, farms, a shop, a mine, a smithy, a treasury, a guard post), a biome set (plains for now) and
  a stage. Lots on ground steeper than four blocks, or wet, or not measured are dropped for good, and streets stop at
  the shore. Nothing overlaps. Growth fills the nearest reserved lot of a kind first (registering a building sign inside a matching lot
  marks it built on), and the planner's advice to put up a building now names the lot it would go on. Streets only
  run where every column of their width is dry, and a branch only exists where the main street reaches. At 25 residents the plan grows to stage 2 (a longer street, two more
  branches and their lots) without touching what is already there. `/settlement plan` summarises the layout and
  `/settlement lots` switches an outline of the lots near you on and off (white reserved, green built on, pale for the
  square and streets, redrawn as you walk) so you can see the plan in the world and edit it from there. Save format is now 18 (the plan). The rules
  are in core and tested; measuring the ground and the particles are Paper-side, checked by T47.
- R8.2: Village sites can now be scored. A pure function takes a coarse sample of the land around a point (the
  biome every 24 blocks out to 96, from the game's computed biomes, so nothing is generated) and scores how much
  water, timber, farmland, grazing, stone and ore is within reach, with nearer land counting for more. It is a
  score, not a gate: a missing resource becomes something the village will have to bring in (food, wood, stone or
  metal), and the profile gives the starting conditions it implies (growth fast, normal or slow by how much has to be
  brought in, and a food balance). An oasis, a swamp, a mountain with springs and a bare plain each come out as you
  would expect in the tests. New command `/settlement survey` scores the ground where you stand, in a village or not.
  No save format change. The scoring is in core and tested; the sampling is Paper-side, checked by T46.
- R8.1: Each village now has a planner. Once a day it reads the stores and the village's needs and works through
  four tiers in order, food, shelter, safety, then trade and growth, and does not move on until the one before is
  met. A tier counts as met at full cover and slips at 70%, so priorities do not flap with a day's luck. For a
  shortage it walks back to the root of the problem (tools need a smithy, which needs metal, which needs a mine)
  and says what is actually missing: a building to put up, jobs to fill, or something to bring in. Every decision
  is written with its reason to a log of up to 100 lines kept apart from the history (the same decision is not
  repeated within ten days), and a resident will mention the latest one in conversation. `/settlement plan`
  shows what the village wants next and its recent decisions. A village that has been visited (a command or a
  conversation in it) but not for a week plans on every third day; one nobody has ever visited plans every day. Beds
  that have not been counted yet are not treated as no beds. It is advice for now: lots and the builder that act on it come later (R8.3, R4.8). Save format
  is now 17 (the decision log). The rules are in core and tested; the command is Paper-side, checked by T45.
- R5.5: Guards now respond to a pattern of attacks, not only to peak danger, replacing R5.1's three-day wait. The
  village remembers each attack on it (a resident killed by a monster, one turned into a zombie, a raid coming or
  being lost). Two attacks in a week make it wary (one guard per fifteen residents, at least one), three or more,
  or danger of 50, make it alarmed (one per ten), and danger of 80 puts it under siege (one per six). The first
  guard is called up the same day, then one a day, and under siege all of them at once, even in a famine (a
  village that is only wary or alarmed still will not draft anyone while food is short). A guard needs tools in
  the stores to count as armed and to calm the village; food is no longer needed, though a guard still eats an
  extra ration when there is one. Guards stand down, one a day, after ten days with no attack and danger below 10.
  `/settlement` shows an "Alert" line while the village is wary or worse. In the first playtest's Mossmoor this
  would have put a guard on watch on day 27, a week before the raid. Save format is now 16 (the days of the recent
  attacks). The rule is in core and tested; recording the attacks is Paper-side and checked by T44.
- R2.2 (fix): Beds are now counted through the game's points of interest, the registry villagers use to claim
  a bed, instead of through block entities. Minecraft 26.2 beds are no longer block entities, so the old scan
  found none and every village showed "0 beds". New command `/settlement beds` lists the beds in the village's
  area, which are claimed and which are free, with coordinates and distance, nearest first. The count is a
  sphere around the village centre, looking 48 blocks above and below the surface there, so beds further above or
  below are missed. Bed counting only runs while the village centre is loaded; the command waits 5 seconds between uses.
  Paper-side and unverified until a playtest (T33).
- R5.1: Sustained danger now turns a resident into a guard. When a village's danger stays high (50 or more,
  "frightened" or worse) for three days after it first was, which a raid the village wins does not do but a
  lost raid, or attacks and deaths that keep coming, do, its bravest suitable adult takes up arms, one a day,
  up to one guard per ten residents. Nobody is called up in a famine or while food is short. Jobless adults
  are called up first, then idlers, then anyone who is not feeding the village (farmers, fishers and butchers
  are never taken); children, elders and the timid (bravery under 40) never are. A guard eats an extra ration
  a day and wears out tools as gatherers do; with no food they cannot stand watch, and with no tools they
  stand it unarmed. A guard with food and tools makes danger fall about 3% a day faster each, for up to three,
  so the cost buys something. Once danger is below 10 ("peaceful") guards stand down, one a day, and the
  history records both. Guards need no building yet (a `[Guard Post]` sign still does nothing). Save format
  is now 15 (a new occupation; the count of high days is a settlement condition). The rule is in core and
  tested; raising danger in game (raids, deaths) is checked by T44.
- R2.7: Once a village has a treasury building, donating and the payout for a filled request happen at its
  counter. `/settlement donate` with an amount now only works within 12 blocks (and 8 up or down) of a
  `[Treasury]` sign; anywhere else it says how far the nearest one is and its coordinates, and takes
  nothing. Telling you what the stack in your hand is worth (`/settlement donate` with no amount) still
  works anywhere. A village with no treasury building keeps the old rule, anywhere inside it, so existing
  villages still work, and breaking the last treasury sign goes back to it. The rule is in core and
  tested; the command check is Paper-side and unverified until a playtest (T43).
- R2.6: The treasury now has a limit. Without a treasury building a village can bank only a base amount
  (`economy.treasury-base`, 200 emeralds by default), and each sign reading `[Treasury]` inside the village
  adds 500. Rewards set aside for open requests count against it (requests are paid from the treasury, so a
  small limit caps how much a village can ask for). Income beyond the limit (merchant sales, donations) is
  wasted, as goods are over a storage limit: a merchant simply stops selling while the treasury is full, an emerald
  donation only takes what fits (the rest stays in your hand, and the message says so), and anything over
  is trimmed each day, so breaking a sign lowers the limit again. `/settlement` shows "Treasury: 12 of 200
  emeralds". So that no existing village loses emeralds, a village that already holds more than the base
  keeps room for what it holds, including rewards set aside for open requests (saved as a condition); new growth beyond it needs a treasury building.
  Save format is now 14. The rules are in core and tested; the sign and the donation message are checked
  in game by T42.
- R2.5: Merchants now work from a storefront. A sign reading `[Shop]` inside a village registers one (and
  breaking the sign removes it); `/settlement buildings` lists it. A merchant needs a free storefront, one
  merchant per storefront, so a village with no shop has no merchant selling: nobody new is made a
  merchant, and an existing merchant (or more merchants than shops) goes back to being unemployed, one a
  day. Building a second shop lets a second merchant work, up to the one per fifteen residents the village
  could use. Existing villages with merchants lose them until someone places a `[Shop]` sign. The rule is
  in core and tested; the sign is checked in game by T41.
- R1.8: A villager that stays in another village's area for three days now becomes a resident of that
  village. Every few seconds the plugin notes where each loaded villager is; one that is outside its own
  village's radius and inside another's (the nearest, if several) starts a count, and after three days
  there it is moved: both histories say so ("X settled in Y and is no longer counted in Z" and "X came to
  live in Y from Z"), and the resident loses their old job, as with migration. Going back home, or to a
  different village, starts the count again, being anywhere in your own village's radius never counts, and
  a village that has been given up can be resettled this way. Someone just brought over by migration
  (R4.2) is left alone for a week, and forgets their old bed and workplace, so they do not drift straight
  back. This is how a villager you carry to a new village becomes part of it, which also means carrying
  every villager away can leave a village empty and, after ten days, abandoned. `membership.follows-residents: false` turns it off. A resident whose
  migration (R4.2) is still being carried out is left alone. No save format change (the count is kept as
  a settlement condition). The rule is in core and tested; reading villager positions is Paper-side and
  unverified until a playtest (T40).
- R4.2: Unemployed or unhappy residents now leave for a better-off settlement nearby. Each day a village
  is checked once: any adult who is not an elder and either has no job or is in low spirits has a one in
  five chance of setting out, and at most one resident leaves a village a day, and never one that would
  leave it with fewer than two people. They go to the nearest settlement within 600 blocks, in the same
  world, that is clearly better off (more food in store per head, less threat), has a free bed and is not
  in famine. Both histories record it ("left X for Y" and "came to Y from X"), the resident keeps who
  they are but loses their job there, and the villager is teleported to the new village the next time it
  is loaded (a vanilla profession can give them work again, as for any unemployed resident). Residents
  with no better village in reach simply stay. `economy.migration: false` turns it off. No save format
  change except a new history kind, so the format is now 13 (the pending move is kept as a settlement
  condition). A villager that is trading, leashed or riding is left alone and tried again later, as is a
  teleport that fails. The rule is in core and tested; the teleport is Paper-side and unverified until a
  playtest (T39).
- R3.5: Residents now have wealth. A working adult earns the worth of what they produce each day (at the
  merchant rates: ten food, six wood, five stone or two metal make an emerald), a merchant keeps a fifth of
  each emerald a sale brings the treasury, and every adult pays for their own meals out of what they have.
  Wealth is a personal figure only: it does not touch the treasury or the stores, so it can neither
  mint nor drain what the village holds. It shows in dialogue (broke, modest, comfortable, wealthy: a
  jobless resident with no coin says so) and as a word after each adult in `/settlement residents`.
  Children neither earn nor pay. Save format is now 12; an older save's residents start with nothing put
  by. The rules are in core and tested; the dialogue and listing are checked in game by T38.
- R3.14: Villagers now sell food only while the village can spare it. A sale the stores cannot cover
  (stock above what the village wants to hold) is refused with a message, and nothing is taken from the
  player; this is checked as the trade happens, so several food offers cannot all draw on the same stock.
  The offers are not greyed out, because changing a villager's trade limits risks leaving them wrong or
  raising its prices for good. In vanilla 26.2 no villager sells wood, stone or metal for emeralds, so
  food is what this governs in practice; tools, goods such as wool and glass, and other trades (enchanted
  books, maps, armour) are left as in vanilla. `economy.trades-need-stock: false` turns the rule and the
  seeding off. A newly founded village now starts with 20 food for each founding resident, so its first
  traders have stock (a village with farmers will therefore take in its first newcomer a little sooner).
  The rules are in core and tested; the refusal and the seeding are Paper-side and unverified until a
  playtest (T37).
- R3.2: Trades with villagers now move goods in the settlement's stores. What you sell a villager for
  emeralds (wheat to a farmer, stone to a mason) is added to the stores, as far as there is room, and
  what you buy with emeralds (bread, glass) is taken out of them, but only down to the level the village
  wants to hold, so buying cannot starve a village or manufacture the shortage that makes it post a
  request. Emeralds still come from the game, not the treasury, and tools and trades that are not goods
  for emeralds are left alone. Selling to a short village makes the next sale pay better (R3.1); a
  1-emerald purchase never changes price, which is why buying is held back by the floor. The rules are
  in core and tested; the trade event hook is Paper-side and unverified until a playtest (T36).
- R3.4: Each settlement now keeps a reputation, -100 to 100, for every player. Gifts raise it (an
  emerald's worth of goods is a point, so a handful of sticks earns nothing), filling a request raises it
  a little more (up to 10 per delivery), and killing a resident lowers it by 25. Residents greet you
  accordingly (wary, hostile, friendly, honoured), `/settlement` shows your "Regard", and resource-trade prices
  move up to a tenth in your favour at +100 and against you at -100 (a cheap trade may round to no change; this rides on `prices.follow-supply`, so it is off when that is).
  Save format is now 11; an older save starts everyone as a stranger. The rules are in core and tested;
  the kill, donation, greeting and trade hooks are Paper-side and unverified until a playtest (T35).
- R2.3: Registered buildings now give residents somewhere to work. A registered `[Mine]` lets an
  unemployed resident become a **miner** (new occupation, four places per mine) who makes stone and
  metal, so smiths can finally work without donations; a registered `[Farm]` likewise gives four
  farmer places. Lumberjacks and merchants need no building. Job assignment is now: food first
  (while food is short and a food job has a free place, that job is taken),
  otherwise the shortest need that has somewhere to work, so a missing mine never blocks the
  lumberjack and a village without one is not forever "short of metal". Only a famine, or stores that cannot cover
  today's meals, freeze the other jobs, so a village that merely holds little food still gets lumberjacks and miners, and a
  registered `[Smithy]` gives smith places when tools are short. A `[Farm]` or `[Mine]` place is shared with
  villagers who already hold that job from their vanilla profession. Miners wear tools out like
  other gatherers. A villager's own vanilla profession still works as before. The tool penalty
  (R3.6) is on by default in the Paper layer (`economy.tool-penalty`, set it false to turn off) but
  only bites in a village that has a registered mine: with no tools it works at three quarters speed and
  the history records the shortage, until someone mines the metal for a smith or donates tools. The rules are in core and tested; the sign
  to job link is Paper-side and unverified until a playtest (T34).
- R3.6: Tool wear is now finished: tools wear out, gatherers (now including miners) slow down while
  the village has none, and the shortage is recorded and mentioned in dialogue.
- R2.2: Beds within a settlement now set its housing capacity, shown in `/settlement` ("Housing: 5 beds
  (2 free)") and in the admin inspect. Beds are counted per chunk within the settlement radius, only in
  loaded chunks and at most once a minute, and a chunk that is not loaded keeps its last known count,
  so housing does not vanish when nobody is nearby. Newcomers (R4.1) now use this saved capacity
  instead of a scan at the moment of arrival, so they arrive when there is a free bed whether or not
  the beds are in loaded chunks. Save format is now 10 (the bed counts); an older save has none
  until its chunks are next loaded. Where two settlements' areas overlap a bed is counted for both (as before). Lowering `settlement-radius`
  drops counts outside the new range. The model is in core (`Housing`, tested); the bed scan is
  unverified until a playtest (T33).
- R2.1: Players can now register buildings with signs. A sign reading `[Farm]`, `[Smithy]`, `[Mine]`,
  `[House]` or `[Guard Post]` (any capitalisation, brackets required) inside a settlement registers that
  building at the sign, the sign is tidied to the standard text, and the history records it (one
  player's building changes in a week are merged into a single line, so signs cannot flood the
  history). A sign has two sides and the front wins if both name a building. Breaking the sign
  removes the building, and so does editing it so that neither side names one. A sign destroyed any
  other way (physics, an explosion) is dropped within a few seconds, in loaded chunks. Registering the
  same sign twice does nothing, changing its kind replaces the building, and a settlement holds at most
  200. `/settlement buildings` lists them and `/settlement admin inspect` counts them. Nothing yet checks
  that a registered "farm" is really one (R2.4), and nothing yet uses them (R2.2, R2.3). Save format is
  now 9 (the buildings); an older save has none. The rules are in core (`BuildingType`, `Building`,
  `Settlement.registerBuilding`, tested); the sign listener and command are unverified until a
  playtest (T32).
- R3.11: `/settlement donate` now respects the storage limits (R3.10). On its own it says how much
  room the stores have for what you are holding. When you donate, it only takes the items that fit
  and are worth something; the rest stay with you, and it says so. A donation to a full store is
  refused ("Nothing taken") instead of being accepted and wasted the next day. The rule is in core
  (`Donation.plan`, `SettlementSimulator.room`, tested); the command is Paper-only and unverified
  until a playtest (T31). Emerald donations are unchanged: the treasury has no limit until R2.6. (A limit rises and falls with the
  population, so a store filled to the brim can still lose a few units when a resident dies.)
- R1.15: Confirmed that the admin commands (R1.4) and abandoned settlements (R1.5) were merged before
  any M2 item began (no M2 item has started). Bookkeeping only.
- R1.24: Unemployed foraging is now documented as intended. An unemployed adult gathers about 1 food
  a day, the floor under a small village with no farmer; it is described in the README and
  `docs/DESIGN.md`, unemployed residents mention it in conversation, and tests pin the behaviour
  (ten foragers make about 70 food a week, and a forager village stays fed longer than an idle one).
  No behaviour changed.
- R3.1: Villager trade prices follow the settlement's stores. A villager selling food, wood, stone,
  metal or tools charges up to half as much again when the village is short of it (at an empty
  store) and up to a quarter less when it has four times what it wants; one buying it asks for
  fewer or more of the item the other way. Prices are untouched between what the village wants and
  twice that, and goods (wool, paper, glass) are left alone. A price moves by whole emeralds towards
  the base, so most real trades move by at most 1 or 2 and a 1-emerald trade (bread, apples) never
  changes; the trades that move most are the ones that ask for many items, like a farmer's 20 wheat
  for an emerald. Only emerald-for-resource trades in a settlement are touched, and the game's own
  reputation and Hero of the Village discounts still apply. It is set when a trade window opens, so
  it never sticks. The model is in core (`PriceModel`, unit tested); the Paper layer is unverified
  until a playtest (T30). Turn it off with `prices.follow-supply: false`.
- R3.13: A request never pays more for a crafted tool than its raw materials would on requests
  of their own, at the same rates. A tool is valued from its recipe (a pickaxe or axe is 3 head
  material and 2 sticks, a hoe or sword 2 head, a shovel 1 head; the head is planks, cobblestone
  or ingots by tier), worn tools in proportion to their wear, and diamond or netherite heads count
  as nothing because villages don't accept diamonds raw. When that cap is below what the tier
  alone would pay, the delivery only counts for as many tool units as it can pay for, so the
  rest stays on the request and no emeralds are lost or carried over; the tool still goes into
  the stores. A tool pays at most its materials' value in whole tool units: a wooden or stone tool
  pays nothing, and an iron pickaxe 2 emeralds against 6 before. Reading the tool's wear is Paper-only and unverified until a playtest (T29).
- R3.12: Donations are valued by what the item is made of. Wood counts in planks: a log, wood
  block or stem is 4, a plank 1, a stick half and a bamboo stalk a quarter, so the same timber is
  worth the same in any form and a request (R3.3) pays the same for a log, four planks or eight
  sticks. A stack is valued as a whole and rounded down, so splitting a donation gains nothing.
  A tool is worth its tier in proportion to its remaining durability, down to nothing when worn
  out. `/settlement donate` no longer takes a donation worth less than one unit (a lone stick, a
  worn-out tool): it says so and keeps the items. Logs are now worth four times what they were,
  so wood donations count for more than before, and a big stack can overflow a small
  village's storage limit and be wasted (R3.11). A mushroom stem is no longer counted as timber,
  and `/settlement donate` only takes the items its credit pays for, so an odd leftover stick
  stays with the player. Reading a tool's wear is Paper-only and unverified until a playtest (T28).
- R3.3: Village requests. When a resource (food, wood, stone, metal or tools) runs short and the
  treasury can pay for it, the village posts a request for it (at least 8 units, up to double what
  it wants), shown in `/settlement` and mentioned by residents, and the history records it. The
  reward is taken out of the treasury and held when the request is posted, at twice the
  merchant's rate, so it can always be paid. `/settlement donate` of the requested resource pays
  the donor in emeralds in proportion to what they hand over, to the last one, and anything beyond
  what is asked for is an ordinary donation. A request closes when the stores recover or after
  30 days and the unpaid reward goes back to the treasury; the same resource then waits 7 days
  before asking again. At most 3 are open. Save format is now 8 (open requests); an older save
  has none. Paying the donor is Paper-only and unverified until a playtest (T27).
- R3.9: New MERCHANT occupation. A merchant makes nothing but sells the village's surplus for
  emeralds into the treasury: up to 4 whole batches a day (fewer for an elder or an unhappy
  merchant), each earning one emerald, always starting with whichever resource has the most
  surplus worth. Rates are 10 food, 6 wood, 5 stone, 2 metal, 2 goods or 1 tool per emerald.
  Stock is only sold above what the village keeps: 6 a resident of most things, 20 of tools and
  metal (smiths and wear need them), and 24 of food, which leaves 20 a head after the day's meal
  and spoilage so merchants never stop newcomers (R4.1). That is well above what R4.3 counts as
  short. An unemployed resident becomes the merchant when something can be sold, food is
  comfortable and nothing else is short, one merchant per 15 residents; a merchant with nothing
  left to sell goes back to unemployed when something is short. A merchant's child follows the
  trade only when nothing is short. The treasury has no spending yet (R3.3, R4.8, R5.1).
- R4.16: Every tracked villager now carries `hamletfolk:gender`, `hamletfolk:occupation` and
  `hamletfolk:life_stage` in its persistent data. That data stays on the server (datapacks, command
  selectors and other plugins can use it); clients never see it. New setting `appearance.villager-type` (off by default) also sets the villager's vanilla type
  from gender and life stage (female plains/snow, male desert/taiga;
  children share the adult's, jungle, savanna and swamp are never used), so a plain resource pack that redraws those
  types can reskin villagers with no client mod; turning it off restores each villager's original type. The keys and mapping are in docs/APPEARANCE.md and
  the mapping is in core with a unit test. Unverified until a playtest (T26): how clients and
  Bedrock see it.
- R4.15: Residents have an age in days and a life stage (child, adult, elder at 60 days). Elders
  produce 60% of normal output. Each resident has their own maximum age (90 to 110 days) and, with
  `aging.old-age-deaths: true` (off by default), dies of old age past it, recorded in history; the
  Paper layer then removes their villager permanently, and if it was unloaded removes it when it
  loads, so it isn't enrolled again as a stranger. It is off by default because villages no player
  visits can empty out if nothing replaces the dead. A cured zombie keeps their lifespan and gets
  at least 20 days. Anyone already grown
  when first seen (founders, arrivals) is given an age of 12 to 51 days, so no one starts old.
  `/settlement residents` shows age, and elders may talk about their years. Save format is now 6:
  `departed` ids are saved, and residents in an older save whose recorded age is beyond
  that range are rebased (never made older), so no one in an old save dies the moment it loads.
  The ages are in-game days (about 20 real minutes each): lifespans are roughly a day and a half
  of real time at the base numbers. `aging.lifespan-scale` (default 20, about a month of real time
  for a life: elder at 1200 days, death at 1800 to 2200) multiplies every age together, including
  the starting ages of the first residents; it applies on restart and to existing residents.
- R4.14: Residents have a gender (female or male), drawn when they are enrolled and saved. Given
  names come from per-gender lists (the old list split in two, with more masculine names added),
  so name and gender agree. `/settlement residents` shows pronouns, and a resident may mention a
  parent as mother or father with matching pronouns. Save format is now 7: an old save's residents
  get the gender their name belongs to, a name on no list gets one derived from their id, and a
  resident an earlier build saved as nonbinary is given one the same way. No one is renamed.
  (An earlier version of this item had a third, nonbinary gender; it was dropped as not fitting the
  setting. Choosing not to marry is left to R6.1 households, as a choice any resident can make.)
- R4.1: A settlement with a food surplus (20 food per resident in store) and a free bed gets a
  newcomer (never on the settlement's founding day), at most one every 3 days; the arrival is recorded in history like any other settler.
  The rule is in core (`SettlementSimulator.newcomerDue`, unit tested). The Paper layer counts
  beds in loaded chunks (R2.2 will replace this) and spawns the villager, so it is unverified
  until a playtest (T22).
- R4.10: A grown child (one with a parent on record) who is still unemployed takes the occupation
  the village is shortest of, preferring a parent's trade when it is also short, and the history
  records the apprenticeship. Uses R4.3's job assignment, so until M2 buildings exist only
  occupations with no vanilla profession are open. A child who claims a vanilla workstation before
  the simulation sees them grown keeps that job and is not apprenticed. (Scenario T21.)
- R4.9: A resident's worst need (food, safety or purpose) now scales their output: at or above 50 it
  costs nothing, below that it falls linearly to half output at zero. A starving or terrified
  workforce produces noticeably less, but never nothing. Food need now holds steady while at
  least half the demand is met and recovers above that (it used to recover only when everyone was
  fully fed), so a village that can feed itself at its lowest output always climbs back.
- R3.10: Each resource has a storage limit (100 plus 10 per resident, 40 per resident for food) and
  anything over it is wasted at the end of the day. Food also spoils, 2% of the stock a day. A
  surplus can now end, so the "granaries full" milestone can clear and recur. Nothing is saved for
  this, so no format change; a stockpile above its limit (e.g. from an old save or a big donation)
  is trimmed the next day it is simulated.
- R4.3: The simulation now owns a resident's occupation. An unemployed adult takes the occupation
  the village is shortest of (at most one a day). Until buildings exist (M2) only occupations with
  no vanilla profession (lumberjack) count as having a free workstation. A villager's vanilla
  profession only fills in a missing occupation (`Resident.seedOccupation`), so newborns still pick
  up a vanilla job, but `SettlementService.track()` and the career-change handler can no longer
  overwrite an assigned one.
- R1.28: Added a read-only `reviewer` subagent (Opus) in `.claude/agents/` that checks diffs
  against this repo's known traps and labels findings verified or unverified. `CLAUDE.md` says
  when to use it.
- R3.8: Donations are credited by value: storage blocks count as their contents (iron, gold,
  copper and their raw blocks, hay, melon, dried kelp, emerald) and tools by material tier.
  `/settlement donate` now needs an amount or `all`; on its own it just says what you're holding
  is worth. Gold and copper blocks, melons and dried kelp blocks are newly accepted.
- R1.26: Playtest scenario T6 now notes that a starving village gives the famine line before the
  "no metal" one, needs a smithing table for the toolsmith, and gains T6b for the metal line once fed.
- R1.25: Shared `.claude/settings.json` allows Gradle, `git add`/`git commit` and `git push` to the working branch without
  prompting, so local Claude Code runs can go unattended. `.claude/settings.local.json` is
  git-ignored for personal overrides.
- R1.19: Periodic backups. Besides the startup backup, `settlements.json` is saved and copied
  into `backups/` every `backups.interval-hours` (default 24; 0 turns it off) while the server
  runs, with the same rotation; `backups.keep` (default 5) sets how many are kept.
- R1.10: `worlds.allow` and `worlds.deny` in config.yml (names or `*` patterns, case-insensitive)
  decide where settlements are tracked. An excluded world is ignored: villagers there are not
  enrolled, `/settlement` reports no settlement, and existing settlements in it stop simulating
  but are kept. Empty lists mean every world, as before.
- R1.4: `/settlement admin list|inspect|rename|save`, gated by the new `hamletfolk.admin`
  permission (ops by default) and usable from the server console, so state can be checked
  without a player in game. Settlements can be named by name or by id prefix.
- R3.6 (partial): Tool wear. Farmers, fishers, lumberjacks and masons wear out a tool on about
  15% of working days, which gives TOOLS a sink. The slowdown and shortage record while a
  village has no tools are built and tested but switched off until R2.3 gives METAL a source
  (smiths need metal to make tools, so every village would otherwise stay toolless for good).
- R3.7: Each settlement keeps a rolling 7-day total of what it produced and consumed per
  resource (work, inputs and eating), saved with the settlement (format 4) and shown in
  `/settlement` as e.g. "food +12/day made, -15/day eaten".
- R1.23: If the world clock goes backwards (e.g. `/time set`), new residents and history entries
  use the settlement's own day, so nothing is filed before days already simulated.
- R1.22: When catch-up skips days beyond `max-catch-up-days`, the settlement's history now
  records how many were skipped ("990 days passed that no one in Oakvale wrote down."), except
  in an abandoned settlement.
- R1.21: History is bounded. A player's repeated donations within a week merge into one line
  ("Skye made 14 donations this week"), and each settlement keeps at most 500 events, dropping
  minor ones (donations, shortages, births, arrivals) before major ones (founding, deaths, cures,
  raids, famines, abandonment). Save format is now 3 (optional `count`/`actor` on events).
- R1.17: Saves are numbered when snapshotted; a slow background autosave can no longer
  overwrite a newer save (e.g. the one made at shutdown).
- R1.18: A donation triggers a save within about 2 seconds instead of waiting for the next
  autosave.
- R1.20: When an input runs short, whoever went without most recently gets first claim the
  next day, so the shortfall rotates round-robin instead of always hitting the newest worker.
- R1.16: docs/DEPLOYING.md documents the pin-and-verify process; PRODUCTION.md tracks
  which commit is live versus last verified.
- R4.4/R4.5: Added a LUMBERJACK occupation as wood's real source, and FLETCHER now
  consumes wood to produce goods (arrows) instead of producing wood directly.
- R1.13: `settlements.json` now enforces its schema version on load: an older save
  migrates automatically (format 1, from before R1.2, gets its missing `turned` field), and
  a save from a newer plugin build is refused rather than silently misread. README now
  recommends off-machine backup coverage for `plugins/Hamletfolk/`.
- R1.5: A settlement empty for 10 days straight is marked abandoned and stops being
  simulated (cheap to keep around indefinitely); its history is untouched, and it un-abandons
  the moment someone lives there again.
- R1.14: `plugin.yml` api-version corrected from `1.21` to `26.2`, matching the Minecraft
  version this build actually targets since R1.7 (confirmed against PaperMC's docs).
- R1.2: A villager infected by zombies and then cured comes back as the same person, with the
  same name, family, traits and memory of players. History records who cured them.
  `/settlement` shows how many residents are zombies who could still be cured.
- R1.3: On startup, the previous `settlements.json` is copied to `plugins/Hamletfolk/backups/`;
  the newest 5 copies are kept.
- R1.6: Performance test: 50 settlements of 50 residents simulate a day in a median 0.8 ms
  (budget: 5 ms).
- R1.7: Targets Minecraft 26.2 (the current stable Paper release) and Java 25.
- R1.9: Renamed to Hamletfolk. The plugin is now `Hamletfolk`, its data lives in
  `plugins/Hamletfolk/`, and the permission is `hamletfolk.use`.
- R0.7: `./gradlew runServer` starts a local Paper test server with the plugin installed.
- Build: Gradle 9.8.0; the Minecraft version is now set in one place (`minecraftVersion`).

## 0.1.0

- R0.1: Residents with persistent names, families, traits and needs; settlements with a shared ledger.
- R0.2: Daily production and consumption, famine, shortages, population milestones.
- R0.3: Settlement history book (`/settlement history`).
- R0.4: Residents talk about what's actually happening (sneak + right-click).
- R0.5: Paper plugin: villager tracking, deaths, births, raids, donations, `/settlement`.
- R0.6: CI builds the plugin on every push.
