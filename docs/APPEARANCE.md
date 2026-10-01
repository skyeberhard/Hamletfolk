# Appearance hooks (R4.16)

Hamletfolk is a server-side plugin and players keep vanilla clients, so it cannot draw a villager
differently by itself. There are two kinds of hook, and it matters which reaches the player:

- **Persistent data tags** (section 1) stay **on the server**. Datapacks, command selectors and
  other plugins can use them. A resource pack or client mod cannot see them.
- **The villager type** (section 2) is part of what the server tells clients about a villager, so
  a plain **resource pack** can reskin by it. This is the hook a visual pack uses.

This page is that contract. Changing a value here breaks packs built against it, so it only
changes with a note in the CHANGELOG.

> **Status: not yet playtested.** The Paper layer has never run on a server (R1.1). Everything
> below marked *unverified* is an assumption to check there. Scenario T26 in `docs/TESTING.md`
> records the answers.

## 1. Data on every tracked villager (always on)

Three strings in the villager's persistent data, under the plugin's namespace `hamletfolk`:

| Key | Values |
|---|---|
| `hamletfolk:gender` | `female`, `male` |
| `hamletfolk:occupation` | `unemployed`, `nitwit`, `farmer`, `fisherman`, `butcher`, `shepherd`, `leatherworker`, `fletcher`, `mason`, `lumberjack`, `merchant`, `armorer`, `weaponsmith`, `toolsmith`, `cartographer`, `cleric`, `librarian` |
| `hamletfolk:life_stage` | `child`, `adult`, `elder` |

- The occupation is the **simulation's**, not the vanilla profession. They differ for jobs with no
  vanilla profession (`lumberjack`) and wherever the simulation has reassigned someone (R4.3).
- A stage changes with age, so tags are refreshed when a villager is tracked and about every five
  seconds for loaded villagers (the grown-up flag is re-read from the villager each time).
- Read them in game with
  `/data get entity @e[type=villager,limit=1,sort=nearest] BukkitValues`.
  *Unverified:* the exact NBT layout Paper writes for these keys.
- **They do not reach clients.** *Unverified, but this is how vanilla works:* the server sends a
  client only a fixed set of fields about an entity, not its saved data, so a resource pack or a
  client mod cannot read these keys in multiplayer. Use them on the server side, for example
  `@e[type=villager,nbt={BukkitValues:{"hamletfolk:gender":"female"}}]` in a datapack function or
  command block. The villager's custom name (when `show-names` is on) *is* sent to clients.

## 2. Villager type (optional, off by default)

With `appearance.villager-type: true` in `config.yml`, the plugin also sets each tracked
villager's vanilla **type** (the biome look: plains, desert, ...) from its gender and life stage.
A plain resource pack that redraws those seven type skins then shows the difference with no client
mod.

| | adult and child | elder |
|---|---|---|
| female | `plains` | `snow` |
| male | `desert` | `taiga` |

- **Children use the adult's type.** The game already draws a child with the baby model.
- **`jungle`, `savanna` and `swamp` are never used.** A pack can leave those three alone. Villagers
  the plugin does not track (excluded worlds) keep their biome type, which can be any of the
  seven, so the pack's gender looks will show on those too, wrongly. Only the unused three look
  unchanged.
- **Profession still works as usual.** The profession is a separate overlay, so a pack can reskin
  by profession and by this type together.
- The mapping lives in `core` (`Appearance.villagerType`) with a unit test, so this table and the
  code cannot drift apart without a failing test.

### What setting it costs

- The biome look of tracked villagers is replaced. Turn it on only if you ship a pack that
  redraws the seven types, or villagers will look arbitrarily odd (a "snow" villager in a desert).
- **A villager's type is not only cosmetic.** In vanilla some trades depend on it (for example a
  master fisherman's boat) and the villager trade rebalance ties some trades to type. Existing
  trades are not redone when the type changes, but trades unlocked afterwards follow the new type.
  *Unverified for 26.2.*
- **Turning the setting off puts the biome look back.** The plugin stores each villager's original
  type (`hamletfolk:original_type`) before the first change and restores it. Villagers changed
  by an earlier build without this are not restored.
- **Zombie villagers** have their own skins, which a pack would need to cover too. Vanilla is
  believed to keep a villager's type through infection and cure; *unverified*.
- *Unverified:* how a Bedrock player (through Geyser) sees the type changes. Treat the pack as
  Java-only until T26 says otherwise.

## 3. Building the pack

The type and profession skins are in the vanilla client jar under
`assets/minecraft/textures/entity/villager/`. Extract them from the client version your server
targets rather than trusting a path from this page: the layout has changed between versions.
Override the four type skins in the table above (female / male, adult / elder) and leave the
other three alone.

Offering the pack to players is done with the server's own resource-pack settings
(`server.properties`), which Hamletfolk does not touch.

## 4. What this does not give you

Per-individual skins, or appearance that depends on anything beyond gender, life stage and
profession. Vanilla cannot do that without a client mod. If you want it, use the data in section 1
with a mod that can read entity data, and accept that those players are no longer vanilla.
