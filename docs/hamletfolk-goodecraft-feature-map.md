# Hamletfolk x GoodeCraft: Feature Mapping

Purpose: map the NPC/village-simulation ideas (ChatGPT brainstorm plus MCA / MineColonies / Recruits: Living Villages research) onto Hamletfolk's roadmap (M1-M7), and filter them through what GoodeCraft can actually host.

Status labels: **[verified]** = confirmed by tests, a boot, or repo docs. **[claimed]** = stated in a doc, not independently confirmed. **[unverified]** = my inference or an external claim; check before relying on it.

---

## 1. GoodeCraft constraints (the filter)

| # | Constraint | Source | Consequence for Hamletfolk |
|---|---|---|---|
| C1 | Paper 26.2 build 129, Java 25 | GoodeCraft assessment [verified] | Hamletfolk targets the same version. No version gap. |
| C2 | Zero client mods for base gameplay | GoodeCraft README [verified] | No custom blocks, entities, GUIs or client-side rendering. Vanilla UI only: chat, books, signs, trading, commands. |
| C3 | Java + Bedrock crossplay (Geyser 2.11.3 / Floodgate 2.2.5) | GoodeCraft assessment [verified] | Every player-facing interaction needs a Bedrock path. Hamletfolk's Bedrock behavior is untested. |
| C4 | Solo admin, single production Paper process | Assessment [verified] | Anything that adds ops burden (extra services, unstable deps) is a cost. Admin tooling is a feature, not a nicety. |
| C5 | Pinned build pipeline (`build-hamletfolk.sh`, commit `98d2f0b`) | Assessment [verified] | Releases must be pinnable and reproducible. Data-format changes need migration or versioning. |
| C6 | Many worlds, including void-generated custom dimensions (ISD datapack) | Assessment [verified] | The simulation must not treat every world as a village world. World allow/deny is required. |
| C7 | Shared hardware (Xeon E3-1230, 32 GB) | Assessment [claimed] | Performance budget matters. Hamletfolk's 0.5 ms benchmark covers the pure-Java core only, not Paper-layer entity mapping. |
| C8 | No cloud AI dependency for core gameplay | Hamletfolk design constraint | Template dialogue stays the default. AI stays optional (M7). |

---

## 2. Baseline: what exists today

| Capability | Status |
|---|---|
| Persistent villager identity (name, family, traits, needs, occupation) | Unit-tested (R0.1) [verified] |
| Settlement ledger and daily simulation (production, shortage, famine) | Unit-tested (R0.2) [verified] |
| Dialogue from simulation state | Unit-tested for smith shortage and recent death (R0.4) [verified] |
| Zombie cure preserves identity | Unit-tested (R1.2, identity half) [verified] |
| Save backups (keep last 5) | Unit-tested (R1.3) [verified] |
| Core simulation cost, 50 settlements x 50 residents | 0.528 ms median vs 5 ms budget, core only (R1.6) [verified] |
| Births, deaths, raids, donations written to history | Code exists, no test (R0.3) [claimed] |
| Cure written to history | Code exists, no test (R1.2, history half) [claimed] |
| Paper-layer behavior in a live game (dialogue, trading, donation, history book) | Not playtested on Java or Bedrock [claimed by repo docs] |
| Live in GoodeCraft production since 2026-09-27 | [verified per GoodeCraft docs] |

---

## 3. Feature map

Verdict key: **Adopt** (build it, in the milestone shown), **Adapt** (take the idea, change the design for GoodeCraft), **Defer** (wait for a prerequisite), **Drop** (conflicts with constraints).

| Idea (source) | Hamletfolk item | GoodeCraft / Bedrock fit | Verdict |
|---|---|---|---|
| Persistent identity, personality, needs (ChatGPT s.1, MCA) | Shipped (M0) | Text-only, fully C2/C3 compatible | Adopt (done) |
| Per-NPC skills, ambition, personal goals (ChatGPT s.2) | Traits exist; goals not in roadmap | Data only, no client impact | Defer to M6. Goals only matter once households and businesses exist. |
| Settlement resources and treasury (ChatGPT s.3) | Ledger shipped | Data only | Adopt (done). Add fuel, weapons and luxury goods only when M3/M5 need them. |
| Supply/demand trade prices | M3 | Uses vanilla trading UI, so Java and Bedrock both see it natively. Best-fit feature for C2/C3. | Adopt (M3) |
| Village requests / bounties, MCA:Quests-style (research) | M3 | Delivery via commands or written book. Bedrock book display and command entry need a test. | Adapt (M3). Steal the design, not the mod. |
| Player reputation | M3 | Data plus chat feedback | Adopt (M3) |
| Building recognition without schematics (ChatGPT "killer feature") | M2 | Block scanning is server-side and works on both editions. Event-driven "dirty region" scan is right; do not scan every tick. | Adopt (M2) |
| Sign-registered buildings (`[Farm]`, `[Smithy]`) | M2 | Bedrock has sign editing, but Geyser behavior with edited signs is unverified. Provide a `/settlement register` command as a fallback. | Adapt (M2) |
| Housing capacity from beds | M2 | Vanilla blocks only | Adopt (M2) |
| Growth: newcomers on surplus, emigration on no work | M4 | Data plus villager spawn/despawn. Watch entity count. | Adopt (M4) |
| Guards and defense, walls and lighting reduce raid damage | M5 | Guards as vanilla villagers or golems would work. Custom guard entities would need client support. Interaction with GoodeCraft's dragon/rift mobs is unknown. | Adapt (M5). Test against custom mobs first. |
| History log with NPC dialogue referencing past events | Log shipped; dialogue partial (recent death) | Text only. Cheap, high value, low risk. | Adopt now. Extending dialogue to famine, raid and milestone events is the best early win. |
| Three-tier simulation (background / active / physical) | Background shipped with 60-day catch-up | Physical tier is the vanilla villager entity. Cost lives in the Paper layer, which is unbenchmarked (C7). | Adapt. Add a Paper-layer performance measurement before scaling population. |
| Abstract population beyond loaded entities (2,800 residents, 40 entities) | Not in roadmap | Conflicts with per-resident identity; large population is a data-size and save-time risk | Defer until tiers (M-later) and after Paper-layer benchmarks |
| Households, inheritance, resident-owned businesses, wages | M6 | Data plus chat/command UI | Defer until M3-M5 are stable |
| Elder / mayor / player governance | M6 | Command UI. Bedrock parity needed. | Defer (M6) |
| Player as investor / employer | M6 | Same | Defer (M6) |
| Roads from foot traffic | Unscheduled | Block edits in player-visible areas; risk of griefing complaints and lag | Defer, post-v1 |
| Settlement tiers, districts, wealth classes | Unscheduled | Pure data first; visuals depend on M2 | Defer, post-v1 |
| Cultures / diversity | Unscheduled | Diversity as behavior and naming works everywhere. Diversity as skins needs a resource pack, and Bedrock conversion via Geyser is limited [unverified]. | Adapt: behavioral only. Drop skin-based diversity. |
| Crime, factions, disasters | Unscheduled | Data only, but large design surface | Defer, post-v1 |
| Optional AI dialogue | M7 | Owen already runs Claude-based bots (Discord, Herobrine), so a precedent exists. Cost and latency still argue for template default and opt-in. | Defer (M7). Template fallback stays mandatory (C8). |
| Forge-only features (MineColonies research, MCA marriage UI, Recruits armies) | n/a | Requires client mods, so violates C2 and C3 | Drop the mods; adapt the ideas into M5/M6 |

---

## 4. Proposed additions for GoodeCraft (not in Hamletfolk's roadmap)

These come from GoodeCraft's constraints, not from the brainstorm. IDs are suggestions.

| Proposed ID | Requirement | Why |
|---|---|---|
| G1 | World allow/deny list in config (e.g. exclude ISD void dimensions and the ship/Titanic worlds) | C6. Otherwise simulation runs in worlds with pasted structures and no real villages. |
| G2 | Bedrock (Geyser) playtest of dialogue, trading, donation, history book, and any sign or command UI | C3. Sneak + right-click may be awkward on Bedrock touch and console controls [unverified]. Provide a non-sneak interaction path (command or plain right-click). |
| G3 | Paper-layer performance measurement (tick cost with real villagers loaded), reported by a `/settlement perf`-style admin command | C7. R1.6 only covers the core. |
| G4 | `settlements.json` schema version plus migration on load; confirm the file is included in GoodeCraft's NAS backup path | C5. Pinned upgrades must not corrupt saves. Plugin-local `backups/` is not off-machine backup. |
| G5 | `api-version` in `plugin.yml` checked against the 26.2 target | Stale value (`1.21`) found during verification. The correct 26.x string is unverified; confirm against Paper docs before changing. |
| G6 | Admin gating and abandoned-settlement handling done before further features | C4. Already planned as R1.4 and R1.5. Raise their priority, since production is live. |
| G7 | Pin-and-verify workflow: which commit is live vs which commit was tested | C5. The verification run used HEAD; production pins `98d2f0b`. |

G1-G7 are tracked in [ROADMAP.md](../ROADMAP.md) as R1.10-R1.16.

---

## 5. Recommended sequence

**Gate 0: verify before building (this week)**
1. Compare your tested commit against the pinned production commit (`98d2f0b`). Re-run the checks on the pinned one if they differ.
2. Playtest TESTING.md T2-T15 on the dev tunnel server (`127.0.0.1:25566`), starting with T8-T10 (births, deaths, raids, donations) and T14 (cure history). This closes R0.3, R1.2 and R1.1.
3. Bedrock pass (G2) if the dev server has Geyser and anyone in the group plays Bedrock.
4. Fix `api-version` (G5) after confirming the right value.

**Milestone 1 (finish M1 with GoodeCraft additions)**
R1.4 admin commands, R1.5 abandonment, R1.8 relocation, G1 world list, G3 perf measure, G4 schema version.

**Quick win in parallel:** extend dialogue to reference famine, raid and milestone history. It costs little, needs no new systems, and makes the sim visible to players.

**Then M2 -> M3 -> M4 -> M5**, in that order. M3 (economy on vanilla trading) is the feature most compatible with C2/C3 and most visible to players. M5 (defense) needs testing against GoodeCraft's custom mobs first.

**Post-v1:** M6 society, roads, tiers, M7 AI dialogue.

---

## 6. Design rules to keep the map honest

- No feature ships that needs a client mod, custom resource pack for gameplay, or non-vanilla UI.
- Every player-facing interaction lists its Java path and Bedrock path.
- Every roadmap item's "done when" is backed by a unit test or a TESTING.md scenario before it is marked Done. R0.3 and R1.2 currently are not.
- The simulation core stays deterministic and free of Minecraft dependencies. New Paper-layer logic should move into core where it can be tested.

---

## 7. Verify yourself

- Whether the live server runs the pinned commit and whether your test matches it.
- Bedrock behavior for sneak interaction, signs, written books and trading UI under Geyser.
- The correct `api-version` for a 26.x Paper plugin.
- Claims about Recruits: Living Villages, Dynamic Villagers, Nations & Villagers and MCA:Quests capabilities. They came from an AI summary and were not independently checked here.
- Whether GoodeCraft's dragon, rift or custom-mob systems interfere with villager raids and defense.
- Paper-layer tick cost with a realistic number of loaded villagers.
- The M1-M7 roadmap text lives in the Hamletfolk repo; GoodeCraft's docs only carry one roadmap sentence. This map assumes the roadmap in your brief is current.
