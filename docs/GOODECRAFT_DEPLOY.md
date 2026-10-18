# Putting Hamletfolk on the GoodeCraft server

For Owen, who reviews and installs (see [DEPLOYING.md](DEPLOYING.md) for the pin-and-verify routine and
`goodecraft/docs/hamletfolk.md` for how the last install was done). Written after reading the GoodeCraft repository and building this code against
its pinned Paper API; the facts below are from that, and anything not checked says so.

## What is different from what is live

Production runs `98d2f0b` (27 September, save format 8). The current branch is 230 commits and about 58,000 lines further on. In plain terms, the
live plugin gives villagers names, an economy and a history. The current one also:

- **builds**: villages plan themselves, lay streets, raise buildings from the stores (houses, farms, mines, shops, guard posts and the like), improve
  them as their people gain skill, light their streets, and wall themselves in after attacks;
- **changes the land** a little: lumberjacks fell trees near the village and plant saplings, miners dig a small fenced quarry beside the mine;
- puts **things in the world**: a notice board (a lectern with a book) by the bell, store barrels in the village's buildings, signs by house doors,
  gold-named keepsakes in the shop's barrel;
- adds **things players can do**: sneak + right-click a villager for a conversation (and now and then a gift), trade with a master craftsman's
  extra offer, donate to open requests, read the notice board;
- adds a **web dashboard** (off unless switched on; bound to the server's own address) and an optional **villager-behaviour module** (off).

Everything that builds or digs only runs while a player is within 128 blocks, unless `construction.unattended` is turned on (it is off).

## The risks, and what has been done about them

| Risk | Status |
|---|---|
| Builds Paper API compatibility | Built and tested against production's pinned API (`26.2.build.129`) with `tools/hamletfolk-paper.gradle`: all tests pass. Not run on the server itself. |
| Villages everywhere | By default every world is tracked and built in. **Use `worlds.allow`** (profile below). |
| Rollback with CoreProtect | The village's block changes are now logged to CoreProtect as `#Hamletfolk` (R1.52). Needs CoreProtect running; not yet seen on the real plugin. |
| Protected ground | The village now does not start a building (nine points of its footprint are asked), lay a street block, put a notice board or store barrel, fell a tree or dig a quarry where WorldGuard would stop a stranger building (R1.52). Not covered: a small region wholly inside a lot that touches none of the nine points, and taking down an old building for an upgrade. Asked by name at run time, so it works only if WorldGuard's API matches what the code calls; if it does not, a warning is logged once and building carries on unprotected. **Check the log line on the first start.** |
| World clocks | A village's day is its world's clock day. A world with a locked clock (`time_lock`) or with no clock at all (checked: such a dimension reads day 0 for ever) has frozen villages; a `day_cycle_speed` of 2 doubles the pace. Pick worlds with an ordinary clock. The dashboard's world page shows each clock. Unchecked: whether worlds sharing one clock all speed up when GoodeCraft speeds one. |
| Save format | The plugin is on format 30 and migrates older saves on first start (each step is tested). An older plugin cannot read a newer file: to roll back, restore the backup in `plugins/Hamletfolk/backups/` or the NAS copy. Production's file was empty on 27 September; **run a copy of today's file through the new build on the dev server first.** |
| Youth group | Villager dialogue and the history are written in plain, mild language; marriages, births and old-age deaths appear in the history, and the history book is readable by anyone. Names of players who took from a store, broke a barrel or killed a resident appear in it; `history.misdeeds-name-players: false` writes "a visitor" instead (R1.53). Worth reading a village's history book before opening it to the group. |
| Bedrock | Names arrive with Floodgate's "." in front; it is now dropped (R1.53). Untested with a Bedrock client: cancelled clicks in the store barrels and the extra trade offer on a master craftsman should behave, but Geyser may show a flicker until the window is closed. |
| Linux | Developed and run on Windows only. Paths use `java.nio` throughout, but no one has run this on Linux yet: the dev server is the place. |
| The brain module | Not in a build made as `tools/build-hamletfolk.sh` does it (it needs the exact server jar to build against), so it stays off. Leave `brain.enabled: false`. |
| Hot reload | As before: never. Stop the server, swap the jar, start. |

## Recommended `plugins/Hamletfolk/config.yml` for a first start

Only the lines that differ from the shipped defaults, or that are worth setting out loud. Owen picks the worlds.

```yaml
worlds:
  allow: [thegoodeplace]      # start with the main survival world; add others one at a time after looking at them
  deny: []
world-marks:
  enabled: false              # no felling or quarrying until the villages have been watched for a few days
construction:
  enabled: true
  unattended: false           # builders work only while a player is within 128 blocks
  guild: true
dashboard:
  enabled: false              # turn on later and reach it through ssh -L, never through the public tunnel (see DASHBOARD.md)
  allow-actions: false
brain:
  enabled: false
protection:
  coreprotect: true
  worldguard: true
  user: "#Hamletfolk"
names:
  strip-bedrock-prefix: true
history:
  misdeeds-name-players: false
trade:
  enabled: true               # caravans between villages in the same world; off if you would rather not
stall:
  enabled: true
gifts:
  enabled: true
aging:
  lifespan-scale: 20
  old-age-deaths: false       # villagers are not removed from the world for dying of age until you have seen how villages hold up
```

The `[Exempt]` sign (see TESTING.md) keeps the plugin's hands off villagers and ground around a sign. Put one by anything that must not be touched.

## Before it goes on the real server

1. **Copy today's `settlements.json`** (and the villagers' world data if you want it exact) to the dev server and start the new build there. It should
   migrate with no error; open `/settlement` in a village.
2. **Build the exact commit** on the dev server (Linux, Java 25, Paper 26.2, GoodeCraft installed) with `tools/build-hamletfolk.sh` after pointing its
   `COMMIT` at the one chosen, and record the SHA-256 of the jar.
3. **Walk the checklist** in [PLAYTEST.md](PLAYTEST.md) on the dev server, and have one Bedrock player do the barrel and trade parts.
4. **Watch the first start's log** for the two protection lines ("Logging the village's building to CoreProtect as #Hamletfolk", "Keeping the
   village out of places WorldGuard protects") and any warning after them.
5. **Back up** (the NAS routine) and deploy by pin-and-verify. Record it in PRODUCTION.md.

## After it is on

- Look at the first village's notice board, barrels and history; confirm in CoreProtect (`/co lookup user:#Hamletfolk`) that its blocks are logged and that
  a rollback of one building works.
- Turn on the next world, then `world-marks`, then the dashboard, one at a time, a few days apart.
- Expect villages to take real days to change: they build only while someone is near, and what they build is paid for from their own stores.

## Questions for Owen

- Which worlds should have villages? (The profile starts with `thegoodeplace`.)
- Is `day_cycle_speed` or `time_lock` meant to speed up or freeze a world's villages? Do worlds sharing a clock speed up together?
- Should the dashboard ever be reachable from outside the server (it is meant to be reached through ssh only; the Cloudflare login idea is on hold)?
- Is a villager that dies of old age being removed from the world acceptable for the youth group, or should that stay off?
