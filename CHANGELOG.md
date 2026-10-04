# Changelog

Each entry lists the roadmap items it delivers. See [ROADMAP.md](ROADMAP.md).

## Unreleased

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
