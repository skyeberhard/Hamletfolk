package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;
import java.util.Optional;

/**
 * The kinds of building a player can register with a sign (R2.1). The sign says what the building
 * is; nothing checks it is really one until blocks can be recognised (R2.4).
 */
public enum BuildingType {
    FARM("Farm"),
    SMITHY("Smithy"),
    MINE("Mine"),
    HOUSE("House"),
    GUARD_POST("Guard Post"),
    SHOP("Shop"),
    TREASURY("Treasury"),
    /** R4.7: the village's meeting place, with its bell: the game's own town-centre pieces, built once on the main square. */
    SQUARE("Town Square"),
    /** R8.12: a timber village's signature building: its lumberjacks make a quarter more wood. */
    SAWMILL("Sawmill"),
    /** R8.12: a mining village's: each smith smelts four more ore a day. */
    FORGE("Forge"),
    /** R8.12: a farming or pastoral village's: food spoils half as fast. */
    GRANARY("Granary"),
    /** R5.6: street lights, a work along the plan's streets rather than a building on a lot (no sign, no lot, no template). */
    STREET_LIGHTS("Street Lights"),
    /** R5.6: a fence ring round the village, also a work on the plan rather than a building. */
    PALISADE("Palisade"),
    /** R5.10: the palisade strengthened, tier by tier, into a wall with gates and towers; also a work on the plan. */
    RAMPART("Rampart");

    /** R2.3: how many residents one building gives work to. */
    public static final int WORKERS_PER_BUILDING = 4;

    private final String label;

    BuildingType(String label) {
        this.label = label;
    }

    /** R2.5: a storefront gives work to one merchant. */
    public static final int MERCHANTS_PER_SHOP = 1;

    /** R2.3 and R2.5: how many residents one building of this kind gives work to. */
    public int places() {
        return this == SHOP ? MERCHANTS_PER_SHOP : WORKERS_PER_BUILDING;
    }

    /** R4.19: how many more places each tier above the first adds. */
    public int placesPerTier() {
        return this == SHOP ? 1 : 2;
    }

    /** R2.3: the occupation a building of this kind employs people in, if any. */
    public Optional<Occupation> job() {
        return switch (this) {
            case FARM -> Optional.of(Occupation.FARMER);
            case MINE -> Optional.of(Occupation.MINER);
            case SMITHY -> Optional.of(Occupation.TOOLSMITH);
            case SHOP -> Optional.of(Occupation.MERCHANT);
            case HOUSE, GUARD_POST, TREASURY, SQUARE, STREET_LIGHTS, PALISADE, RAMPART, SAWMILL, FORGE, GRANARY -> Optional.empty();
        };
    }

    /** R5.6: true for a work on the plan (street lights, a palisade): built by the village, never registered with a sign. */
    public boolean isWorks() {
        return this == STREET_LIGHTS || this == PALISADE || this == RAMPART;
    }

    /** e.g. "Guard Post". */
    public String label() {
        return label;
    }

    /** What the sign says, e.g. "[Guard Post]". */
    public String signText() {
        return "[" + label + "]";
    }

    /**
     * The building a line of sign text names, if it is a bracketed building name: "[Farm]", "[farm]",
     * "[ Guard Post ]", "[guardpost]" and "[Guard_Post]" all work. Text without brackets does not, so a
     * sign that merely says "Farm" is left alone.
     */
    public static Optional<BuildingType> fromSign(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String line = text.strip();
        if (line.length() < 3 || line.charAt(0) != '[' || line.charAt(line.length() - 1) != ']') {
            return Optional.empty();
        }
        String wanted = squash(line.substring(1, line.length() - 1));
        for (BuildingType type : values()) {
            if (!type.isWorks() && squash(type.label).equals(wanted)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** The kind a planner directive's target names ("farm", "guard_post", "street_lights"), works included. */
    public static Optional<BuildingType> fromTarget(String target) {
        String wanted = squash(target);
        for (BuildingType type : values()) {
            if (squash(type.label).equals(wanted)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    private static String squash(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]+", "");
    }
}
