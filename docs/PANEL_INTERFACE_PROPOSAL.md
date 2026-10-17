# Proposal: a panel interface between GoodeCraft and the Hamletfolk dashboard

From Skylar (Hamletfolk), for Owen and the assistant that works on GoodeCraft. This is a draft to review and change, not a request to merge anything. Nothing here
is built. Nothing changes in GoodeCraft until you decide it should. It follows [GOODECRAFT_PROPOSAL.md](GOODECRAFT_PROPOSAL.md) (the shared admin dashboard idea) and
[GOODECRAFT_INTEGRATION.md](GOODECRAFT_INTEGRATION.md) (what I found reading GoodeCraft's code). The roadmap item is R1.38.

## What it is for

The Hamletfolk dashboard is a web page served from inside the plugin (read-only unless `dashboard.allow-actions` is on, bound to `127.0.0.1`, behind login once R1.37 is done). It
is growing a three-level layout: a tray of worlds, a page per world, a page per village ([WORLD_DASHBOARD.md](WORLD_DASHBOARD.md)). GoodeCraft has things an admin would
want on those pages and elsewhere (portal routes, rifts, the dragon event, world clocks), and no web surface of its own. This proposal lets GoodeCraft put its own panels on the
dashboard **without either plugin depending on the other's classes**, and without GoodeCraft having to write a web page, a login, or an audit log.

Nothing here lets the dashboard reach into GoodeCraft. GoodeCraft decides what to show and what actions exist. Hamletfolk draws and gates them.

## The shape

Two small, separate interfaces, found through Bukkit's `ServicesManager`. Both sides compile against a tiny package of interfaces and records (below), which can be copied
into each project or published as a one-file jar: it contains no logic and no Bukkit types.

```
GoodeCraft (provider) ---- registers ----> DashboardPanelProvider        (Hamletfolk looks it up and draws it)
Hamletfolk  (host)    ---- registers ----> DashboardHost                 (GoodeCraft may ask for a refresh; optional)
GoodeCraft (provider) ---- registers ----> WorldLinks, WorldClocks       (read-only data Hamletfolk shows on its own pages)
```

If the other plugin is absent, nothing happens: the provider is not found and the panel is not drawn, or GoodeCraft finds no host and does not register. Neither plugin
declares a hard dependency (`softdepend` is enough, for load order).

## 1. Panels

### The interface (provider side: GoodeCraft implements)

```java
package com.hamletfolk.api;                    // the shared package name is open: see "Questions"

public interface DashboardPanelProvider {
    int API_VERSION = 1;                       // bumped only for a breaking change

    /** Called on the server's main thread, now and then. The panels this plugin offers; cheap to build. */
    List<Panel> panels();

    /** Called on the main thread, after the host has checked and confirmed it. Never called with something the panel did not declare. */
    ActionResult perform(String panelId, String actionId, Map<String, Object> params, Actor actor);
}

public record Panel(
        String id,                              // stable, e.g. "goodecraft.rifts"
        String title,                           // the tab or tray entry
        Place place,                            // where it appears
        String icon,                            // optional, a short name from a fixed set
        List<Section> sections,                 // what it shows
        List<ActionSpec> actions) {}            // what an admin can do from it

public enum Place { TRAY, WORLD_TAB, VILLAGE_TAB }   // a world tab is given the world's name; a village tab the village's id
```

A panel is **data, not code**: titles and a list of sections. The host draws them with its own styles (so they match, work on a phone, and cannot run script).

```java
public sealed interface Section {
    record Text(String heading, String body) implements Section {}                       // plain text, no markup
    record Facts(String heading, List<Fact> rows) implements Section {}                 // label/value pairs
    record Cards(List<Card> cards) implements Section {}                                // numbers with a label and an optional note
    record Table(String heading, List<String> columns, List<List<Cell>> rows) implements Section {}
    record Lines(String heading, List<Line> lines) implements Section {}                // a feed: text, an optional kind, an optional time
}
public record Fact(String label, String value) {}
public record Card(String label, String value, String note, Light light) {}
public record Cell(String text, Light light, String actionId, Map<String, Object> actionParams) {}   // a cell may be a button
public enum Light { NONE, GREEN, AMBER, RED, GREY }
```

Plain strings only. The host escapes everything. There is no HTML, no script, no links out, no images: that keeps the page's content security policy as strict as it is now (one
nonce'd script, nothing from outside).

For a `WORLD_TAB` or `VILLAGE_TAB` panel the host asks `panels()` and filters by what it is drawing; the provider can return different sections per place by reading
`PanelContext` (below).

```java
public record PanelContext(Place place, String world, String villageId) {}      // what is being drawn
List<Panel> panels(PanelContext context);                                        // (replaces panels() above)
```

### Actions

```java
public record ActionSpec(
        String id,                              // e.g. "close-rift"
        String label,                           // the button
        Confirm confirm,                        // NONE, or ASK (the host asks first and the audit log records it)
        List<Param> params) {}

public record Param(String name, Type type, boolean required, Object min, Object max, List<String> choices, int maxLength) {}
public enum Type { INTEGER, DECIMAL, TEXT, CHOICE, BOOLEAN, WORLD, PLAYER }       // WORLD: one of the server's worlds; PLAYER: an online player

public record Actor(String name, String how) {}                                     // "admin@example (dashboard)"; never a credential
public record ActionResult(boolean ok, String message) {}
```

How an action runs, always in this order, all in Hamletfolk:

1. The request names a panel, an action and parameters. The host looks the panel and action up **in what the provider last offered**. Anything not declared is refused.
2. Every parameter is checked against its `Param` (type, range, length, choice, and for `WORLD` and `PLAYER`, that it exists). Unknown parameters are refused. No free text reaches
   the provider except `TEXT` limited to `maxLength` and a plain character set.
3. The login and role checks from R1.37 apply (a viewer cannot act). Actions are off unless `dashboard.allow-actions` is on.
4. If `confirm` is `ASK`, the page asks and the request must say it was asked.
5. The host calls `perform` **on the main thread** and writes one line to the audit log: who, which panel, which action, the parameters, the result. The provider
   does not have to write its own.

The provider cannot be handed anything it did not declare, cannot be run off the main thread, and is never given a string to run as a command.

### Performance and safety rules

- `panels()` must not do slow work or load chunks; the host calls it at most every few seconds and only while someone has the page open, and times it. A provider that
  is slow or throws is skipped and logged, and its panels are greyed ("GoodeCraft did not answer").
- Panels are rebuilt, not subscribed to: no callbacks into the host.
- Sizes are capped by the host (sections, rows, text length), so one panel cannot bloat the page.
- The host never calls a provider after it is unregistered (the plugin disabled).

## 2. World data Hamletfolk shows on its own pages

These are plain read-only services, separate from panels, so Hamletfolk can show GoodeCraft's data on pages it draws (the world's Portals and Time tabs).

```java
public interface WorldLinks {                    // GoodeCraft implements
    List<Link> links();                          // every route between worlds that GoodeCraft knows
}
public record Link(String from, String to, Kind kind, String label, boolean open, Location at) {}   // at is null for a route with no place
public enum Kind { NETHER_ROUTE, END_ROUTE, PORTAL_STONE, RIFT, LINKING_BOOK }
public record Location(String world, double x, double y, double z) {}

public interface WorldClocks {                   // GoodeCraft implements
    Clock clock(String world);
}
public record Clock(boolean locked, Integer lockedAt, double speed) {}     // what GoodeCraft has set: a locked tick, and the day-cycle speed
```

What the dashboard does with them:

- **Portals tab** of a world: its routes out and in, the Portal Stones in it, open rifts, and links that look broken (a target world that is not loaded; a Portal Stone whose partner is gone).
- **Time tab**: why a world's villages are slow or still: "locked at tick 18000 by GoodeCraft", "day cycle x2". Today Hamletfolk can only measure that a clock is stopped or fast; with `WorldClocks` it can say why.
- Hamletfolk does **not** need GoodeCraft to change anything about its clocks. If you would like the dashboard to be able to set a world's day-cycle speed or lock, that is an
  action in a panel (section 1), and is yours to offer or not.

Portal Stones' own data (118 portals in a binary file) stays Portal Stones'. I would not read that file from Hamletfolk. If GoodeCraft would rather publish those links, that is the
`PORTAL_STONE` kind above; if not, it is simply absent.

## 3. Examples of what GoodeCraft might offer (your call, all optional)

| Panel | Place | Shows | Actions |
|---|---|---|---|
| Rifts | `TRAY` or `WORLD_TAB` | Open rifts, where, time left, destination | Close a rift |
| Dragon | `TRAY` | Countdown or state, day, who is on the board | none, or "postpone" if you ever want it |
| World physics | `WORLD_TAB` | The world's gravity, hunger rate, mobs, instability score | none |
| Worlds | `TRAY` | The 26 legacy and generated worlds with their Atlas level | Delete a generated world (confirm) |

The dashboard has no opinion about any of these. A panel with no actions is simply a read-only page.

## 4. What stays in each plugin

| | GoodeCraft | Hamletfolk |
|---|---|---|
| Panel content, what an action does, who may perform it in game | yes | no |
| Drawing, login, roles, confirmation, audit, main-thread running, parameter checks | no | yes |
| A world's physics, clock, portals, rifts, the dragon | yes | reads only, through the services above |
| Villages, their data and actions | no | yes |

## 5. Versioning and packaging

- `API_VERSION` is an integer on each interface. Hamletfolk accepts a provider whose version it knows, and shows "GoodeCraft panels are a newer version than this dashboard understands"
  otherwise. Additions (a new section kind, a new `Type`) do not bump it if an old host can ignore them. A host ignores a section kind it does not know.
- Package: one small source file set (interfaces and records, no dependencies, Java 25). Easiest for you: copy it into GoodeCraft under your own package and implement it; Hamletfolk
  does the same under `io.github.skyeberhard.hamletfolk.api`. For two separately compiled copies to talk through the `ServicesManager`, the classes must be **the same class
  loaded once**, so the real choice is below.

### The one technical decision

`ServicesManager` hands over an object of a class that both plugins must see as the same type. Three ways:

1. **Hamletfolk exports the interfaces; GoodeCraft compiles against them (`compileOnly`) and softdepends on Hamletfolk.** GoodeCraft's classes then load Hamletfolk's interface
   classes through Hamletfolk's loader. Simple, works with Paper's plugin class loaders when `softdepend: [Hamletfolk]` is set. Cost: GoodeCraft needs the interface jar to compile.
2. **A tiny third plugin or library jar both use.** Cleanest, one more file on the server.
3. **No shared types: a plain map protocol.** The provider returns `Map<String, Object>` trees (what the dashboard would serve as JSON). No shared classes at all, nothing to compile
   against; the cost is losing the compile-time checking. This is the least coupled and the easiest to keep working across upgrades and pins.

My recommendation is (3) for panels, because GoodeCraft is pinned to a Hamletfolk revision and upgrades on your schedule, and a map protocol survives two different versions meeting: a
provider returns `Map` data, the host validates it against the schema above and ignores what it does not know. The records in this document are then the **documented shape of the data**, with a small
validator in Hamletfolk. `WorldLinks` and `WorldClocks` would work the same way (`List<Map<String, Object>>`). I would like your view before writing any code.

## 6. What I would build in Hamletfolk, in order

1. The host side: look up providers, call `panels(context)` on a timer while the page is open, validate and cap the result, draw it (`R1.38`), with a fake provider in a unit test.
2. The action path: parameter checks, role and confirmation, audit, main-thread run, refusal of anything undeclared.
3. `WorldLinks` and `WorldClocks` on the world page.
4. Login (R1.37) before any of this leaves the machine: panels with actions are powerful.

All of it is off unless `dashboard.enabled` is on, and actions stay off unless `dashboard.allow-actions` is on. Nothing is deployed to production without your pin, build and NAS backup.

## Questions for Owen

1. Do you want GoodeCraft panels on the dashboard at all? If yes, which first (rifts? dragon? world physics?).
2. Of the three packaging options in section 5, which do you prefer? I recommend the plain map protocol.
3. Who may use the dashboard on the youth server (R1.37: which admins, behind Cloudflare Access)? Should a panel's actions need a second admin role, or a particular confirmation?
4. Will GoodeCraft implement `WorldClocks` and `WorldLinks`? Is there a way to get Portal Stones' links at runtime other than reading its binary file?
5. Should the dashboard be able to change a world's clock (a day-cycle speed or time lock) through a GoodeCraft action, or only show it?
6. Any constraint I have missed: ports, memory, NGINX, load order with `loadbefore: [PortalStones]`?

Nothing proceeds until you have answered 1 and 2. I would send you the interface for review again before building anything against it.
