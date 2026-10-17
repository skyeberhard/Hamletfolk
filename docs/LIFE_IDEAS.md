# What would give the villages life

A brainstorm, not a plan. What the simulation has now: residents with traits, families, wealth, needs, voices and a written history; a
ledger, trades and a plan; guards, threat and reputation. What it lacks is mostly *things you can see happen* and *things that happen
to people*. Ranked by what it would take, using what exists.

## 1. Seeing people live (cheapest feel for the money)

- **Visible work and routines (R4.17, builds on the pathing service R9.5).** Farmers walk to their fields, miners to the quarry, builders to the
  site and "work" there (arm swing, particles) while the blocks appear. Dawn: out of bed. Noon: work. Dusk: the square, the bell. Night: home.
  A village nobody watches does nothing; a village you fly over should look busy.
- **The bell as a hub.** People gather at the square at dusk, trade gossip (the dialogue system already has voices and situations), children play there.
- **Market day.** Every few days merchants set out stalls (barrels and signs) on the square; prices are talked about.

## 2. Things that happen (the Witcher's contracts, Fable's quests)

- **A notice board at the square.** The requests the village already posts (R3.3), news from the history, bounties for monsters near the village,
  and small stories ("Kaia's boy has wandered off") that a player can take up. It is where a player starts to feel needed.
- **Events with consequences.** Harvest festivals when the granary is full (everyone at the square, fireworks, a mood lift), a plague that
  needs a cleric, a fire, a bad winter, bandits at the gates, a wandering bard or peddler who arrives, stays a night and leaves rumours.
- **Named threats and heroes.** The raid leader has a name; the guard who held the gate becomes a legend and is remembered (a statue, a song line,
  a street named for them). Master smiths make named tools that are passed on.

## 3. People who matter to each other (Dwarf Fortress)

- **Thoughts.** Each resident keeps a short log of what shaped their mood ("ate a good meal", "saw a friend die", "was thanked by a traveller"),
  readable with a command. Mood already changes output; the reasons would make it a story.
- **Friends, rivals, couples, households (R6.1).** Marriages, children who inherit a trade or a house, grief, apprentices taught by masters.
- **Reputation that means something.** A village that remembers you opens its doors, gives you its best prices, asks you to a feast, or holds a grudge.

## 4. Places with a character of their own

- **Culture per village.** A founding day kept as a festival, a banner, a motto, a way of speaking set by the land (the voices and leanings exist).
- **Graves and memorials.** The dead (the history already records each) have a place; ruins (R8.8) have a story when a village is lost.
- **Neighbours.** Villages hear about each other: rumours of a famine two valleys over, a caravan with a llama along the road (R6.5), a rivalry or a
  marriage across villages. The map starts to be a world, not ten islands.

## 5. Mystery

- Ruins with something in them, a witch's hut, a well that is said to be haunted, an old map in the library. Small, odd, and told by villagers
  rather than shown by markers.

## Where I would start

1. **Visible work and the dusk gathering** (R4.17 + the pathing service): the biggest change to how a village *looks* to someone passing over it.
2. **The notice board**: gives the player a reason to walk in, and uses requests and history that already exist.
3. **Thoughts for each resident**: almost no visual cost, makes every resident a person.
4. **Events and festivals**, then **caravans between villages**, once the first three are in.

## After the first playtest: why they do not yet feel lived in (2026-10-17)

The villages were there and growing, but empty of *evidence of people*. Each of these leaves a trace in the world that comes from data we
already hold, so none needs a new simulation:

- **Whose house is it.** Residents have family names (R6.1 will make households). A small sign or a name on an item frame by each door
  ("Mercer"), and a bed each resident visibly sleeps in (vanilla already claims one: a name tag on a sign at the bed's foot is enough).
- **Rooms with a purpose.** The house kit chosen by what the household does: a farmer's has a composter, a hoe in an item frame and a barrel of
  seed; a smith's an anvil and a grindstone; a librarian's shelves of books; a shepherd's a loom and wool. R4.18's template catalogue is the place.
- **The stores made visible.** The granary's barrels hold bread and wheat in proportion to the ledger's food, the smithy's chest holds tools in
  proportion to its tools, the storehouse planks and logs. Take from them and the ledger drops (and reputation with it): the village's accounts
  become something you can walk past and see. Must be built so a chest cannot be farmed for free items: a count that follows the ledger, never the other way.
- **Light and smoke.** Lanterns and campfires lit at dusk and out at dawn, a campfire under each chimney for smoke, doors that are closed at night.
- **Signs of daily life.** Washing (banners) on a line, flower pots, hay bales, a well with a bucket, a market stall on the square by day, a
  notice board (a lectern with a book of what the village is asking for: the request data exists).
- **People about.** The visible work of R4.17, then the dusk gathering at the bell and children playing there (R4.28 has children).
- **Memory in the landscape.** A graveyard that grows with the history's deaths, and a plaque with their names; a founder's stone on the square.
- **Sound.** Anvils from the smithy, the bell, a market murmur: only while a player is near, like every behaviour.

The cheapest first: lit lanterns at dusk, the notice board, and name signs on doors. The most striking: the stores made visible.
