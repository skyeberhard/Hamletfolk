# Testing

## Automated

```
./gradlew build          # compiles everything and runs the core unit tests
./gradlew :core:test     # just the simulation tests
```

The simulation core has no Minecraft dependencies, so most logic is tested here.
New simulation behavior should come with a unit test.

## Local server

```
./gradlew runServer
```

This builds the plugin, downloads Paper for the `minecraftVersion` in
`gradle.properties`, and starts a server in `paper/run/` with the plugin installed.

1. The first run stops and asks you to accept the Minecraft EULA. Open
   `paper/run/eula.txt`, set `eula=true`, and run the command again.
2. In the Minecraft launcher, pick the same Minecraft version, then connect to `localhost`.
3. In the server console, give yourself operator permissions: `op <your name>`.
4. Stop the server with `stop` in the console.

Re-run `./gradlew runServer` after code changes; it rebuilds the plugin each time.

**Start fresh:** delete `paper/run/world*` and `paper/run/plugins/Hamletfolk/`.

### Useful commands

| Goal | Command |
|---|---|
| Find a village | `/locate structure minecraft:village_plains` then `/tp` |
| Skip a day (the simulation checks every 5 seconds) | `/time add 24000` |
| Spawn a smith | `/summon villager ~ ~ ~ {VillagerData:{profession:"minecraft:toolsmith",level:1,type:"minecraft:plains"}}` |
| Spawn a zombie | `/summon zombie ~ ~ ~` |
| Start a raid | `/effect give @s minecraft:bad_omen 600 0`, then walk into a village |
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
| T6 | R0.2, R0.4 | Spawn a toolsmith; `/time add 24000` twice; talk to them | They say there's no metal; `/settlement` shows "Troubles: no metal" |
| T7 | R0.2 | Hold iron ingots, `/settlement donate`, skip a day | Shortage ends; history shows the donation and "Supplies of metal were restored." |
| T8 | R0.3 | Let a zombie kill a villager | History: "<name>, the <job>, was killed by a zombie." |
| T9 | R0.3 | Breed two villagers | The baby shares a parent's family name; history records the birth |
| T10 | R0.3 | Win a raid | History names you among the defenders |
| T11 | R0.1 | Restart the server, check a villager's name and `/settlement history` | Everything is unchanged |
| T12 | R0.2 | `/time add 2400000` (100 days) | The settlement catches up 60 days (the configured cap) without a lag spike |
| T13 | R1.3 | Restart the server three times | `plugins/Hamletfolk/backups/` holds a timestamped copy per restart, never more than 5 |
