package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;

/**
 * What a resident does for a living. Mirrors vanilla villager professions so the
 * Minecraft layer can map a villager's profession key straight onto it.
 */
public enum Occupation {
    // Foraging (R1.24): an unemployed adult gathers about 1 food a day. Intended: the floor under a small
    // village, documented in the README and DESIGN.md, and acknowledged in dialogue.
    UNEMPLOYED("none", "unemployed", ResourceType.FOOD, 1, null),
    NITWIT("nitwit", "idler", null, 0, null),
    FARMER("farmer", "farmer", ResourceType.FOOD, 4, null),
    FISHERMAN("fisherman", "fisher", ResourceType.FOOD, 3, null),
    BUTCHER("butcher", "butcher", ResourceType.FOOD, 2, null),
    SHEPHERD("shepherd", "shepherd", ResourceType.GOODS, 1, null),
    LEATHERWORKER("leatherworker", "leatherworker", ResourceType.GOODS, 1, null),
    FLETCHER("fletcher", "fletcher", ResourceType.GOODS, 1, ResourceType.WOOD),
    MASON("mason", "mason", ResourceType.STONE, 2, null),
    // No vanilla profession backs these; they're assigned directly by the simulation
    // (R4.3 "jobs follow need") rather than reached through fromVanillaKey.
    LUMBERJACK("lumberjack", "lumberjack", ResourceType.WOOD, 3, null),
    // R2.3: works a registered mine: mostly stone, and some metal, which is what lets smiths work without donations.
    MINER("miner", "miner", ResourceType.STONE, 3, null, ResourceType.METAL, 1),
    // R3.9: makes nothing; sells surplus into the treasury (see SettlementSimulator.sell).
    MERCHANT("merchant", "merchant", null, 0, null),
    ARMORER("armorer", "armorer", ResourceType.TOOLS, 1, ResourceType.METAL),
    WEAPONSMITH("weaponsmith", "weaponsmith", ResourceType.TOOLS, 1, ResourceType.METAL),
    TOOLSMITH("toolsmith", "toolsmith", ResourceType.TOOLS, 1, ResourceType.METAL),
    CARTOGRAPHER("cartographer", "cartographer", ResourceType.GOODS, 1, null),
    CLERIC("cleric", "cleric", ResourceType.GOODS, 1, null),
    LIBRARIAN("librarian", "librarian", ResourceType.GOODS, 1, null);

    private final String vanillaKey;
    private final String title;
    private final ResourceType produces;
    private final int baseOutput;
    private final ResourceType consumes;
    private final ResourceType secondaryProduces;
    private final int secondaryBaseOutput;

    Occupation(String vanillaKey, String title, ResourceType produces, int baseOutput, ResourceType consumes) {
        this(vanillaKey, title, produces, baseOutput, consumes, null, 0);
    }

    Occupation(String vanillaKey, String title, ResourceType produces, int baseOutput, ResourceType consumes,
               ResourceType secondaryProduces, int secondaryBaseOutput) {
        this.vanillaKey = vanillaKey;
        this.title = title;
        this.produces = produces;
        this.baseOutput = baseOutput;
        this.consumes = consumes;
        this.secondaryProduces = secondaryProduces;
        this.secondaryBaseOutput = secondaryBaseOutput;
    }

    /** Maps a vanilla profession key (e.g. {@code "farmer"}) to an occupation. Unknown keys become UNEMPLOYED. */
    public static Occupation fromVanillaKey(String key) {
        if (key == null) {
            return UNEMPLOYED;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        if (colon >= 0) {
            normalized = normalized.substring(colon + 1);
        }
        for (Occupation occupation : values()) {
            if (occupation.vanillaKey.equals(normalized)) {
                return occupation;
            }
        }
        return UNEMPLOYED;
    }

    /** True for occupations no vanilla profession backs, so the simulation alone hands them out (R4.3). */
    public boolean simOwned() {
        return this == LUMBERJACK || this == MERCHANT || this == MINER;
    }

    public String title() {
        return title;
    }

    /** The resource this occupation adds to the ledger each day, or null if it produces nothing. */
    public ResourceType produces() {
        return produces;
    }

    /** Gatherers wear out tools as they work (R3.6). */
    public boolean usesTools() {
        return this == FARMER || this == FISHERMAN || this == LUMBERJACK || this == MASON || this == MINER;
    }

    /** A second resource this occupation adds each day (a miner gets metal as well as stone), or null. */
    public ResourceType secondaryProduces() {
        return secondaryProduces;
    }

    public int secondaryBaseOutput() {
        return secondaryBaseOutput;
    }

    public int baseOutput() {
        return baseOutput;
    }

    /** The input this occupation needs one unit of per day to work, or null if it needs none. */
    public ResourceType consumes() {
        return consumes;
    }
}
