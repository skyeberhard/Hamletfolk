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
    RAMPART("Rampart"),
    /** R5.11: a gatehouse of the rampart: only ever an admin's captured template, never registered or a project of its own. */
    GATEHOUSE("Gatehouse"),
    /** R5.11: a watch tower of the rampart: likewise only a captured template. */
    TOWER("Tower"),
    /** R8.13: where a fisherman works (a barrel), by the water; a fishing village's. */
    HARBOUR("Harbour"),
    /** R8.13: pens for sheep, with the loom a shepherd works at. */
    PENS("Pens"),
    /** R8.13: the butcher's smoker. */
    SMOKEHOUSE("Smokehouse"),
    /** R8.13: the leatherworker's cauldron and racks. */
    TANNERY("Tannery"),
    /** R8.13: stalls for horses, where a horse trainer works. */
    STABLE("Stable"),
    /** R8.13: hives, where a beekeeper works. */
    APIARY("Apiary"),
    /** R8.13: the cartographer's table. */
    MAP_ROOM("Map Room"),
    /** R8.13: shelves and a lectern, where a librarian works; a town's. */
    LIBRARY("Library"),
    /** R8.13: furnaces and glass, where a glassblower works. */
    GLASSWORKS("Glassworks"),
    /** R8.13: a market stall house: each of its merchants makes one more sale a day. */
    TRADING_POST("Trading Post");

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
            case HARBOUR -> Optional.of(Occupation.FISHERMAN);
            case PENS -> Optional.of(Occupation.SHEPHERD);
            case SMOKEHOUSE -> Optional.of(Occupation.BUTCHER);
            case TANNERY -> Optional.of(Occupation.LEATHERWORKER);
            case STABLE -> Optional.of(Occupation.HORSE_TRAINER);
            case APIARY -> Optional.of(Occupation.BEEKEEPER);
            case MAP_ROOM -> Optional.of(Occupation.CARTOGRAPHER);
            case LIBRARY -> Optional.of(Occupation.LIBRARIAN);
            case GLASSWORKS -> Optional.of(Occupation.GLASSBLOWER);
            case HOUSE, GUARD_POST, TREASURY, SQUARE, STREET_LIGHTS, PALISADE, RAMPART, SAWMILL, FORGE, GRANARY, GATEHOUSE, TOWER,
                    TRADING_POST -> Optional.empty();
        };
    }

    /** R5.6: true for a work on the plan (street lights, a palisade): built by the village, never registered with a sign. */
    public boolean isWorks() {
        return this == STREET_LIGHTS || this == PALISADE || this == RAMPART;
    }

    /** R5.11: true for a part of the rampart that exists only as a captured template (its tier is the rampart's, 2 or 3). */
    public boolean isPart() {
        return this == GATEHOUSE || this == TOWER;
    }

    /** The type an admin's {@code capture} or {@code build} names: a building with a sign, or a part of the rampart. */
    public static Optional<BuildingType> fromCapture(String name) {
        Optional<BuildingType> building = fromSign("[" + name + "]");
        if (building.isPresent()) {
            return building;
        }
        String wanted = squash(name);
        for (BuildingType type : values()) {
            if (type.isPart() && squash(type.label).equals(wanted)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
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
            if (!type.isWorks() && !type.isPart() && squash(type.label).equals(wanted)) {
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
