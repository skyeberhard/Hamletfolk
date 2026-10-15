# Villager Uplift

Planning notes for a Hamletfolk-compatible villager appearance resource pack. This folder is the
home for the pack and its design notes; it does not contain a finished pack yet.

## Goal

Give tracked Hamletfolk residents a more varied appearance that reflects the attributes the plugin
knows about:

- **Gender:** use the resident's logged gender as the source of truth.
- **Age:** preserve a visible distinction for elders. Children already use Minecraft's baby model.
- **Region and climate:** develop regional clothing and palettes, and consider temperature when
  selecting warm- or cold-weather styles.
- **Occupation:** retain or extend the vanilla profession clothing cues where they suit the design.

The intended first release should work for Java players with a normal resource pack and no client
mod. Per-person customization is a separate, more ambitious path.

## What Hamletfolk exposes today

The core resident record has a stable UUID, gender, age/life stage and simulated occupation. The
Paper plugin writes `hamletfolk:gender`, `hamletfolk:occupation` and `hamletfolk:life_stage` into
the villager's persistent data. Those values are server-side: vanilla clients and resource packs
cannot inspect them.

There is one current client-visible bridge. When `appearance.villager-type: true`, the plugin maps
gender and life stage to the vanilla villager type:

| Resident | Vanilla type | Intended pack role |
| --- | --- | --- |
| Female adult or child | `plains` | Female adult style |
| Male adult or child | `desert` | Male adult style |
| Female elder | `snow` | Female elder style |
| Male elder | `taiga` | Male elder style |
| Other three types | `jungle`, `savanna`, `swamp` | Currently unused by this mapping |

Profession remains a separate visual signal in the vanilla villager rendering, so profession
textures can continue to supply role-specific clothing. See [`docs/APPEARANCE.md`](../../docs/APPEARANCE.md)
and [`Appearance.java`](../../core/src/main/java/io/github/skyeberhard/hamletfolk/core/Appearance.java)
for the current contract and mapping.

## Constraints and design choices

1. **A resource pack cannot read Hamletfolk persistent data.** Gender, stage, occupation tags and
   UUID are useful server-side or to compatible client mods, but do not independently select a
   vanilla pack texture.
2. **The vanilla type hook has only seven values.** The current four-value map represents gender
   and elder status. Adding region, climate, skin-tone families and dress combinations will exceed
   that budget quickly. Any extension needs a deliberate compact mapping and a stable plugin/pack
   contract.
3. **There is no resident region or temperature field today.** Hamletfolk already groups biomes
   into village styles for planning and construction, and Paper can inspect a villager's location
   and biome. It does not currently log a regional appearance attribute on each resident. A future
   design should decide whether a resident inherits the settlement's home style or follows their
   current biome; inheriting the home style is likely to produce more consistent identities.
4. **Changing villager type can affect more than appearance.** The existing appearance notes flag
   possible type-dependent trade behavior for the target Minecraft version. Verify before enabling
   the type mapping on a live world.
5. **Cover all relevant entity forms.** Decide how zombie villagers, cured villagers, children,
   untracked villagers and villagers in excluded worlds should look. Bedrock/Geyser rendering also
   needs a separate playtest; treat this proposal as Java-focused until verified.
6. **Match the exact Minecraft version.** The project currently targets Minecraft 26.2. Texture and
   model resource paths and pack metadata can change between game versions. Inspect assets from the
   matching client version instead of relying on remembered paths.

## Resource pack requirements

- A valid `pack.mcmeta` with the resource-pack format supported by the target Minecraft client.
- A `pack.png` icon and a short description identifying the compatible Hamletfolk mapping/version.
- Overrides at the exact asset paths used by the target client for villager type textures; confirm
  paths and filenames from that version's client assets before authoring. The existing notes point
  to `assets/minecraft/textures/entity/villager/`, but explicitly warn that layout may change.
- At minimum, custom base textures for the four types used by the current gender/age mapping. Keep
  the three unused types at their defaults unless the mapping is deliberately expanded.
- Profession-specific textures only where desired. Check how base type textures and profession
  overlays compose in the target version, and preserve transparent areas and pixel dimensions.
- Review the texture source and distribution permissions before publishing a pack; do not assume
  extracted game assets may be redistributed unchanged.
- A test world/server with the pack applied, plus tracked and untracked villagers and multiple
  professions. Test resource-pack delivery and acceptance separately from the plugin; Hamletfolk
  does not configure `server.properties` for players.

## Proposed approach

### Phase 1: prove the existing hook

1. Extract or inspect the villager assets from the Minecraft 26.2 client and record the actual
   texture/model paths and pack format.
2. Make a small prototype that changes the four mapped type skins in an unmistakable but cohesive
   way. Keep profession overlays intact.
3. Enable `appearance.villager-type: true` on a disposable test server and confirm that female,
   male and elder residents resolve to the documented types and that disabling the setting restores
   their original biome types.
4. Check trades, zombie conversion/cure, child appearance, excluded-world villagers and Java client
   rendering. Record results in the appearance playtest (T26) and update the contract if behavior
   differs from the current assumptions.

### Phase 2: define regional appearance data

1. Select a small set of visual regions/climate bands from the existing biome grouping rather than
   exposing raw biome names. Define fallbacks for modded or unknown biomes.
2. Choose whether region is fixed from the settlement's home biome or recalculated from location;
   prefer a fixed home region for stable identity unless gameplay calls for migration of style.
3. Decide what skin-tone variation means artistically and how it relates to region without treating
   temperature as a proxy for a person's identity. Temperature can guide garment weight, layers and
   palette; the project's design should choose any skin-tone distribution explicitly.
4. Work out the required number of visual combinations. If they cannot fit cleanly into the seven
   vanilla types without losing gender/age distinctions, treat the type hook as a prototype only and
   choose a different client-facing variant mechanism before producing the full art set.

### Phase 3: extend the plugin/pack contract if needed

1. Add a core appearance-profile calculation with unit tests and documented, stable keys.
2. Derive the profile from resident and settlement data; keep Minecraft API details in the Paper
   module. Expose a client-visible selector only after selecting a mechanism that can represent the
   agreed combinations.
3. Keep server-only persistent tags useful for datapacks and other plugins, but do not describe them
   as inputs available to a vanilla resource pack.
4. Document migration, fallback, untracked-villager behavior and setting-off restoration. Avoid
   silently changing existing villagers' vanilla trade behavior.

### Phase 4: art, compatibility and release

1. Create a style guide and texture matrix for gender presentation, age, region/climate and
   profession, with examples before filling every texture.
2. Implement and review the pack at native pixel scale, including babies, profession overlays and
   zombie villagers where supported.
3. Test on the target Java version and representative resource-pack settings. Test Geyser/Bedrock
   separately and state compatibility accurately.
4. Ship the pack with its compatible Hamletfolk version, mapping table, installation instructions
   and known limitations. Keep this contract synchronized with `docs/APPEARANCE.md` and the plugin
   configuration.

## Current recommendation

Start with Phase 1 as a small visual prototype. It demonstrates the existing gender/age hook and
validates the game-version details. Before committing to regional skin tones and clothing, define the
combination matrix and decide whether seven vanilla villager types can represent it adequately. If
not, plan a client mod or another client-visible variant system for that richer version rather than
building a large pack around tags clients cannot read.
