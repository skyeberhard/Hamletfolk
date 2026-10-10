# Playtest checklist (R1.1)

An ordered run through `docs/TESTING.md`, city rules first. The exact steps and expected results
are in TESTING.md under the same T-number; this page only orders them and gives you somewhere
to record the outcome. Commands are in [COMMANDS.md](COMMANDS.md).

**Mode:** `C` = can be driven from the server console; `P` = needs a person in game.
Fill in **Result** as `pass`, `fail` (say what happened) or `skip`. Anything not yet played
stays out of PRODUCTION.md. The C/P marks are my reading of each scenario's steps; where a step
turns out to need a player, just do it in game.

## 0. Setup (once)

- [ ] `git pull`, then `./gradlew runServer` (accept the EULA, `op <name>`)
- [ ] Fresh world: delete `paper/run/world*` and `paper/run/plugins/Hamletfolk/`
- [ ] Note the commit (`git log -1 --format=%h`): ______

## 1. Smoke: does it run at all

| T | Goal | Mode | Result |
|---|---|---|---|
| T1 | Starts clean, `Tracking 0 settlements.`, no errors | C | |
| T17 | `/settlement admin list`, `inspect`, `rename`, `save` | C | |
| T2 | Villager shows a name on hover | P | |
| T3 | `/settlement` shows the village summary | P | |
| T4, T5 | Talking works; the greeting warms up after six talks | P | |
| T6, T6b | A smith with no metal says so (after the famine line, once food is donated) | P | |
| T11 | Names and history survive a restart | C | |
| T13 | Three restarts in a row without losing anything | C | |

## 2. Founding, plan and land (the city rules)

| T | Goal | Mode | Result |
|---|---|---|---|
| T46 | `/settlement survey` in each biome | P | |
| T51 | `/settlement admin found Testford 8` makes a village | P | |
| T45 | Planner explains what it wants and why | P | |
| T47 | `plan` and `lots`: streets and lots outlined | P | |
| T65 | Land shapes the village's character | P | |
| T74 | A big village grows to later plan stages | P | |
| T33 | Beds counted as housing | P | |
| T32 | Building signs register and unregister | P | |

## 3. Economy

| T | Goal | Mode | Result |
|---|---|---|---|
| T7, T23, T28, T31 | Donating: values, amounts, room | P | |
| T36, T37 | Trades move the stores; stock limits hold | P | |
| T30 | Trade prices follow need | P | |
| T34 | A mine makes miners; metal for smiths; tool wear | P | |
| T20, T21 | Jobs follow need (lumberjack); children grow into jobs | P | |
| T41 | Shop and merchant | P | |
| T42, T43 | Treasury fills; bank-counter distance | P | |
| T27, T29 | Requests and tool requests pay out | P | |
| T35, T38 | Reputation and wealth | P | |
| T61 | Commodities: grain, logs, coal, iron tools | P | |
| T66, T70, T71, T72 | Signature buildings and land trades | P | |

## 4. Construction

| T | Goal | Mode | Result |
|---|---|---|---|
| T49 | `admin build` places a template | P | |
| T50 | A village builds a house from its plan | P | |
| T52 | A builder works, and building creates work | P | |
| T54 | Ground is graded: holes, bumps, streams | P | |
| T76 | Street grading on slopes | P | |
| T53, T59 | Unattended building, `warp`, `fastforward` | C | |
| T69 | Gatehouse and tower captures | P | |

## 5. Defense

| T | Goal | Mode | Result |
|---|---|---|---|
| T8, T10 | Deaths and a won raid land in the history | P | |
| T44 | Attacks call up guards | P | |
| T55 | Defence spending after an attack | P | |
| T57 | Lights and fence after an attack | P | |
| T67 | Rampart | P | |
| T63 | Golems | P | |
| T14, T15 | Zombie infection and cure | P | |

## 6. Population and the world around

| T | Goal | Mode | Result |
|---|---|---|---|
| T9, T22, T60 | Births need bread and beds | P | |
| T12, T25, T24 | 100 days pass; old age; old saves migrate | C | |
| T39, T40 | Migration between villages; membership of a split village | P | |
| T58 | `admin job` and `unstick` | P | |
| T62, T64 | Trade levels; marks (felled trees, quarry) | P | |
| T18 | Worlds allow/deny | C | |
| T48 | `[Exempt]` signs | P | |

## 7. Saving and safety

| T | Goal | Mode | Result |
|---|---|---|---|
| T16 | Hard kill right after a donation loses nothing | P | |
| T19 | Backups rotate | C | |
| T26 | Appearance data on villagers | P | |

## 8. Villagers (after the city rules pass)

| T | Goal | Mode | Result |
|---|---|---|---|
| T68, T73, T75 | Voices: different tones, trade talk, land and mood talk | P | |
| T79 | Brain module attaches and detaches | P | |
| T80 | `brain inspect` and the decision log | P | |
| T81 | Behaviour switches and budget trip | P | |

## Also report

- `/settlement admin list` output near the split village (R1.27)
- Any console error or stack trace
- Server tick time during the run, if you can see it (R1.12)
