package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * R2.8: finds the buildings a village already stands on, from what the Minecraft layer read (a bed or a job site is a point of
 * interest the game knows by name, farmland is read from the ground), with no sign on any of them. A house for each group of beds,
 * a smithy or other work building for each group of job sites, a farm for each field. Pure: the Paper layer feeds it
 * {@link Site}s and {@link Cell}s and {@link Settlement#syncFound} registers what it returns.
 */
public final class BuildingFinder {
    /** The {@code registeredBy} of a building found rather than signed or built: it has no sign to look for. */
    public static final String FOUND_BY = "found";

    /** Beds this close to a house's first bed (blocks, either way) are in that house. */
    static final int HOUSE_DISTANCE = 6;
    /** A work building takes in job sites this close to its first one. */
    static final int GROUP_DISTANCE = 10;
    /** A building of the same kind this close to an older one is that same building. */
    public static final int SAME_BUILDING = 12;
    /** Farmland within this many blocks of a field's first block is that field (a field has water in it). */
    static final int FIELD_SPAN = 8;
    /** Fewest farmland blocks that make a farm. */
    static final int MIN_FIELD = 6;
    /** The blocks round a found building that a lot may not overlap. */
    public static final int FOOTPRINT_RADIUS = 5;

    /** A point of interest the game knows by name: {@code kind} is {@code home} for a bed, else a villager job ({@code toolsmith}). */
    public record Site(String kind, int x, int y, int z) {
    }

    /** A block of farmland. */
    public record Cell(int x, int y, int z) {
    }

    /** A building found: its kind, a block that stays put while it stands, and the ground it covers. */
    public record Found(BuildingType type, int x, int y, int z, Rect footprint) {
        public String key() {
            return Building.key(x, y, z);
        }
    }

    private static final Map<String, BuildingType> WORK = Map.ofEntries(
            Map.entry("toolsmith", BuildingType.SMITHY),
            Map.entry("armorer", BuildingType.ARMOURY),
            Map.entry("weaponsmith", BuildingType.ARMOURY),
            Map.entry("mason", BuildingType.MASONS_YARD),
            Map.entry("librarian", BuildingType.LIBRARY),
            Map.entry("fletcher", BuildingType.BOWYER),
            Map.entry("cartographer", BuildingType.MAP_ROOM),
            Map.entry("cleric", BuildingType.CHAPEL),
            Map.entry("leatherworker", BuildingType.TANNERY),
            Map.entry("butcher", BuildingType.SMOKEHOUSE),
            Map.entry("shepherd", BuildingType.PENS));
    // (A farmer's composter and a fisherman's barrel are not read: farms are found by their fields, and a barrel is as often storage.)

    private BuildingFinder() {
    }

    /** Village days between looks at what stands in a village. */
    public static final int EVERY_DAYS = 10;
    private static final String SCANNED = "foundScanned";

    /** Whether the village is due another look at what stands (never looked, or {@link #EVERY_DAYS} days ago). */
    public static boolean due(Settlement settlement, long day) {
        Long last = settlement.conditions().get(SCANNED);
        return last == null || day - last >= EVERY_DAYS;
    }

    /** Notes that the village was looked at today. */
    public static void scanned(Settlement settlement, long day) {
        settlement.conditions().put(SCANNED, day);
    }

    /** The kind of building a job site belongs in, if it stands for one. */
    public static java.util.Optional<BuildingType> workBuilding(String kind) {
        return java.util.Optional.ofNullable(WORK.get(kind.toLowerCase(Locale.ROOT)));
    }

    /** The point-of-interest kinds {@link #find} reads besides beds ({@code home}). */
    public static java.util.Set<String> workKinds() {
        return WORK.keySet();
    }

    /** Every building the sites and farmland stand for, in a repeatable order. */
    public static List<Found> find(List<Site> sites, List<Cell> farmland) {
        List<Found> out = new ArrayList<>();
        List<Site> beds = new ArrayList<>();
        Map<BuildingType, List<Site>> work = new java.util.EnumMap<>(BuildingType.class);
        for (Site site : sites) {
            if (site.kind().equalsIgnoreCase("home")) {
                beds.add(site);
            } else {
                workBuilding(site.kind()).ifPresent(type -> work.computeIfAbsent(type, t -> new ArrayList<>()).add(site));
            }
        }
        for (List<Site> group : groups(beds, HOUSE_DISTANCE)) {
            out.add(foundAt(BuildingType.HOUSE, group));
        }
        for (Map.Entry<BuildingType, List<Site>> entry : work.entrySet()) {
            for (List<Site> group : groups(entry.getValue(), GROUP_DISTANCE)) {
                out.add(foundAt(entry.getKey(), group));
            }
        }
        out.addAll(fields(farmland));
        out.sort(Comparator.comparing((Found f) -> f.type().ordinal()).thenComparingInt(Found::x).thenComparingInt(Found::z));
        return out;
    }

    /**
     * The sites split into groups of bounded size: each site joins the first group whose first site (its anchor) is within
     * {@code distance} blocks, and 6 up or down, else starts a group. Not chained from site to site, so a street of houses is many
     * houses, not one.
     */
    private static List<List<Site>> groups(List<Site> sites, int distance) {
        List<Site> sorted = new ArrayList<>(sites);
        sorted.sort(Comparator.comparingInt(Site::x).thenComparingInt(Site::z).thenComparingInt(Site::y));
        List<List<Site>> out = new ArrayList<>();
        for (Site site : sorted) {
            List<Site> home = null;
            for (List<Site> group : out) {
                Site anchor = group.get(0);
                if (Math.abs(anchor.x() - site.x()) <= distance && Math.abs(anchor.z() - site.z()) <= distance
                        && Math.abs(anchor.y() - site.y()) <= 6) {
                    home = group;
                    break;
                }
            }
            if (home == null) {
                home = new ArrayList<>();
                out.add(home);
            }
            home.add(site);
        }
        return out;
    }

    /** The building a group of sites is: at its first site (the lowest x, then z), covering the group and a margin. */
    private static Found foundAt(BuildingType type, List<Site> group) {
        Site first = group.get(0);
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Site site : group) {
            minX = Math.min(minX, site.x());
            minZ = Math.min(minZ, site.z());
            maxX = Math.max(maxX, site.x());
            maxZ = Math.max(maxZ, site.z());
        }
        Rect cover = new Rect(minX, minZ, maxX - minX + 1, maxZ - minZ + 1).inflated(FOOTPRINT_RADIUS - 1);
        return new Found(type, first.x(), first.y(), first.z(), cover);
    }

    /** One farm for each field of at least {@link #MIN_FIELD} blocks of farmland, a field being the farmland within {@link #FIELD_SPAN} blocks of its first block. */
    private static List<Found> fields(List<Cell> farmland) {
        List<Cell> sorted = new ArrayList<>(farmland);
        sorted.sort(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z).thenComparingInt(Cell::y));
        List<List<Cell>> byAnchor = new ArrayList<>();
        for (Cell cell : sorted) {
            List<Cell> home = null;
            for (List<Cell> field : byAnchor) {
                Cell anchor = field.get(0);
                if (Math.abs(anchor.x() - cell.x()) <= FIELD_SPAN && Math.abs(anchor.z() - cell.z()) <= FIELD_SPAN
                        && Math.abs(anchor.y() - cell.y()) <= 2) {
                    home = field;
                    break;
                }
            }
            if (home == null) {
                home = new ArrayList<>();
                byAnchor.add(home);
            }
            home.add(cell);
        }
        List<Found> out = new ArrayList<>();
        for (List<Cell> field : byAnchor) {
            if (field.size() < MIN_FIELD) {
                continue;
            }
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            long sumY = 0;
            for (Cell cell : field) {
                minX = Math.min(minX, cell.x());
                minZ = Math.min(minZ, cell.z());
                maxX = Math.max(maxX, cell.x());
                maxZ = Math.max(maxZ, cell.z());
                sumY += cell.y();
            }
            Rect cover = new Rect(minX, minZ, maxX - minX + 1, maxZ - minZ + 1).inflated(1);
            out.add(new Found(BuildingType.FARM, (minX + maxX) / 2, (int) (sumY / field.size()), (minZ + maxZ) / 2, cover));
        }
        return out;
    }
}
