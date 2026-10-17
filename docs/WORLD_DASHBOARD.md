# The world dashboard: design

Status: design only. Nothing here is built except what [DASHBOARD.md](DASHBOARD.md) already describes. Build order is at the end.

The dashboard has three levels, and a person drills down through them:

```
side tray (worlds, with lights)  ->  a world's page  ->  a village's page (the tabs we have, plus three)
```

## 1. The side tray

One entry per world, each with a light, in the same three colours as a village (green going well, amber watch, red going badly), plus grey.

- **The light** (built as R1.44, `WorldHealth` in core). A world is as healthy as its villages, weighted by the people who live in them. A village's own light
  is amber for something that holds it back, which is nearly always true of some village, so amber villages only count when they are half the world:
  - green: no village is in trouble (red), and under half of its people live in villages that are held back;
  - amber: some village is in trouble, or half or more of its people live in villages that are held back;
  - red: half or more of its people live in villages in trouble, or every village is abandoned, or nobody lives there. One bad village in a big world is
    amber at worst. The light is meant to mean something, so it never goes red because of a single village.
  - grey: the world is not simulated (excluded by `worlds.deny`, or not loaded). It still appears, so the list is complete.
- Beside the light: the count ("14 villages: 9 green, 4 amber, 1 red"), the players online, and the world's time speed if it is not 1.
- The tray also holds "All worlds" (today's overview) and, later, the entries other plugins add (R1.38).

## 2. A world's page

Tabs, left to right:

| Tab | Shows | Can do (with `allow-actions`) |
|---|---|---|
| Overview | The world's attributes: name, environment, day, time of day, weather, difficulty, how many villages and people, the light and why it is that colour | |
| Players | Who is online in it, where, and which village (if any) each stands inside | |
| Villages | The village table for this world: today's village cards, health, what each is building and what is in its way | Open a village |
| Portals | The links between this world and others (see section 3) | Open, close or repair a link, if the portal plugin lets us |
| Time | The world's speed (section 4), the warp and fast-forward settings for its villages | Set the speed, run all its villages ahead, set their fast-forward, with the existing limits and confirmations |
| Quests | The quests that span this world's villages (section 5) | Offer, cancel or finish one |
| Feed | The history feed, filtered to this world | |

Everything under "can do" goes through the checked-action list in core (`AdminAction`) with the same limits, confirmation, audit log and login
as today. Nothing new may bypass that. Today's `warp-all` and `fastforward-all` with a `world` are the first of these (R1.43).

## 3. Portals

The portal system (goodecraft's, by Owen) is a separate plugin. The world page does not depend on it. It is a panel the portal plugin supplies through
the extension API of R1.38 (a name, the data to show, and its actions with their parameters, through Bukkit's services manager), drawn on the world tab
as "Portals" and applying the same login, roles, confirmation, main-thread running and audit log.

What Hamletfolk needs from it, for its own uses:

- **The links** between worlds: from where to where, open or closed or broken, and which direction. This is how the world page, and later trade
  (R6.5) and migration, know which worlds connect.
- **Events** when a link opens or closes, so the light and the feed can mention it.
- **Who owns a link**: the portal plugin keeps them; we only read, and ask it to act through its actions.

Open questions, to settle with a look at the portal plugin's code rather than guesses: where it stores links; whether it publishes events; whether it
already has an admin surface we would duplicate; how it names worlds.

R1.46 built the saved *run-faster* setting: each world has a speed (fast-forward for all its villages, saved in `world-settings.json`) and a default for
warp days, and a village's own fast-forward wins. It is a setting for the admin's convenience. R1.47's world speed, the lived-time ratio with its age and
trade rules, is a separate and later thing, and may reuse the same file.

## 4. World speed (time dilation)

What it means: **the villages of a world live faster or slower than the hub's.** A world with speed 2 has two village days for each day of the hub.

What it does not mean: the Minecraft world's tick rate, which cannot be changed per world. Crops, mobs and redstone run at the server's speed. The
sky can follow the speed (advance or hold the daylight clock), and that is cosmetic and optional (`world.sky-follows-speed`).

How: it is a per-world multiplier on the clock each village already keeps (`clockAhead`, fast-forward in `SettlementService`). A village's simulated
day count advances `speed` days for each world day. The multiplier is **saved** (today's fast-forward is in memory and stops on restart) and is shown in
the tray when it is not 1. The existing per-village fast-forward multiplies it.

Rules to write down and test before building it (the reason this comes after the world page):

- **Age across worlds.** A resident keeps their age *in days of the village they are in*. A person who moves between worlds of different speed has their
  birth day shifted (the existing `Resident.shiftBirth`, which already handles a village clock ahead) so they do not jump in age.
- **Goods and money** crossing a portal are counted in units, which do not depend on time. Trade between worlds (R6.5) prices a unit the same on both sides;
  speed changes how fast a village makes and eats, not what a unit is worth.
- **Exploits.** A fast world compounds production and reputation. Reputation is per village and gained by gifts and requests, which already scale with the
  village's own days, so a fast world does not make a player's standing grow faster per real hour *for a given gift*, but its villages produce and ask for
  more. The admin sets speeds, so this is a tuning question, not a hole. Cap: 0.25 to 8, to start.
- **Cost.** Running a village faster costs server time in proportion. A fast world is the same cost as fast-forwarding all its villages, so the same
  stall limits and warnings apply.
- **Unloaded or empty worlds** do not tick; their villages catch up when someone arrives, as they do now, up to the existing catch-up limit.

## 5. Quests

Two layers, built in this order.

### 5.1 Requests that span villages

A village already has requests (R3.3): what it asks for, what it pays, who filled it. A **world request** is the same thing asked of more than one village:
"the north needs iron", "the road between A and B is cut". It is derived from the ledgers, the threat and incident records and the plan, never invented: a
village short of iron while another has a surplus produces the request. It appears on the notice boards of the villages it concerns (R4.53), in the world's
Quests tab, and pays from the villages' treasuries. It needs trade (R6.5), and is designed there.

### 5.2 Story quests

A story quest is a small state machine saved with the world: **setup, clues, resolution, expiry**.

Example: *Farmer Bill's pig is missing.*

1. **Setup.** The quest director picks a village and a resident (a farmer with pens, here) and the notice board and that resident's talk mention the
   pig. This costs nothing and can be ignored.
2. **Cause, chosen from what is true.** The director looks at the world: a witch hut within range, a record of attacks on other villages in the last
   weeks, a dragon alive in the End, a pillager outpost. It picks a cause the world can back, and falls back to an ordinary one (the pig wandered off and
   is found in a field) if there is none. A cause is never invented out of nothing: "the witch took it" is only chosen where a witch exists.
3. **Clues** arrive through things already in the game: what villagers say (dialogue situations), lines in the village's history, items left at a spot,
   other villages' notice boards. Each clue points at something that exists.
4. **Resolution** is the player doing something that changes the world: dealing with the witch, finding the pig, sending guards. The village remembers it
   in its history and in what its people say; a reward is paid from the treasury like a request.
5. **Expiry.** Every quest ends: after N days unresolved, the cause is told ("the witch had taken it") and the quest closes, so nothing is left hanging.

What it needs:

- A saved quest record (a save-format bump with a migration), holding its kind, village, cast (resident ids), cause, clues given, state and day.
- A **director** that limits quests: at most one active story quest per village and a world-wide cap, chosen by how long since the last, and only where a
  player is near enough to see it (like the notice board and stores).
- **Never unwinnable.** A quest is only offered when its cause can be reached, and an admin can cancel any from the world's Quests tab.
- Authored **templates** (setup lines, clue lines, causes, rewards) as text files like the dialogue lines (`core/src/main/resources/quests/`), so they can
  be added without code.

Out of scope until later: quests with their own NPCs outside villages, branching stories, quests that change the plan of a village.

## 6. The village page

Everything it has today (stores, work, requests, wants, plan, residents, buildings, projects, decisions, history, feed), plus three tabs:

| Tab | Shows | Notes |
|---|---|---|
| Treasury | The emerald balance, the limit, what it was spent on and what came in (donations, merchants, requests), and, when R5.4 lands, break-ins | Read-only first. The numbers exist already (`Ledger.treasury`, `Flow`) |
| Stores | The ledger by commodity with the barrels' state (R4.55): what is shown, what was taken, who | Read-only first |
| Quests | The village's requests (R3.3) with who filled them, its world requests, and story quests in it | Admin can cancel |

Who may edit the treasury and stores belongs with stewardship ([STEWARDSHIP.md](STEWARDSHIP.md)): an admin sees and acts on everything; a steward
sees the village's own tabs only. These three tabs are built read-only, and edit rights are decided with stewardship.

## 7. Before it leaves the machine

R1.37 (login) comes first. The world page adds powerful controls (speed, warp, portals). The world tab's extension slots (R1.38) must be settled before
the portal plugin's panels are built against them.

## 8. Build order

1. **R1.44 The world tray and the world page, read-only**: lights, attributes, players, the village table, the feed. No new actions.
2. **R1.45 Village treasury, stores and quests tabs, read-only.**
3. **R1.46 Per-world controls**: the existing warp and fast-forward become a saved per-world setting; the Time tab.
4. **R1.38 Panels from other plugins**, with the Portals tab as its first user. Needs the portal plugin's data (section 3).
5. **World speed (R1.47)**, after its rules (section 4) are tested in core.
6. **World requests (with R6.5), then story quests**, in M6.
