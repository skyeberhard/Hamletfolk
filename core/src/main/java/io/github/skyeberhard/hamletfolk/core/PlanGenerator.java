package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;

/**
 * R8.3: makes a {@link VillagePlan} from the ground. A main square at the centre, a main street (the spine) through it
 * along whichever axis is flatter, branches across the spine, and lots along both sides of every street, each
 * tied to a building type. Lots whose ground is too steep, wet or unmeasured are dropped at founding, never built on
 * later. Stage 1 is the square, the spine and two branches; stage 2 ({@link #extend}) lengthens the spine and adds two more
 * branches, with their own lots, when the village levels up. A pure function of the ground and the seed, so the same
 * ground and seed always give the same plan, with no overlapping lots.
 */
public final class PlanGenerator {
    /** Half the width of the main square (it is 15 by 15). */
    static final int SQUARE_HALF = 7;
    static final int SPINE_HALF_WIDTH = 2;
    static final int BRANCH_HALF_WIDTH = 1;
    /** Blocks between a street's edge and the lots beside it. */
    static final int FRONT_GAP = 2;
    /** A lot's ground may differ by at most this many blocks between its highest and lowest column. */
    public static final int SLOPE_LIMIT = 4;
    static final int SPINE_1 = 48;
    static final int SPINE_2 = 72;
    static final int BRANCH_AT_1 = 28;
    static final int BRANCH_LEN_1 = 28;
    static final int BRANCH_AT_2 = 52;
    static final int BRANCH_LEN_2 = 36;
    /** R8.14: the plan grows to four stages. */
    public static final int MAX_STAGE = 4;
    /** Residents at which the plan reaches each stage (index is the stage). */
    private static final int[] STAGE_POPULATION = {0, 0, 25, 50, 100};
    /** How far the spine runs, where a stage's branches cross it and how long they are (index is the stage). */
    private static final int[] SPINE = {0, SPINE_1, SPINE_2, 100, 128};
    private static final int[] BRANCH_AT = {0, BRANCH_AT_1, BRANCH_AT_2, 76, 104};
    private static final int[] BRANCH_LEN = {0, BRANCH_LEN_1, BRANCH_LEN_2, 40, 44};

    /** The stage of plan a village of this many residents has: 1 to {@link #MAX_STAGE}. */
    public static int stageFor(int population) {
        int stage = 1;
        for (int s = 2; s <= MAX_STAGE; s++) {
            if (population >= STAGE_POPULATION[s]) {
                stage = s;
            }
        }
        return stage;
    }

    /** How far from the centre the ground must be loaded and measured to lay out a stage (its spine and a margin). */
    public static int readRadius(int stage) {
        // (never less than the 112 the first two stages always needed: the axis choice samples 96 blocks each way)
        return Math.max(112, SPINE[Math.max(1, Math.min(MAX_STAGE, stage))] + 40);
    }

    private static final BuildingType[] STAGE_ONE = {BuildingType.HOUSE, BuildingType.FARM, BuildingType.HOUSE,
            BuildingType.SHOP, BuildingType.HOUSE, BuildingType.MINE, BuildingType.HOUSE, BuildingType.SMITHY,
            BuildingType.HOUSE, BuildingType.TREASURY, BuildingType.HOUSE, BuildingType.GUARD_POST, BuildingType.FARM,
            BuildingType.HOUSE};
    private static final BuildingType[] STAGE_THREE = {BuildingType.HOUSE, BuildingType.SHOP, BuildingType.HOUSE,
            BuildingType.TREASURY, BuildingType.HOUSE, BuildingType.FARM, BuildingType.HOUSE, BuildingType.GUARD_POST,
            BuildingType.HOUSE, BuildingType.SMITHY};
    private static final BuildingType[] STAGE_FOUR = {BuildingType.HOUSE, BuildingType.HOUSE, BuildingType.SHOP,
            BuildingType.HOUSE, BuildingType.FARM, BuildingType.HOUSE, BuildingType.MINE, BuildingType.HOUSE,
            BuildingType.GUARD_POST, BuildingType.HOUSE};
    private static final BuildingType[] STAGE_TWO = {BuildingType.HOUSE, BuildingType.HOUSE, BuildingType.FARM,
            BuildingType.HOUSE, BuildingType.SHOP, BuildingType.HOUSE, BuildingType.SMITHY, BuildingType.HOUSE,
            BuildingType.FARM, BuildingType.HOUSE};

    private PlanGenerator() {
    }

    /** How much ground a building needs: along the street, and deep from it. */
    static int[] size(BuildingType type) {
        return switch (type) {
            case HOUSE, GUARD_POST -> new int[] {9, 9};
            case FARM -> new int[] {17, 13};
            case SHOP -> new int[] {11, 9};
            case TREASURY -> new int[] {11, 11};
            // The game's smithies are up to 9 by 12 blocks, so the lot has to be at least 13 each way.
            case SMITHY -> new int[] {13, 13};
            case MINE -> new int[] {13, 13};
            case SQUARE -> new int[] {SQUARE_HALF * 2 + 1, SQUARE_HALF * 2 + 1};
            case SAWMILL, GRANARY -> new int[] {11, 9};
            case HARBOUR, PENS, SMOKEHOUSE, TANNERY, STABLE, APIARY, MAP_ROOM, LIBRARY, GLASSWORKS, TRADING_POST, BOWYER, MASONS_YARD,
                    ARMOURY, CHAPEL -> new int[] {11, 9};
            case FORGE -> new int[] {11, 11};
            case STREET_LIGHTS, PALISADE, RAMPART, GATEHOUSE, TOWER -> new int[] {1, 1}; // works on the plan, not buildings on a lot
        };
    }

    // ----- coordinates: "along" is the spine's axis and "cross" is across it -----

    private record Frame(int centerX, int centerZ, boolean alongX) {
        /** A rectangle from offsets in (along, cross) from the centre. */
        Rect rect(int along, int cross, int alongLen, int crossLen) {
            return alongX ? new Rect(centerX + along, centerZ + cross, alongLen, crossLen)
                    : new Rect(centerX + cross, centerZ + along, crossLen, alongLen);
        }
    }

    /** True if the main square (15 by 15 around the centre) is dry, measured ground: a village cannot be planned in a lake. */
    public static boolean siteUsable(HeightSource terrain, int centerX, int centerZ) {
        for (int x = centerX - SQUARE_HALF; x <= centerX + SQUARE_HALF; x++) {
            for (int z = centerZ - SQUARE_HALF; z <= centerZ + SQUARE_HALF; z++) {
                if (!usable(terrain, x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static VillagePlan generate(int centerX, int centerZ, long seed, String biomeSet, HeightSource terrain) {
        boolean alongX = roughness(terrain, centerX, centerZ, true) < roughness(terrain, centerX, centerZ, false)
                || (roughness(terrain, centerX, centerZ, true) == roughness(terrain, centerX, centerZ, false)
                        && Math.floorMod(seed, 2) == 0);
        VillagePlan plan = new VillagePlan(seed, biomeSet, centerX, centerZ, alongX, 1);
        Frame frame = new Frame(centerX, centerZ, alongX);
        plan.setSquare(frame.rect(-SQUARE_HALF, -SQUARE_HALF, 2 * SQUARE_HALF + 1, 2 * SQUARE_HALF + 1));

        int up = armLength(terrain, frame, SQUARE_HALF + 1, SPINE_1, +1, 0);
        int down = armLength(terrain, frame, SQUARE_HALF + 1, SPINE_1, -1, 0);
        Rect spine = frame.rect(-down, -SPINE_HALF_WIDTH, up + down + 1, 2 * SPINE_HALF_WIDTH + 1);
        plan.addRoad(new VillagePlan.Road(plan.newId(), VillagePlan.RoadKind.SPINE, spine, 1));
        List<Arm> arms = new ArrayList<>();
        arms.add(new Arm(true, 0, +1, SQUARE_HALF + 2, up, SPINE_HALF_WIDTH));
        arms.add(new Arm(true, 0, -1, SQUARE_HALF + 2, down, SPINE_HALF_WIDTH));
        addBranch(plan, frame, terrain, +BRANCH_AT_1, BRANCH_LEN_1, 1, arms, up);
        addBranch(plan, frame, terrain, -BRANCH_AT_1, BRANCH_LEN_1, 1, arms, down);
        placeLots(plan, frame, terrain, arms, STAGE_ONE, 1, 0);
        return plan;
    }

    /**
     * Grows the plan by one stage (up to {@link #MAX_STAGE}): the spine is lengthened, two more branches are added further
     * out, and the new streets get their own lots. Does nothing, and returns false, if the plan is already at the last stage.
     * (R8.14: stages 3 and 4 are the same again, further out.)
     */
    public static boolean extend(VillagePlan plan, HeightSource terrain) {
        int next = plan.stage() + 1;
        if (next > MAX_STAGE) {
            return false;
        }
        Frame frame = new Frame(plan.centerX(), plan.centerZ(), plan.spineAlongX());
        int oldUp = 0;
        int oldDown = 0;
        for (VillagePlan.Road road : plan.roads()) {
            if (road.kind() == VillagePlan.RoadKind.SPINE) {
                oldUp = Math.max(oldUp, alongMax(frame, road.rect()));
                oldDown = Math.max(oldDown, -alongMin(frame, road.rect()));
            }
        }
        int up = armLength(terrain, frame, oldUp + 1, SPINE[next], +1, 0);
        int down = armLength(terrain, frame, oldDown + 1, SPINE[next], -1, 0);
        List<Arm> arms = new ArrayList<>();
        if (up > oldUp) {
            plan.addRoad(new VillagePlan.Road(plan.newId(), VillagePlan.RoadKind.SPINE,
                    frame.rect(oldUp + 1, -SPINE_HALF_WIDTH, up - oldUp, 2 * SPINE_HALF_WIDTH + 1), next));
            arms.add(new Arm(true, 0, +1, oldUp + 2, up, SPINE_HALF_WIDTH));
        }
        if (down > oldDown) {
            plan.addRoad(new VillagePlan.Road(plan.newId(), VillagePlan.RoadKind.SPINE,
                    frame.rect(-down, -SPINE_HALF_WIDTH, down - oldDown, 2 * SPINE_HALF_WIDTH + 1), next));
            arms.add(new Arm(true, 0, -1, oldDown + 2, down, SPINE_HALF_WIDTH));
        }
        addBranch(plan, frame, terrain, +BRANCH_AT[next], BRANCH_LEN[next], next, arms, up);
        addBranch(plan, frame, terrain, -BRANCH_AT[next], BRANCH_LEN[next], next, arms, down);
        plan.setStage(next);
        BuildingType[][] sequences = {null, STAGE_ONE, STAGE_TWO, STAGE_THREE, STAGE_FOUR};
        placeLots(plan, frame, terrain, arms, sequences[next], next, plan.lots().size());
        return true;
    }

    private static int alongMax(Frame frame, Rect rect) {
        return frame.alongX() ? rect.maxX() - frame.centerX() : rect.maxZ() - frame.centerZ();
    }

    private static int alongMin(Frame frame, Rect rect) {
        return frame.alongX() ? rect.x() - frame.centerX() : rect.z() - frame.centerZ();
    }

    // ----- streets -----

    /**
     * One street arm lots are placed along: {@code onSpine} tells which kind; {@code at} is where a branch crosses the
     * spine (the offset along it); {@code direction} is outward (+1 or -1); lots begin {@code from} blocks out and stop at
     * {@code to}; {@code halfWidth} is the street's half width.
     */
    private record Arm(boolean onSpine, int at, int direction, int from, int to, int halfWidth) {
    }

    private static void addBranch(VillagePlan plan, Frame frame, HeightSource terrain, int at, int length, int stage,
            List<Arm> arms, int spineReach) {
        // A branch only exists where the main street actually reaches (it would be cut off from the village otherwise).
        if (spineReach < Math.abs(at) + BRANCH_HALF_WIDTH) {
            return;
        }
        // A branch runs across the spine at offset 'at'; each of its two arms ends where the ground gives out.
        int toPositive = armLength(terrain, frame, SPINE_HALF_WIDTH + 1, length, +1, at);
        int toNegative = armLength(terrain, frame, SPINE_HALF_WIDTH + 1, length, -1, at);
        Rect rect = frame.rect(at - BRANCH_HALF_WIDTH, -toNegative, 2 * BRANCH_HALF_WIDTH + 1, toPositive + toNegative + 1);
        plan.addRoad(new VillagePlan.Road(plan.newId(), VillagePlan.RoadKind.BRANCH, rect, stage));
        arms.add(new Arm(false, at, +1, SPINE_HALF_WIDTH + FRONT_GAP + 1, toPositive, BRANCH_HALF_WIDTH));
        arms.add(new Arm(false, at, -1, SPINE_HALF_WIDTH + FRONT_GAP + 1, toNegative, BRANCH_HALF_WIDTH));
    }

    /**
     * How far a street can run outward (up to {@code max}) before it meets water or ground nobody measured, starting
     * the check {@code from} blocks out. {@code at} is the street's offset along the spine when it is a branch (0 for
     * the spine itself, whose "cross" offset is then zero).
     */
    private static int armLength(HeightSource terrain, Frame frame, int from, int max, int direction, int at) {
        boolean spine = at == 0;
        int reach = Math.min(from - 1, max);
        for (int step = from; step <= max; step++) {
            // Every column of the street's width must be dry and measured, not only its middle.
            boolean ok = true;
            if (spine) {
                for (int cross = -SPINE_HALF_WIDTH; cross <= SPINE_HALF_WIDTH && ok; cross++) {
                    Rect column = frame.rect(direction * step, cross, 1, 1);
                    ok = usable(terrain, column.x(), column.z());
                }
            } else {
                for (int along = at - BRANCH_HALF_WIDTH; along <= at + BRANCH_HALF_WIDTH && ok; along++) {
                    Rect column = frame.rect(along, direction * step, 1, 1);
                    ok = usable(terrain, column.x(), column.z());
                }
            }
            if (!ok) {
                break;
            }
            reach = step;
        }
        return Math.max(0, reach);
    }

    private static boolean usable(HeightSource terrain, int x, int z) {
        return terrain.height(x, z) != HeightSource.UNKNOWN && !terrain.water(x, z);
    }

    /** Which axis the ground is flatter along, summed over 96 blocks each way from the centre in steps of 4. */
    private static long roughness(HeightSource terrain, int cx, int cz, boolean alongX) {
        long total = 0;
        int previous = terrain.height(cx, cz);
        for (int step = 4; step <= 96; step += 4) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int x = alongX ? cx + sign * step : cx;
                int z = alongX ? cz : cz + sign * step;
                int h = terrain.height(x, z);
                total += (h == HeightSource.UNKNOWN || previous == HeightSource.UNKNOWN) ? 10
                        : Math.abs(h - previous) + (terrain.water(x, z) ? 10 : 0);
            }
        }
        return total;
    }

    // ----- lots -----

    private static void placeLots(VillagePlan plan, Frame frame, HeightSource terrain, List<Arm> arms,
            BuildingType[] sequence, int stage, int sequenceStart) {
        int start = (int) Math.floorMod(plan.seed(), (long) sequence.length) + sequenceStart;
        java.util.List<BuildingType> pool = new java.util.ArrayList<>();
        for (Arm arm : arms) {
            for (int side = -1; side <= 1; side += 2) {
                int cursor = arm.from();
                int guard = 0;
                while (cursor < arm.to() && guard++ < 200) {
                    if (pool.isEmpty()) {
                        for (int i = 0; i < sequence.length; i++) {
                            pool.add(sequence[Math.floorMod(start + i, sequence.length)]);
                        }
                    }
                    // Zoning: of the buildings still to place, the one whose place is nearest this far out from the square.
                    // If the best-suited one will not fit before the end of the arm, the next best that does goes there: a
                    // long farm lot must not end the street and leave the guard post and the mine with nowhere to stand.
                    Rect probe = lotRect(frame, arm, side, cursor, 9, 9);
                    BuildingType type = null;
                    int[] size = null;
                    Rect candidate = null;
                    for (BuildingType option : probe == null ? pool : inOrderOfSuit(plan, pool, probe)) {
                        int[] optionSize = size(option);
                        Rect fitted = lotRect(frame, arm, side, cursor, optionSize[0], optionSize[1]);
                        if (fitted != null && cursor + optionSize[0] - 1 <= arm.to()) {
                            type = option;
                            size = optionSize;
                            candidate = fitted;
                            break;
                        }
                    }
                    if (candidate == null) {
                        break;
                    }
                    if (blockedByStreet(plan, candidate)) {
                        cursor++;
                        continue;
                    }
                    if (overlapsAnything(plan, candidate)) {
                        cursor += size[0] + 2;
                        continue;
                    }
                    if (!buildable(terrain, candidate)) {
                        plan.addDropped(1);
                        cursor += size[0] + 2;
                        continue;
                    }
                    plan.addLot(new VillagePlan.Lot(plan.newId(), candidate, type, plan.biomeSet(), stage,
                            VillagePlan.LotStatus.RESERVED));
                    pool.remove(type);
                    cursor += size[0] + 2;
                }
            }
        }
    }

    /**
     * How far from the main square each kind of building likes to be: the shop and the treasury beside it, houses a
     * little way out, then the smithy, with farms, the mine and the guard post on the outskirts.
     */
    private static int preferredDistance(BuildingType type) {
        return switch (type) {
            case SHOP -> 11;
            case TREASURY -> 14;
            case HOUSE -> 24;
            case SMITHY -> 28;
            case FARM -> 41;
            case MINE -> 33;
            case GUARD_POST -> 37;
            case SAWMILL -> 31;
            case FORGE -> 28;
            case GRANARY -> 36;
            case HARBOUR, PENS, SMOKEHOUSE, TANNERY, STABLE, APIARY, MAP_ROOM, LIBRARY, GLASSWORKS, TRADING_POST, BOWYER, MASONS_YARD,
                    ARMOURY, CHAPEL -> 30;
            case SQUARE, STREET_LIGHTS, PALISADE, RAMPART, GATEHOUSE, TOWER -> 0;
        };
    }

    /**
     * The kinds in {@code pool} (each once), best suited to a spot first: the one whose preferred distance from the square
     * is closest. Houses are the most common and can go nearly anywhere, so a little is held against them: when a house
     * and a one-off building suit a spot about equally, the one-off gets it, and every kind gets a lot before any repeats.
     */
    private static List<BuildingType> inOrderOfSuit(VillagePlan plan, java.util.List<BuildingType> pool, Rect spot) {
        int distance = (int) Math.round(Math.hypot(spot.centerX() - plan.centerX(), spot.centerZ() - plan.centerZ()));
        List<BuildingType> kinds = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(pool));
        kinds.sort(java.util.Comparator.comparingInt(
                (BuildingType t) -> Math.abs(preferredDistance(t) - distance) + (t == BuildingType.HOUSE ? HOUSE_PENALTY : 0)));
        return kinds;
    }

    /** Blocks of distance counted against a house when it competes with a one-off building for a spot. */
    private static final int HOUSE_PENALTY = 8;

    /** The rectangle for a lot {@code cursor} blocks out along an arm, on one side of its street. */
    private static Rect lotRect(Frame frame, Arm arm, int side, int cursor, int alongLen, int depth) {
        int out = arm.direction() > 0 ? cursor : -cursor - alongLen + 1; // offset along the arm
        int across = side > 0 ? arm.halfWidth() + FRONT_GAP : -(arm.halfWidth() + FRONT_GAP) - depth + 1;
        return arm.onSpine() ? frame.rect(out, across, alongLen, depth)
                : frame.rect(arm.at() + across, out, depth, alongLen);
    }

    private static boolean blockedByStreet(VillagePlan plan, Rect candidate) {
        Rect margin = candidate.inflated(1);
        if (margin.overlaps(plan.square())) {
            return true;
        }
        for (VillagePlan.Road road : plan.roads()) {
            if (margin.overlaps(road.rect())) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlapsAnything(VillagePlan plan, Rect candidate) {
        Rect margin = candidate.inflated(1);
        for (VillagePlan.Lot lot : plan.lots()) {
            if (margin.overlaps(lot.rect())) {
                return true;
            }
        }
        return false;
    }

    /** True if every column of the footprint is measured, dry, and within {@link #SLOPE_LIMIT} of the others. */
    static boolean buildable(HeightSource terrain, Rect footprint) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int x = footprint.x(); x <= footprint.maxX(); x++) {
            for (int z = footprint.z(); z <= footprint.maxZ(); z++) {
                int h = terrain.height(x, z);
                if (h == HeightSource.UNKNOWN || terrain.water(x, z)) {
                    return false;
                }
                lowest = Math.min(lowest, h);
                highest = Math.max(highest, h);
            }
        }
        return highest - lowest <= SLOPE_LIMIT;
    }
}
