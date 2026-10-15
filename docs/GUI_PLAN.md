# GUI plan

An implementation outline for adding vanilla-client menus to Hamletfolk while keeping the current
commands useful. The goal is a small amount of UI infrastructure, reuse of the existing settlement
rules, and no required client mod.

## 1. Current state and boundaries

- The Paper module has command handlers in `SettlementCommand`, `AdminCommand` and `TemplateCommand`.
  They currently format chat output or open a written book. There is no inventory GUI implementation
  in the plugin.
- `SettlementService` and the core model already own most simulation, donation, trade, construction,
  lookup and save behavior. A GUI should call these services rather than duplicate their rules.
- `SettlementService.settlementAt(location)` resolves the active settlement by world and configured
  radius. This can guard the player village menu.
- `SettlementService.registry()` supplies the server-wide settlement registry used by admin commands.
- `hamletfolk.admin` already gates admin commands. It can gate the admin menu and every admin action
  dispatched by it.
- The repository has no settlement ownership or player-appointed governor model. A player "control"
  menu must not imply authority that the current data model does not grant. The ordinary village GUI
  can be informational and allow player donations; a separate management tier needs an explicit
  permission/ownership rule first.
- Current player donation uses the held item, checks storage/treasury room, routes emeralds to the
  treasury, and can pay for a matching village request. It is not a general player-controlled
  exchange or withdrawal system.
- Current villagers already use the vanilla merchant inventory. `VillagerListener` adjusts live
  vanilla recipe prices based on village stock/reputation and applies completed trades to village
  stores. Replacing that with a chest menu would need to preserve those rules and is not a minimal
  first step.
- `Merchant` is a simulated occupation. There is no `Treasurer` or `Steward` occupation and no
  dedicated NPC bank GUI today.

Relevant code: [`SettlementCommand.java`](../paper/src/main/java/io/github/skyeberhard/hamletfolk/paper/SettlementCommand.java),
[`AdminCommand.java`](../paper/src/main/java/io/github/skyeberhard/hamletfolk/paper/AdminCommand.java),
[`TemplateCommand.java`](../paper/src/main/java/io/github/skyeberhard/hamletfolk/paper/TemplateCommand.java),
[`SettlementService.java`](../paper/src/main/java/io/github/skyeberhard/hamletfolk/paper/SettlementService.java),
[`VillagerListener.java`](../paper/src/main/java/io/github/skyeberhard/hamletfolk/paper/VillagerListener.java),
and [`docs/COMMANDS.md`](COMMANDS.md).

## 2. Minimal design principles

1. **Use one menu system:** Paper inventory menus with a typed `InventoryHolder` for each Hamletfolk
   menu. No client mod and no custom screen protocol.
2. **Keep data and rules out of menu listeners:** listeners read the menu context, validate the
   action, invoke a service operation, then redraw or close the menu. Do not implement economy rules
   inside click handlers.
3. **Share command behavior:** where a command currently contains useful logic, extract only the
   operation needed by both command and menu. Keep command syntax and output working.
4. **Use one compact context:** menu holder stores a menu kind, optional settlement UUID, and page or
   selected-record key. Resolve the current settlement from its UUID when handling a click; do not
   keep a stale `Settlement` object in a long-lived menu.
5. **Revalidate on every click:** check player online/world, permission, current settlement bounds,
   target still exists, and action preconditions. A menu can remain open while the world changes.
6. **Treat menu slots as buttons:** cancel item movement for Hamletfolk menus, ignore clicks in the
   player's own inventory unless explicitly needed, and validate the top inventory holder. Never
   treat a displayed icon as trusted input.
7. **Prefer read-only first:** make menus useful before adding high-impact controls. Use a confirmation
   view for irreversible or destructive actions.
8. **Retain chat and books for text:** free-form names/numbers, long reports, errors, logs and console
   operations remain well served by commands/chat. The GUI can route to them or prompt the player.

Suggested small code footprint:

- `SettlementMenuService`: opens menus, builds pages, resolves settlement context, refreshes views.
- `SettlementMenuListener`: handles inventory clicks/close and any chat prompts.
- `MenuHolder`: typed holder/context, rather than relying on inventory titles or item names.
- A small set of helper methods extracted from command classes for operations that need to be shared.

Avoid a generic GUI framework, custom packet code, persistent GUI state, or a new UI abstraction
layer until actual menus demonstrate a need for them.

## 3. Entry points and permissions

- `/settlement` remains the current text summary for backward compatibility.
- Add `/settlement gui` (or `/settlement menu`) to open the player village menu. Require a player and
  a settlement at their current location; otherwise give a short message and retain `survey` as the
  anywhere-in-world command.
- Add `/settlement admin gui` to open the server-wide admin menu. Require
  `hamletfolk.admin`; works only for an online player. Admin commands continue to work from console.
- Optional clickable links in current chat output can open the relevant GUI. This is convenience,
  not a separate interaction framework.
- All player village menu actions resolve the active settlement again and require the player to
  remain within its bounds. There is no owner-only branch until an ownership/control model exists.

## 4. Player village GUI

### Main page

Use a single chest-style page with buttons for:

- **Overview** — name, population, children, character, housing, stores, treasury, danger/alert and
  active wanted request.
- **Residents** — paged directory and a read-only detail page per resident.
- **Buildings** — registered, built and planned buildings.
- **Beds** — total/free/claimed summary, with pages of coordinates if useful.
- **Plan** — current planner directives, districts, village character/direction, recent decisions and
  a button to toggle lot outlines.
- **Projects** — current project, progress, builder and recent completed work.
- **History** — open the existing written book, which is already a good interface for long text.
- **Treasury / Donate** — show treasury balance, capacity, room, open requests, and the currently
  held item's donation estimate. Offer an explicit "donate held stack" action; ask for a number in
  chat for partial quantities. Preserve current proximity-to-treasury rule when a treasury building
  exists.
- **Survey** — run the existing local survey at the player's position and show a compact results
  page. Offer no background/remote survey in this menu.

### Resident detail

Show name, pronouns, age/stage, occupation, mood, wealth and experience, matching `/settlement
residents`. Keep resident actions read-only in the first release. Do not expose `admin job` through
this page.

### Treasury and donation scope

The first version is a donation/deposit front end to the existing `/settlement donate` behavior:

- Display held item and amount, value, category, storage room, and request/payout eligibility.
- Make the player choose a donation action explicitly; never remove items just by opening the menu.
- Use the same `Donation` and request payout logic as the command, including capacity and treasury
  rules, reputation and save requests.
- Support a custom quantity with a temporary, player-bound chat prompt. Cancel if the player closes
  the prompt, changes world/settlement, changes the held stack, or times out.
- Do not add withdrawal, arbitrary conversion rates, or a bank account balance for players; none
  exists in the current design.

## 5. Admin Control GUI

### Settlement directory

List every settlement in the registry, not only those near the admin. Display name, short ID, world,
coordinates, population and abandoned state. Use pages and simple name/ID search through a chat prompt
only if a single page becomes insufficient. Selecting one opens its detail view.

### Settlement detail view

Use the existing `SettlementInspector.report`/inspection data where practical. Provide buttons to
open the player-style overview pages, then a distinct Admin Actions page. Mark the target village in
the title/lore and re-resolve its ID on every click.

### Admin action grouping

- **Safe/low-impact:** inspect, save all, rename (chat prompt), reload dialogue, show captured template
  list.
- **Simulation controls:** warp days, fast-forward start/stop/speed, resettle, replan, regrade.
- **World/build controls:** found a village at the admin's current position; build a selected template
  at the admin's current position; cancel an open construction project.
- **Villager targeting:** ignore and job require selecting/looking at a villager. Keep the commands as
  the primary route in the first GUI release; later provide a nearby-villager selector with explicit
  range and settlement checks.
- **Template capture:** pos1/pos2 are spatial selections, so keep as commands or add buttons that set
  the current position as a corner; type/tier/style selection can then be a menu. This is optional
  admin tooling, not core village management.

Require a second confirmation page for replan, resettle, cancel project, warp/fast-forward and any
future action that removes, relocates or overwrites data. GUI actions must use the same command
permission and service validation. Admin menu is unavailable to console, but all existing commands
remain available there.

## 6. Map of every current command

### `/settlement` player tree

| Command | GUI mapping | Remains useful as command/chat because… |
|---|---|---|
| `/settlement` / `info` | Main village overview page | Fast text summary; preserve as default behavior. |
| `history` | Button opens the current written history book | Book handles long, scrollable history better than a chest menu. |
| `residents` | Paged resident directory, then read-only details | Command remains a compact directory and fallback. |
| `donate [amount\|all]` | Treasury/Donate page with estimate and explicit deposit action | Partial amount and error details may need chat; keep command exactly. |
| `buildings` | Buildings page, paginated if necessary | Command remains fast for short lists. |
| `beds` | Bed summary and optional coordinate pages | Command has current five-second cooldown due to potentially expensive POI/chunk search; GUI must use the same rate limit and show a loading/cooldown result. |
| `plan` | Plan page with directives, districts, character and decisions | Long explanation may be easier as chat/book; split GUI into detail pages, not a new simulation view. |
| `lots [on\|off]` | Toggle button in Plan; status shown in menu | World particles remain the actual visualization; command remains convenient. |
| `projects` | Projects page and project detail | Command is a quick status path. Admin cancellation is separate. |
| `survey` | Run at current location, show a Survey result page | Position-sensitive and works outside a village; keep as command for quick site checks. |
| `gui` (new) | Opens player village menu | Must fail clearly outside a village. |

### `/settlement admin` tree

| Command | Admin GUI mapping | Keep command / constraints |
|---|---|---|
| `list` | Admin directory page | Command is essential for console and copyable output. |
| `inspect [name\|id]` | Select directory entry, open detail | Keep name/ID lookup command and console support. |
| `rename <name\|id> <new name>` | Select settlement, then chat prompt for name | Free-form text and console support remain easier by command. |
| `save` | Global Save button with result page/message | Keep console command. |
| `ignore` | Optional nearby-villager selector, later | Current look-at target behavior is simplest and safer as command. |
| `capture pos1\|pos2` | Optional set-corner buttons using current position | Spatial workflow still needs player in-world; command remains minimal. |
| `capture <type> [tier] [style]` | Optional type/tier/style selection after corners are set | Retain command for complete precise input and errors. |
| `capture list` | Captured Templates page | Command remains useful from console. |
| `capture remove <type> <tier> [style]` | Optional template selection + confirm remove | File-affecting admin operation; command remains available. |
| `build <type> [tier] [style]` | Optional select type/tier/style then build at current location | Player-location requirement remains; confirm before placing blocks. |
| `cancelproject [name]` | Admin village detail action, only if project open, then confirm | Keep command for console and quick use. |
| `found [name] [residents]` | Optional Found action at current admin location, with prompts | Needs a player position and free-form name/count; command remains direct route. |
| `resettle [name] [travellers]` | Select target, select count, confirm | Keep command and console path. |
| `regrade [name]` | Target village action + confirmation | Keep command as direct route. |
| `replan [name]` | Target village action + strong confirmation | Destructive to existing plan; command remains and GUI must be explicit. |
| `warp [name] <days>` | Target village, day-count input, confirm, show result | Numeric input and console support remain command strengths. |
| `job <occupation>\|unpin` | Possible later nearby villager + occupation picker | Current look-at target is clear; do not mix with resident detail until targeting is reliable. |
| `unstick [name]` | Target village action + confirm, report moved residents | Keep command for console and quick recovery. |
| `fastforward [name\|all] [speed\|stop]` | Running status + start/stop controls, numeric speed prompt | Keep command for console and precise control. |
| `dialogue` | Reload button + display up to the current validation errors | Command remains useful at console; errors may be more readable in chat. |
| `gui` (new) | Opens server-wide Admin Control | Require `hamletfolk.admin` and a player. |

### `/settlement brain` tree

| Command | Admin GUI mapping | Keep command / constraints |
|---|---|---|
| `status` | Brain status page | Text report remains useful and console-compatible. |
| `on` / `off` | Explicit toggle buttons with current state and confirmation | Keep global kill switch command. |
| `inspect` | Optional targeted villager detail, later | Requires looking at a villager; keep as command initially. |
| `debug on\|off` | Debug toggle buttons | Keep command for console and automation. |
| `report` | Link to textual report in chat | Timing report is text-heavy; menu not useful. |
| `behaviour <name> on\|off` | Behavior list with toggles and family context | Keep command for exact control and console. |
| `family <family> on\|off` | Family toggle list | Good fit for an admin settings page, but command remains. |
| `walk <villager> <x> <y> <z> [teleport]` | No first-version GUI | Selector/coordinates/teleport are precision debug tools; remain command-only. |

## 7. Profession-specific interfaces

### Merchant

**Do not replace the current villager trade screen in the first pass.** Hamletfolk already applies
village supply/reputation pricing on merchant inventory open, refuses sales the village cannot cover,
and records completed trades in its stores. This works with vanilla clients and the existing
MerchantInventory. A replacement chest menu would have to reimplement recipe selection, payments,
stock limits, price changes, villager experience/restock rules, player disconnects and inventory
transaction safety.

If a distinct Hamletfolk shop is later desired, implement it as an opt-in, separate storefront
interaction backed by a single transaction service shared with existing trade accounting. Do not
silently replace ordinary right-click trading. A chest-like menu is plausible for fixed stock, but
must reserve/check stock and atomically validate payment and item delivery on click.

### Treasury / bank

There is no Treasurer/Steward occupation or player bank account today. The minimal useful feature is
the **Treasury/Donate page** in the player village menu, reusing current donation and request payout
rules. A separate emerald/item exchange or withdrawals would be a new economy feature: define who
sets rates, what stock can be withdrawn, whether requests take priority, and what treasury limits
apply before designing controls. Do not add a Treasurer NPC until the role itself is designed.

### Other occupations

No occupation-specific GUI is justified by current code. Villagers currently retain their vanilla
trading behavior and talk interaction; occupation is a simulation value, not an interaction menu
contract. Add specialized screens only when an occupation gains a concrete player service that
cannot be represented in its existing vanilla trade window.

## 8. Other useful GUI candidates

- **Village map/plan viewer:** an optional read-only page with current stage, streets, lots and
  buildings. The actual plan outlines already use particles; avoid building a map renderer as part
  of the first GUI milestone.
- **Requests board:** compact list of wanted resources and emerald offers with a link to Donate.
  This is likely a section of the village dashboard rather than a separate GUI.
- **Player controls/ownership:** only after the project defines a settlement steward/owner, how that
  role is granted/revoked, and which actions it may perform. Until then, don't imply that standing in
  the village grants management rights.
- **No custom HUD initially:** requires continuous state presentation and is outside a simple
  inventory-menu design.

## 9. Minimal delivery sequence

1. **Menu plumbing:** typed holder, click cancellation/routing, permission and bounds revalidation,
   close handling. No feature changes.
2. **Read-only player GUI:** overview + links to residents, buildings, beds, plan, projects and
   history book. Paginate lists. Keep commands unchanged.
3. **Admin directory/detail GUI:** server-wide list, inspect pages, save and reload dialogue. This
   validates remote settlement selection without exposing risky controls.
4. **Player donation GUI:** estimate, explicit deposit, request/treasury details; share the exact
   donation operation with `/settlement donate`.
5. **Low-risk admin actions:** rename via prompt, then confirmation-gated project/resettlement/planning
   actions. Keep commands and console paths.
6. **Optional profession/UI experiments:** first test whether ordinary merchant trades plus a
   themed resource pack meet the need. Build a separate shop menu or a management-owner role only as
   separate features with their own transaction/permission designs.

## 10. Safety and implementation checks

- Keep menu actions synchronous on the server thread where Bukkit world/inventory state is read or
  changed.
- Before removing items from a player, calculate the accepted amount and destination capacity; only
  remove exactly what the existing donation logic accepts.
- Cancel item movement/dragging in plugin menus and prevent taking decorative icons.
- Recheck admin permission and current player-village bounds at click time, not only when opening.
- Bind admin selection to a settlement UUID, not a list index/name that can change.
- Close or refresh menus after world change, settlement change or target removal.
- Add confirmation pages for actions that discard plans/projects, alter membership, advance time or
  place blocks.
- Keep human-readable success/error output in chat, including save errors and validation failures.
- Add focused tests for extracted core operations and menu context validation; do not put simulation
  or trade rules in Bukkit click handlers.

## Recommendation

Build the player read-only dashboard and admin settlement directory first, then add donation as the
first transactional menu. Keep written history and vanilla merchant trading intact. Defer player
village control, arbitrary treasury exchange and custom merchant inventory until ownership and
transaction rules are explicitly designed.
