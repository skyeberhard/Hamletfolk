# Smarter villagers (M9)

The simulation decides what a village needs; the world should show it. Today a villager cannot be given a
new behaviour through the public Paper API: it can be sent somewhere and its memories read and set, but
it cannot be taught to fight, to walk to its farm and work it, or to react to danger. M9 reaches into the
game's internal villager code to do that, under rules that keep the rest of the plugin safe.

## Target and constraint

- Built against **Paper 26.2 only** (the version this project and the owner's server run). The module is
  compiled against that exact server build; a game update means rebuilding and re-checking it, nothing more.
- Still server-side only, so Bedrock players on Geyser see the same behaviour.
- The simulation still owns every number. A behaviour makes a villager *act out* a decision the simulation
  has already made (a guard fights, a farmer works the field); it never changes the ledger by itself.

## Rules

1. **Everything that touches internals lives in one module** (`brain/`). `core` and the rest of `paper` never
   import an internal class. The plugin talks to the module through a small interface, so if the module is
   missing or refuses to load, the plugin runs exactly as it does today.
2. **A self-check at startup** (R9.1). The module lists every internal class, method and field it relies on
   and verifies each exists in the running server. If any is missing it logs which one, turns itself off and
   stays off. The check is a pure list, so it is testable without a server.
3. **A kill switch that works live** (R9.1). `/settlement brain on|off|status`, and `brain.enabled` in the
   config (default off until it has been played). Off removes every behaviour the module added and returns
   villagers to vanilla without a restart.
4. **A circuit breaker.** An exception in an added behaviour, or the added behaviours using more than a time
   budget per tick, turns the module off and logs it once with the villager involved. A fault costs the
   feature, never the server.
5. **Nothing is written into the world.** Behaviours are added when a villager loads and removed when it
   unloads or the module goes off, so removing the plugin leaves vanilla villagers and vanilla saves.
6. **Troubleshooting is part of the feature** (R9.2). Inspect a villager's activity, memories and added
   behaviours, switchable debug logging of each decision, the cost per tick, and a report written to a file.
7. **Every behaviour is optional by itself.** Each has its own config switch under `brain.behaviours`, so one
   misbehaving behaviour can be turned off without losing the others.

## What M9 grows into

The brain module is the foundation for more than guards. Each of these is its own behaviour family with its own
switch under `brain.behaviours`, on top of the master switch (R9.4):

| Family | Behaviour | Roadmap |
|---|---|---|
| guards | fight hostile monsters near the village while the stores hold tools (stand their ground instead of panicking, retreat when badly hurt); built | R9.3 |
| guards | patrol the village between fights, the night watch in turns; built | R9.6 |
| work | walk to the workplace in working hours and work there while a player is near; built (not yet seen) | R4.17 |
| builders | walk to a project site and build it over time | R4.7, R4.8 |
| stewards | elders and stewards meet people, stand at the counter, walk the village | R6.4, R6.6 |
| pathing | one shared service all of them use, with fallbacks (path, short hops, teleport or give up) and road waypoints; built | R9.5, R8.3 |

Two rules apply to all of them: they only act while a player is near the villager (far villagers are
simulation only, which also keeps the cost down), and they show what the simulation decided, never change it.

## Order of work

1. **R5.5**, guards respond to a pattern of attacks. Needs no internals, and R9.3 depends on guards existing
   sooner.
2. **R9.1**, starting with a spike: does the internals build set-up resolve on this Gradle and Java, can a
   test behaviour be added to a villager's brain and removed again, and what does it cost per tick.
3. **R9.2** and **R9.4**, the tools and the behaviour framework, before any real behaviour, so the first behaviour can be
   debugged and switched off by itself. **R9.5**, the pathing service, before the behaviours that walk.
4. **R9.3**, guards fight: attack hostile monsters near the village while the stores hold tools, retreat when
   badly hurt.
5. Then R4.17 (visible work) can use the module instead of the public-API route, with the same switch.

## The R9.1 spike: what we found (2026-10-09, Paper 26.2 build 130)

1. **The build.** No extra Gradle plugin is needed. Since 26.1 the game ships without obfuscation and Paper runs on Mojang's
   names, so `brain/` compiles directly against the patched server jar `paper/run/versions/26.2/paper-26.2.jar` and the
   libraries Paper downloaded next to it (`paper/run/libraries`), on Gradle 9.8 and Java 25. The catch: `./gradlew runServer`
   must have been run once to put them there; without them the build leaves the module out (and the plugin reports it missing
   and stays off). The classes are packed into the one plugin jar; the plugin loads them by name only after the self-check.
2. **Adding and taking off a behaviour works, live.** A villager's `Brain` keeps its behaviours in a private table
   (`availableBehaviorsByPriority`: priority, then activity, then a set). The module puts its behaviour into that table under
   the CORE activity and takes it out again; `Brain.addActivity` is not used, because it also replaces the activity's
   requirements. Read from the jar, not from memory: the villager class is now `net.minecraft.world.entity.npc.villager.Villager`
   (it moved), the schedule is an `EnvironmentAttribute`, and reading a memory the brain has not registered throws
   (`getMemory`; setting or erasing one does nothing).
   On a test server with 8 villagers: on, the behaviour was called once per villager per tick (968 calls in 6 seconds); off,
   the count stopped dead and stayed there; on again, all 8 were back. The table may only be changed between ticks, never from
   inside a behaviour (the brain is iterating it), so a fault switches the behaviours off at once and the once-a-second timer
   takes them out. **An error that escapes a villager's tick makes Paper remove the villager** (`Level.guardEntityTick`
   discards it), so every behaviour catches every Throwable, and the self-check compares full signatures (parameter types,
   return type, constructors): a member whose signature changes in an update is caught at startup, not in a tick.
3. **Cost.** About 1 to 3 microseconds per call for the test behaviour (worst seen 0.2 ms, at startup): some 20 microseconds a
   tick for 8 villagers, against 50,000 in a tick. Real behaviours will cost more; R9.2 shows the figure, and R9.4 added the
   budget breaker of rule 4: each behaviour has a budget for all its calls in one tick, and one that goes over it in 10 of
   the last 100 ticks stands aside, is logged once and comes off every villager, while the others carry on (on the test
   server the first tick after switching on took 0.7 ms, warm-up, which is why a single slow tick does not count).
4. **Not yet seen in play:** a villager whose brain the game rebuilds (on a change of profession) getting the behaviours back
   (the module checks once a second and re-attaches; a `/data` profession change on the test server left all 8 being called,
   but whether that rebuilt the brain is not known), the fault path on a real error, and the test behaviour with a player near.

## Puppet NPCs instead of villagers (R9.8, a spike)

Most of the work-arounds in M9 fight vanilla: professions the game changes back, panic memories a guard must
erase, idle chat that resets a walk, a module pinned to one server build. The question R9.8 answers is whether a
resident could be played by an entity the plugin drives itself. The plugin is server-side only, so whatever it is
must still look like an entity the client already knows; only its behaviour would be ours. Four options were
weighed (2026-11-22):

| Option | Cost | Gain | Risk |
|---|---|---|---|
| 0. Keep villagers and the brain module | nothing new | raids, golems, beds, trading and world-generated villagers for free | every server update can break `brain/` |
| 1. A goal-based host mob through the public `MobGoals` API | low to medium | full control, no internals, survives updates | is there a host that looks right? |
| 2. Player-shaped NPCs (skins) | high: movement and pathing from scratch | the best look | Bedrock display, much new code |
| 3. Our own entity class in internals | highest | the most freedom | all of `brain/`'s pinning and messy saves |

The recommendation was to stay on villagers until the playtest (R1.1) shows they are the bottleneck, and to keep
option 1 ready with this spike. A hybrid of 0 and 1 is also tested: a villager with its own AI switched off and
moved through the public `Pathfinder` API keeps the villager look and drops the brain work-arounds, if navigation
still works without the AI (not known).

Whatever is chosen, the entities are not saved: the simulation already holds every resident, so a puppet is spawned
when its chunk loads and removed when it unloads, and removing the plugin leaves a clean world (rule 5).

What the spike must find, on a local server (`./gradlew runServer`):

1. Which humanoid mobs in Paper 26.2 use goal-based AI (read from the server jar with javap, never from memory).
2. Whether a villager with `setAware(false)` can still be walked with `Mob.getPathfinder()`, and whether it then
   stops panicking, chatting, claiming beds and changing profession.
3. For the best candidate: one test NPC for a resident walks a `Journey` route, opens a trade screen
   (`Bukkit.createMerchant`) and is gone after its chunk unloads, with nothing left in the save; seen in Java and
   on Bedrock through Geyser.
4. What it loses against a villager, and a recommendation with a rough size for the switch.

Findings go below this line when the spike is run.

## Risks we accept, and how they are contained

- *Game updates break the module.* Pinned to 26.2; the self-check refuses to run on a mismatch.
- *A behaviour breaks vanilla villager life* (trading, sleeping, breeding, raids). Each behaviour is scoped to
  a few tracked residents, can be switched off alone, and is covered by the kill switch.
- *Performance.* Counters and the time budget (rule 4) exist from the first behaviour.
- *Unknowns.* The spike in R9.1 answers the build and API questions before anything else is written.
