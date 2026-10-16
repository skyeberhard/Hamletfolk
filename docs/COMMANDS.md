# Commands

Everything is under one command, `/settlement` (alias `/village`). Subcommands tab-complete.
Source of truth: `SettlementCommand`, `AdminCommand`, `TemplateCommand` in `paper/`.

## Permissions

| Permission | Default | Lets you |
|---|---|---|
| `hamletfolk.use` | everyone | Use `/settlement` and talk to residents |
| `hamletfolk.ignore` | everyone | Place an `[Exempt]` sign (leaves unaffiliated villagers around it alone) |
| `hamletfolk.ignore.village` | op | An `[Exempt]` sign that also frees villagers already in a village |
| `hamletfolk.admin` | op | `/settlement admin ...` and `/settlement brain ...`, also from the console |

## Player commands (`/settlement ...`)

Most need you to be standing in (or near) a village. `survey` works anywhere.

| Command | What it does |
|---|---|
| `/settlement` or `info` | Village summary: name, founding day, population, character, stores, treasury, housing, troubles, wanted items |
| `history` | The village's written history (events, newest first) |
| `residents` | Everyone in the village: name, age, trade, mood, wealth, xp |
| `buildings` | Registered buildings (from signs, built, or planned) |
| `beds` | Beds the village counts, and which are claimed |
| `plan` | The village plan: layout, stage, what the planner is asking for and why |
| `lots [on\|off]` | Shows plan outlines (streets, lots) in the world; stays on until turned off |
| `projects` | Open construction projects and works |
| `survey` | Reads the land where you stand (biome, features) and says what it would favour |
| `donate [amount\|all]` | Gives the item in your hand to the stores or the treasury; no amount shows the value, `all` gives the stack |

### In the world (no command)

| Action | Result |
|---|---|
| Sneak + right-click a villager | Talk: name, job, mood, a line of dialogue (plain right-click still trades) |
| Place a sign `[Farm]`, `[Mine]`, `[Smithy]`, `[House]`, `[Guard Post]`, `[Shop]`, `[Treasury]` (and more) | Registers that building for the village |
| Place a sign `[Exempt]`, second line a radius | Leaves villagers in that radius alone |

## From the server console

| Command | What it does |
|---|---|
| `settlement` or `settlement list` | One line for every village: day, people, stores, treasury, jobs, beds, what it is building, what is wrong |
| `settlement info|history|residents|buildings|plan|projects <village name or id>` | The same reports a player gets, for the named village (history as plain lines) |
| `settlement admin ...`, `settlement brain ...` | As below |

The console also prints the villages' history as it is written, and a one-line summary of each village every few minutes (R1.34; `console.events` and
`console.digest-minutes` in `config.yml`).

## The dashboard (R1.35)

Not a command: with `dashboard.enabled: true` in `config.yml` the server serves a read-only page at `http://127.0.0.1:8123/` (port, bind address and refresh
rate are in the config) showing every village, what is in its way, and a page and a map for each. See docs/GUI_PLAN.md section 11.

## Admin commands (`/settlement admin ...`, op or console)

| Command | What it does |
|---|---|
| `list` | All villages: id, name, population, world, location. First thing to run for a split village |
| `inspect [name\|id]` | Full detail on one village (nearest if no name) |
| `rename <name\|id> <new name>` | Renames a village |
| `save` | Writes the save file now |
| `ignore` | Look at a villager within 6 blocks: toggles leaving it alone (not in any village, not re-priced, not moved). Player only |
| `found [name] [residents]` | Founds a new planned village where you stand, with that many founders (default 6). Player only; open, fairly flat land |
| `replan [name]` | Throws away and regenerates the village plan |
| `warp [name] <days>` | Runs a village up to 60 days ahead at once |
| `fastforward [name\|all] [speed\|stop]` | Runs a village at several times speed in real time (in memory; stops on restart). No arguments shows what is running |
| `job <occupation>\|unpin` | Look at a villager: pin them to a job, or release them |
| `unstick [name]` | Frees residents of a village that are walled in |
| `cancelproject [name]` | Cancels the village's open construction project |
| `dialogue` | Reloads `plugins/Hamletfolk/dialogue/` without a restart |
| `capture pos1\|pos2` | Marks the two corners of a building to save as a template |
| `capture <type> [tier] [style]` | Saves the marked area as the template for a building type (type may be `gatehouse` or `tower`; tier 2 planks, 3 stone) |
| `capture list` / `capture remove <type> <tier> [style]` | Lists or deletes captured templates |
| `build <type> [tier] [style]` | Builds a template where you stand (types: house, farm, smithy, mine, guard_post, shop, treasury, gatehouse, tower) |

## Brain module (`/settlement brain ...`, op or console)

For the optional villager-behaviour module (M9). Safe to ignore until the city rules work.

| Command | What it does |
|---|---|
| `status` | Whether the module is on, which behaviours and families are active |
| `on` / `off` | The kill switch: attach or remove the behaviours from every villager |
| `inspect` | Look at a villager: their brain's current activity and behaviours |
| `debug on\|off` | Records every behaviour decision (only while on) |
| `report` | Timing per behaviour against its budget |
| `behaviour <name> on\|off` | Switch one behaviour |
| `family <family> on\|off` | Switch a group of behaviours |

## Useful vanilla and server commands

| Goal | Command |
|---|---|
| Become operator | `op <name>` (server console) |
| Find a village | `/locate structure minecraft:village_plains` then `/tp` |
| Skip a day | `/time add 24000` (the simulation checks every 5 seconds). 100 days: `/time add 2400000` |
| Spawn a smith | `/summon villager ~ ~ ~ {VillagerData:{profession:"minecraft:toolsmith",level:1,type:"minecraft:plains"}}` |
| Spawn a zombie | `/summon zombie ~ ~ ~` |
| Start a raid | `/effect give @s minecraft:bad_omen 600 0`, then walk into a village |
| Villagers turn when killed | `/difficulty hard` |
| Inspect a villager's data | `/data get entity @e[type=villager,limit=1,sort=nearest] BukkitValues` |
| Give items to test donations | `/give @s minecraft:iron_ingot 64` |
| Stop the server cleanly | `stop` (console) |
| Read saved data | `paper/run/plugins/Hamletfolk/settlements.json` |
| Config | `plugins/Hamletfolk/config.yml` (restart to apply): radius, backups, aging, prices, economy, construction, brain |
