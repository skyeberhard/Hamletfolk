# GoodeCraft integration: what its code does, and how the two plugins should meet

A review of the GoodeCraft repository (`goodecraft-plugin`, its docs and `docs/portal_data.json`, read on 17 October 2026) to work out how the portal
and world systems plug into the world dashboard ([WORLD_DASHBOARD.md](WORLD_DASHBOARD.md)). Nothing in GoodeCraft was changed. Where this says "it does",
I read the code; where it says "unclear", I did not find out.

## What GoodeCraft has that matters to us

### 1. Four ways to move between worlds, owned by three different things

| System | What it is | Who owns it | Where its data is |
|---|---|---|---|
| **Portal routing** | Vanilla nether and end portals re-routed so each of the 18 legacy worlds (and the generated ones) has its own "nether" and "end" destination. Nether portals scale coordinates between worlds; end portals cancel and teleport to the target's spawn | GoodeCraft (`LegacyWorldManager`, `portal-routing` in its `config.yml`, `PortalDestinationMap`) | Config file; `LegacyWorldManager` keeps a `Map<String, PortalRoute(netherTarget, endTarget)>` with a getter |
| **Portal Stones** | Player-built redstone portals, linked in game: 118 portals across 17 worlds, 57 bidirectional pairs | A third-party plugin (Portal Stones 2.2) | `plugins/PortalStones/data`, a Java-serialized binary file. `docs/portal_data.json` is an extract: each portal has an id, world, x/y/z, `toPortal`, `fromPortals` and its bounding box |
| **Linking and World Books** | Items that teleport a player to a bound place or a generated world | GoodeCraft (`items/`) | Item data on the book; no central list |
| **Rifts and exit portals** | Temporary amethyst portals to generated worlds; exit portals back out | GoodeCraft (`RiftManager`, `ExitPortalManager`) | In memory with the rift; the destination world persists |

So there is no single "portal graph". Routing is a table of 27 routes (world to world, two kinds), Portal Stones is a graph of 118 located portals, and the
rest are items and short-lived rifts.

### 2. Per-world time already exists, and it changes what a village day is

GoodeCraft gives each world a `WorldParams` (from `legacy-world-effects.json` for legacy worlds, or the generated world's saved parameters) with:

- `time_lock`: the clock is set to that tick and `ADVANCE_TIME` is switched off. The world's day stops.
- `day_cycle_speed`: a multiplier. `WorldEffectsListener` adds `(speed - 1)` ticks every server tick with `world.setFullTime`, carrying the fraction; a speed
  of 0 switches `ADVANCE_TIME` off.

Hamletfolk's village day is `world.getFullTime() / 24000` (`SettlementService.day`). So **this is already time dilation for villages**, by accident:

- A world with `day_cycle_speed: 2` makes its villages live twice as fast. Nothing in Hamletfolk knows.
- A world with a `time_lock` (Hades has `18000`) has a stopped day count, so its villages **make no daily progress at all**. `docs/hamletfolk.md` in GoodeCraft
  already says so ("worlds with a locked clock do not get daily economic progress").

This matters for the design. R1.47's world speed should not be a second, competing speed. The clock is GoodeCraft's (it owns the worlds and their physics); Hamletfolk should *read*
it, show it, and cope with it. The saved speed built in R1.46 is a different thing (run the villages faster than their clock), and it stacks on top.

### 3. There is no API

GoodeCraft registers no service and publishes no events. It exposes getters on its main class (`getGeneratedWorlds`, `getWorldParams`, `getLegacyWorldManager`,
`getRiftManager` and so on), which another plugin could call only by depending on its classes. It has no admin web surface; admin is `/gc` commands and RCON.

### 4. Other facts worth knowing

- Worlds are many and change at runtime (26 legacy, 4 generated, new ones from the Altar; `/gc delete` removes one). Villages can be left in a world that is no longer loaded.
- Paper 26.2 build 129 is production. Some dimensions have no world clock and reject `setTime` (GoodeCraft catches the exception). Reading the time is fine (the dashboard does it on all loaded worlds).
- The Atlas (per-player world exploration level) is kept in player data: it says which worlds a player has visited. That is a natural source of "who has been where" for later quests.
- Production still runs Hamletfolk `98d2f0b` (27 September). Everything since goes through Owen's pin, build and NAS backup (`docs/DEPLOYING.md`). The save format has moved from 8 to 30, with migrations, and the production file is empty.

## What this means for the world dashboard

1. **Time is the real integration.** The world page should show each world's clock state, which Hamletfolk can find out with no dependency: whether the clock advances
   (`ADVANCE_TIME`) and the measured ratio of world days to real time. A locked world is the loudest case: its villages are frozen, and today the dashboard gives no hint.
   This is cheap and valuable, and is added to R1.44 below.
2. **Links need a small shared interface, not a hard dependency either way.** The two plugins should agree on a few plain interfaces and records; each side registers or
   reads them through Bukkit's services manager; neither depends on the other's classes.
3. **Portal Stones should come through GoodeCraft.** Reading its binary file from Hamletfolk would be fragile and is Owen's data. GoodeCraft already loads the `portal_data` extract
   for its own use; it is the right place to publish the links as one list.

## Proposed shared interface

Small, read-mostly, in a tiny package both can copy or compile against (decided with Owen). Hamletfolk looks each service up with `ServicesManager.load`; if it is absent, the dashboard shows
nothing for that panel.

```java
interface WorldLinks {                       // GoodeCraft implements
    List<Link> links();                      // every route between worlds
    record Link(String from, String to, Kind kind, String label, boolean open, Optional<Location> at) {}
    enum Kind { NETHER_ROUTE, END_ROUTE, PORTAL_STONE, RIFT, LINKING_BOOK }
}

interface WorldClocks {                      // GoodeCraft implements
    Clock clock(String world);               // what GoodeCraft has set for the world
    record Clock(boolean locked, OptionalInt lockedAt, double speed) {}
}

interface DashboardPanels {                  // Hamletfolk implements (R1.38)
    void register(Plugin owner, Panel panel);   // a name, the data to show, actions with parameters
}
```

- `WorldLinks` feeds the world page's **Portals** tab: for a world, its routes in and out, the Portal Stones in it, the open rifts, the links that are broken
  (a target world that is not loaded, a Portal Stone whose partner is gone).
- `WorldClocks` lets the Time tab say why a world's villages are slow or still, and (if Owen adds a setter) lets an admin change a world's day-cycle speed or lock from one place.
- `DashboardPanels` is R1.38: GoodeCraft can add its own panels (dragon, rifts, altar) with the dashboard's login, confirmation and audit.

## Order of work

1. **Now, no dependency (Hamletfolk only):** R1.44 grows a clock line: whether each world's clock advances and its measured speed, with a warning on the light when a world with villages has a stopped clock.
   Also list worlds that hold villages but are not loaded.
2. R1.38: the panel interface in Hamletfolk, reviewed by Owen before anything is built against it.
3. With Owen: agree `WorldLinks` and `WorldClocks`; GoodeCraft implements them (a few dozen lines on top of what it already has).
4. The Portals tab, the clock source and, if wanted, the setter.
5. R1.47 (world speed) is then decided against what GoodeCraft already does: most likely Hamletfolk adds nothing and shows what GoodeCraft sets.

## Questions for Owen

- Is the `day_cycle_speed` / `time_lock` effect on villages what you want? A locked world has frozen villages; should that change, or just be shown?
- Will you add the two small services (`WorldLinks`, `WorldClocks`), and should Portal Stones' links be published through GoodeCraft?
- Is there a way, short of reading Portal Stones' binary file, to get its portals at runtime (its API, if it has one)?
- Should the dashboard be able to change a world's clock settings, or only show them?
