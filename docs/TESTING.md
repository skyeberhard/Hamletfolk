# Testing

## Automated

```
./gradlew build          # compiles everything and runs the core unit tests
./gradlew :core:test     # just the simulation tests
```

The simulation core has no Minecraft dependencies, so most logic is tested here.
New simulation behavior should come with a unit test.

## Local server

### Before you start (once)

1. **Java 25 (JDK).** Minecraft 26.x needs it. Install
   [Eclipse Temurin 25](https://adoptium.net/temurin/releases/?version=25) and check with
   `java -version` in a new terminal.
2. **Git**, to download the code: [git-scm.com](https://git-scm.com/downloads).
3. **Minecraft Java Edition** with the version from `minecraftVersion` in `gradle.properties`
   (currently 26.2). In the launcher: Installations → New installation → pick that version.
4. **The code:**
   ```
   git clone https://github.com/skyeberhard/Hamletfolk.git
   cd Hamletfolk
   ```

### Running the server

```
./gradlew runServer        # macOS / Linux
gradlew.bat runServer      # Windows
```

This builds the plugin, downloads Paper, and starts a server in `paper/run/` with the plugin
installed. The first run takes a few minutes while it downloads everything.

1. The first run stops and asks you to accept the Minecraft EULA. Open
   `paper/run/eula.txt`, set `eula=true`, and run the command again.
2. When the console says `Done`, start Minecraft with the matching version and go to
   Multiplayer → Direct Connection → `localhost`.
3. In the server console (the terminal), give yourself operator permissions: `op <your name>`.
4. Stop the server by typing `stop` in the console.

Run `git pull` to get the latest changes, then `runServer` again; it rebuilds the plugin each time.

**Start fresh:** delete `paper/run/world*` and `paper/run/plugins/Hamletfolk/`.

**Without building:** download the jar from the latest
[GitHub Actions run](https://github.com/skyeberhard/Hamletfolk/actions) (artifact
`Hamletfolk-plugin`) and drop it into the `plugins/` folder of any Paper 26.2 server.

### Useful commands

| Goal | Command |
|---|---|
| Find a village | `/locate structure minecraft:village_plains` then `/tp` |
| Skip a day (the simulation checks every 5 seconds) | `/time add 24000` |
| Spawn a smith | `/summon villager ~ ~ ~ {VillagerData:{profession:"minecraft:toolsmith",level:1,type:"minecraft:plains"}}` |
| Spawn a zombie | `/summon zombie ~ ~ ~` |
| Start a raid | `/effect give @s minecraft:bad_omen 600 0`, then walk into a village |
| Make zombies infect villagers | `/difficulty hard` (villagers killed by zombies always turn) |
| Cure a zombie villager | Throw a splash potion of weakness at it, then use a golden apple on it; wait ~3-5 minutes |
| Make villagers breed | Give them bread; they need free beds |
| Read the saved data | `paper/run/plugins/Hamletfolk/settlements.json` |

## Playtest scenarios

Run these on a fresh world. Each names the roadmap item it verifies. Record results
in the PR that changes the behavior.

| # | Verifies | Steps | Expected |
|---|---|---|---|
| T1 | R0.5 | Start the server | Console shows `Tracking 0 settlements.` and no errors |
| T2 | R0.1 | Go to a village and look at a villager | A name like *Harold Carter* shows on hover |
| T3 | R0.3 | `/settlement` in the village | Name, founding day, population, stores, danger |
| T4 | R0.4 | Sneak + right-click a villager | Name, job and mood, then a line of dialogue; plain right-click still opens trading |
| T5 | R0.4 | Talk to the same villager 6 times | Greeting changes from stranger to "Good to see you, <you>!" |
| T6 | R0.2, R0.4, R1.26 | Place a smithing table and summon a villager next to it so they become a toolsmith (without a workstation vanilla removes the job); `/time add 24000` twice; talk to them | `/settlement` shows "Troubles: no metal". A starving village gives the famine line first ("There's no food left…"), so the toolsmith mentions food, not metal |
| T6b | R0.4, R1.26 | After T6, hold bread and `/settlement donate all` until the village has food; `/time add 24000`; talk to the toolsmith | Famine ends and they say there's no metal |
| T7 | R0.2 | Hold iron ingots, `/settlement donate all`, skip a day | Shortage ends; history shows the donation and "Supplies of metal were restored." |
| T8 | R0.3 | Let a zombie kill a villager | History: "<name>, the <job>, was killed by a zombie." |
| T9 | R0.3 | Breed two villagers | The baby shares a parent's family name; history records the birth |
| T10 | R0.3 | Win a raid | History names you among the defenders |
| T11 | R0.1 | Restart the server, check a villager's name and `/settlement history` | Everything is unchanged |
| T12 | R0.2 | `/time add 2400000` (100 days) | The settlement catches up 60 days (the configured cap) without a lag spike |
| T13 | R1.3 | Restart the server three times | `plugins/Hamletfolk/backups/` holds a timestamped copy per restart, never more than 5 |
| T14 | R1.2 | On hard difficulty, let a zombie kill a named villager; check `/settlement`; then cure the zombie villager | While a zombie: history says they were turned, `/settlement` shows 1 lost to zombies. After curing: same name; `/settlement history` says they were cured by you |
| T15 | R1.2 | Turn a villager, restart the server, then cure them | They come back with the same name after the restart |
| T16 | R1.18 | `/settlement donate all` a stack of iron, wait 5 seconds, then kill the server process hard (not `stop`) and restart | `/settlement` still shows the donated metal, and history still has the donation |
| T17 | R1.4 | From the server console: `/settlement admin list`, then `/settlement admin inspect <name>`, then `/settlement admin rename <name> New Haven` (two words), `/settlement admin inspect New Haven`, then `/settlement admin save` | List shows every settlement with a short id; inspect prints stores, flow, conditions and recent history; rename shows in `/settlement history`; save reports the count. As a non-op player, `/settlement admin list` is refused |
| T18 | R1.10 | Set `worlds.deny: [world_nether]` in config.yml, restart, then in the Nether summon a villager and sneak + right-click it, run `/settlement`, and check the console | The villager opens trading as normal, `/settlement` says you are not in any settlement, nothing is recorded, and there are no errors in the console. In the overworld everything still works. Then set `worlds.allow: [world]` with an empty deny list and repeat: the Nether is ignored again |
| T19 | R1.19 | Set `backups.interval-hours: 0.01` (about 36 seconds) and `backups.keep: 3`, start the server, and leave it running for three minutes | The console logs "Backed up settlements to ..." roughly every 36 seconds while running, `plugins/Hamletfolk/backups/` never holds more than 3 files, and a backup taken after donating items contains the donation. With `interval-hours: 0` no periodic backups appear |
| T20 | R4.3 | In a village with no wood, `/settlement` shows an unemployed resident becoming a lumberjack over a day or two. Unload and reload the chunk, then place a workstation next to them | They stay a lumberjack: neither `track()` nor a vanilla career change puts them back to their vanilla profession |
| T21 | R4.10 | Breed two villagers, wait for the baby to grow up, and re-check `/settlement` (or restart to force a re-track). Best done in a village short of wood | A grown child with no vanilla job yet takes the shortest trade, or a parent's if it is also short, and `/settlement history` says "was apprenticed as a ...". A child that already claimed a vanilla workstation keeps that job and is not apprenticed: known limit until M2 buildings decide who gets a workstation |
| T22 | R4.1 | In a village with several free beds (more beds than residents), give it plenty of food (donate hay bales or bread with `/settlement donate all`) and let a day or two pass | A new villager appears near the village centre within a few in-game days, at most one every 3 days, and `/settlement history` shows "... settled in <village>". With no free beds, or food under 20 per resident, none appears |
| T23 | R3.8 | Hold 5 iron blocks; run `/settlement donate` (no amount), then `/settlement donate 2`, then `/settlement donate all` | The first only says the stack is worth 45 metal and takes nothing. The second takes 2 blocks and credits 18 metal, leaving 3 in hand (check `/settlement`). The third takes the rest for 27 more. Holding a diamond pickaxe says it's worth more than a wooden one; `/settlement donate 0` and `/settlement donate lots` are refused |
| T24 | R4.14 | Run `/settlement residents`. Then upgrade: copy a format-4 `plugins/Hamletfolk/settlements.json` from an older build, start this build, and run it again | Each line ends with the resident's pronouns, e.g. `— farmer (she/her)`, and names such as Anya or Greta show she/her and Harold or Silas he/him. After the upgrade every resident keeps their name and has pronouns. Talking to a child of a resident sometimes mentions their mother, father or parent, with matching pronouns |
| T25 | R4.15 | Set `aging.old-age-deaths: true` in config.yml and restart. Run `/settlement residents`, then `/time add 240000` ten times (about 100 in-game days, each step within `max-catch-up-days`) | Adult lines show age in days (and "(elder)" past 60). With the setting off (the default) nobody dies and elders still show. With it on, as residents pass their maximum age, `/settlement history` records "... died of old age, at N days" and that villager disappears from the world. A villager that was unloaded when this happened is removed when its chunk next loads, and does not come back as a new resident. An elder, spoken to, sometimes mentions their years |
