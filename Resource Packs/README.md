# Resource pack opportunities for Hamletfolk

This folder collects resource-pack concepts that could complement Hamletfolk. Each opportunity
depends on what Minecraft makes visible to a client: a normal resource pack cannot read Hamletfolk's
server-side persistent data, resident UUIDs, settlement plans or histories.

## Opportunity menu

| Opportunity | What players would see | Hamletfolk inputs | Feasibility |
| --- | --- | --- | --- |
| **Gender and elder appearance** | Distinct adult styles for female and male residents, plus elder variants; babies retain the baby model | Existing gender/life-stage-to-type mapping | Best first prototype. A vanilla pack can redraw the four mapped types today. |
| **Profession and village craft identity** | Tailored aprons, tools, fabric and trade details for farmers, smiths, librarians, guards and other roles | Vanilla profession signal; simulated occupation is available only server-side | Vanilla profession overlays work where the game's profession aligns. To reflect reassigned jobs or Hamletfolk-only roles, the plugin needs a client-visible selector. |
| **Regional dress and materials** | Settlement-wide clothing palettes, layers, patterns and accessories that suggest local craft traditions | Settlement plan's biome set exists; per-resident appearance region is not yet logged | Possible after deciding and exposing a compact region selector. A resource pack cannot read the plan or biome data directly. |
| **Climate-aware clothing** | Heavier coats, hoods or layered garments in cold settlements; lighter clothing in hot ones | The Paper layer can inspect biome/location, but no climate value is part of the appearance contract | Requires plugin-derived climate categories and a client-visible selector. Prefer climate for garment weight and palette; define skin-tone art separately. |
| **Personal traits and family identity** | Subtle individual accessories, family colors or recognizable recurring looks | Stable resident UUID, names and family relationships exist in simulation data | Not addressable by a plain vanilla pack. A compatible client mod could read synchronized data; alternatively the plugin could assign a small visible variant ID, with limited variety. |
| **Life events and status** | Visual cues for a guard on duty, a master craftsperson, a newcomer or a notable elder | Occupation, trade level, settlement membership and histories are known server-side | Mostly needs a new client-visible status signal. Existing profession and type may cover a small subset; pack alone cannot inspect the history or tags. |
| **Settlement identity pack** | A coherent look for villages: resident clothing palette, banners, signs, books and possibly building material palette | Hamletfolk has settlement names, history, plan styles and construction concepts; not all are client-visible | A themed pack can reskin generic assets immediately. Dynamic village-specific assets require a selector or per-settlement customization mechanism. |
| **Player-facing Hamletfolk presentation** | More legible books, menus, icons, signs or visual cues for requests and village status | Books, signs, trades and commands are already part of the player experience | A static pack can improve generic vanilla assets, but cannot know which village state to show unless the plugin uses custom-model/data components or another supported client signal. |
| **Full character customization** | Many skin tones, hairstyles, clothing combinations and individual looks, with consistent zombie/cure behavior | Nearly all resident attributes, including UUID and traits | Best suited to a client mod or a dedicated entity-variant system. Highest art and compatibility cost; not a vanilla resource-pack-only feature. |

## Possible project paths

- **Vanilla-first pack:** implement gender/elder skins and compatible profession art. This has the
  lowest barrier for players and tests the current appearance hook.
- **Hamletfolk-aware regional pack:** add a small plugin appearance profile such as
  `region=taiga`, `climate=cold`, `stage=elder`, then select a constrained set of appearances. This
  requires an explicit client-visible encoding beyond today's tags.
- **Optional enhanced pack plus client mod:** use synchronized resident IDs and appearance data for
  broad per-person variation, while retaining a sensible vanilla-pack fallback. This gives the most
  expressive result but adds a client installation requirement and compatibility work.
- **Worldbuilding companion pack:** focus on books, signs, banners, settlement materials and
  profession details without promising dynamic person-specific selection. It can complement any
  villager appearance pack and can be developed largely independently of the appearance hook.

## Suggested priority

1. Prove the **gender and elder** variant in a normal Java resource pack.
2. Add profession art that composes cleanly with the current type mapping.
3. Prototype **regional dress** after choosing region/climate categories and a client-visible
   selector.
4. Treat per-person traits, family identity and event/status visuals as a separate enhanced path.
5. Consider a companion worldbuilding pack for Hamletfolk's books, signs and settlement style; it is
   valuable even if dynamic skin selection remains limited.

## Related pack plan

The first concrete pack concept is [Villager Uplift](VillagerUplift/README.md), which documents the
current appearance contract, pack requirements and a staged implementation approach.
