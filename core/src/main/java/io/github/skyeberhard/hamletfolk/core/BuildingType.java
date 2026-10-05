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
    SQUARE("Town Square");

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

    /** R2.3: the occupation a building of this kind employs people in, if any. */
    public Optional<Occupation> job() {
        return switch (this) {
            case FARM -> Optional.of(Occupation.FARMER);
            case MINE -> Optional.of(Occupation.MINER);
            case SMITHY -> Optional.of(Occupation.TOOLSMITH);
            case SHOP -> Optional.of(Occupation.MERCHANT);
            case HOUSE, GUARD_POST, TREASURY, SQUARE -> Optional.empty();
        };
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
