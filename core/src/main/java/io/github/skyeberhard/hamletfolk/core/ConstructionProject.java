package io.github.skyeberhard.hamletfolk.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * R4.7, R4.8: one building the village has decided to put up (or upgrade): what, which tier of which template, and
 * where. It is queued, then claimed by a builder, who works through the block diff over time; the diff itself is
 * never stored (it is worked out again from the world each time), so a restart loses nothing but the payments in
 * flight, which are kept as {@link #credit}. A finished project stays on record so a later upgrade knows what the
 * village built itself (a building a player made is never in this list, so it is never replaced).
 */
public final class ConstructionProject {
    public enum Status {
        QUEUED, ACTIVE, DONE, CANCELLED
    }

    /** {@link #signY()} until the building's sign has been placed. */
    public static final int NO_SIGN = Integer.MIN_VALUE;

    private final int id;
    private final BuildingType type;
    private final int tier;
    private final int previousTier; // 0 for a new building, else the tier this one replaces
    private final String biomeSet;
    private final int x;
    private final int y;
    private final int z;
    private int lotId;
    private final long queuedDay;
    private Status status;
    private UUID builder;
    private long finishedDay;
    private boolean siteChecked;
    private int shiftX;
    private int shiftZ;
    private int signX;
    private int signY = NO_SIGN;
    private int signZ;
    private final Map<ResourceType, Integer> credit = new EnumMap<>(ResourceType.class);
    /** Not saved: what the builder is waiting for and how much is left, for display. */
    private ResourceType waitingFor;
    private int blocksLeft = -1;

    public ConstructionProject(int id, BuildingType type, int tier, int previousTier, String biomeSet, int x, int y, int z,
            int lotId, long queuedDay) {
        this.id = id;
        this.type = type;
        this.tier = tier;
        this.previousTier = previousTier;
        this.biomeSet = BiomeSet.normalize(biomeSet);
        this.x = x;
        this.y = y;
        this.z = z;
        this.lotId = lotId;
        this.queuedDay = queuedDay;
        this.status = Status.QUEUED;
    }

    public int id() {
        return id;
    }

    public BuildingType type() {
        return type;
    }

    public int tier() {
        return tier;
    }

    public int previousTier() {
        return previousTier;
    }

    public boolean isUpgrade() {
        return previousTier > 0;
    }

    public String biomeSet() {
        return biomeSet;
    }

    /** The floor-level corner of smallest x and z (see {@link Blueprint}). */
    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public int lotId() {
        return lotId;
    }

    /** The plan the lot belonged to is gone (a re-plan): the project stays on record but is tied to no lot. */
    public void forgetLot() {
        this.lotId = -1;
    }

    public long queuedDay() {
        return queuedDay;
    }

    public Status status() {
        return status;
    }

    public boolean isOpen() {
        return status == Status.QUEUED || status == Status.ACTIVE;
    }

    public UUID builder() {
        return builder;
    }

    public long finishedDay() {
        return finishedDay;
    }

    /** True once the site has been checked for anything a player built (done once, before the first block). */
    public boolean siteChecked() {
        return siteChecked;
    }

    public void setSiteChecked(boolean siteChecked) {
        this.siteChecked = siteChecked;
    }

    /** For an upgrade: where the building being replaced has its corner, relative to this one's ({@code old = new + shift}). */
    public int shiftX() {
        return shiftX;
    }

    public int shiftZ() {
        return shiftZ;
    }

    public void setShift(int x, int z) {
        this.shiftX = x;
        this.shiftZ = z;
    }

    /** Where the finished building's sign is (registered as the building), or {@link #NO_SIGN} for y. */
    public int signX() {
        return signX;
    }

    public int signY() {
        return signY;
    }

    public int signZ() {
        return signZ;
    }

    public void setSign(int x, int y, int z) {
        this.signX = x;
        this.signY = y;
        this.signZ = z;
    }

    void claim(UUID resident) {
        this.builder = resident;
        this.status = Status.ACTIVE;
    }

    void release() {
        this.builder = null;
        this.status = Status.QUEUED;
    }

    void finish(long day) {
        this.status = Status.DONE;
        this.finishedDay = day;
        this.builder = null;
        this.waitingFor = null;
        this.blocksLeft = 0;
    }

    void cancel(long day) {
        this.status = Status.CANCELLED;
        this.finishedDay = day;
        this.builder = null;
        this.waitingFor = null;
    }

    /** Restores a saved status without the side effects of the transitions above. */
    void restore(Status status, UUID builder, long finishedDay, boolean siteChecked) {
        this.status = status;
        this.builder = builder;
        this.finishedDay = finishedDay;
        this.siteChecked = siteChecked;
    }

    /** Half-units of each resource already paid for blocks not yet placed (a slab costs half a unit). */
    Map<ResourceType, Integer> credit() {
        return credit;
    }

    public ResourceType waitingFor() {
        return waitingFor;
    }

    public void setWaitingFor(ResourceType waitingFor) {
        this.waitingFor = waitingFor;
    }

    /** Blocks still to place at the last count, or -1 before the first. */
    public int blocksLeft() {
        return blocksLeft;
    }

    public void setBlocksLeft(int blocksLeft) {
        this.blocksLeft = blocksLeft;
    }
}
