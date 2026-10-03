package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A community of residents with a shared ledger and a written history. */
public final class Settlement {
    /** Most events kept per settlement; past this, minor events go first (R1.21). */
    public static final int MAX_HISTORY = 500;
    /** A donor's donations within this many days of their first are merged into one line. */
    public static final int DONATION_MERGE_DAYS = 7;

    private final UUID id;
    private String name;
    private final String world;
    private final int centerX;
    private final int centerZ;
    private final long foundedDay;
    private long lastSimulatedDay;
    private double threat;
    private final Map<UUID, Resident> residents = new LinkedHashMap<>();
    private final Ledger ledger = new Ledger();
    private final List<HistoryEvent> history = new ArrayList<>();
    private final ResourceFlow flow = new ResourceFlow();
    /** Residents turned into zombie villagers, keyed by the zombie's entity id. They can be cured. */
    private final Map<UUID, Resident> turned = new LinkedHashMap<>();
    private final Map<UUID, Long> departed = new LinkedHashMap<>();
    private final Map<ResourceType, Request> requests = new LinkedHashMap<>();
    private final Map<String, Building> buildings = new LinkedHashMap<>();
    private final Housing housing = new Housing();
    private final List<UUID> newlyDeparted = new ArrayList<>();
    /** Ongoing or one-time conditions, keyed by name, valued by the day they began. */
    private final Map<String, Long> conditions = new HashMap<>();

    public Settlement(UUID id, String name, String world, int centerX, int centerZ, long foundedDay) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.foundedDay = foundedDay;
        this.lastSimulatedDay = foundedDay;
    }

    public UUID id() {
        return id;
    }

    void setName(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public String world() {
        return world;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public long foundedDay() {
        return foundedDay;
    }

    public long lastSimulatedDay() {
        return lastSimulatedDay;
    }

    void setLastSimulatedDay(long day) {
        this.lastSimulatedDay = day;
    }

    /** Perceived danger, 0-100. Rises with deaths and raids and fades over time. */
    public double threat() {
        return threat;
    }

    public void raiseThreat(double amount) {
        threat = Math.min(100, threat + amount);
    }

    void setThreat(double threat) {
        this.threat = Math.max(0, Math.min(100, threat));
    }

    public Ledger ledger() {
        return ledger;
    }

    public Collection<Resident> residents() {
        return Collections.unmodifiableCollection(residents.values());
    }

    public Optional<Resident> resident(UUID id) {
        return Optional.ofNullable(residents.get(id));
    }

    public int population() {
        return residents.size();
    }

    void addResident(Resident resident) {
        residents.put(resident.id(), resident);
    }

    Resident removeResident(UUID id) {
        return residents.remove(id);
    }

    /** Most buildings one settlement can have registered, so a sign-spamming player cannot bloat the save. */
    public static final int MAX_BUILDINGS = 200;

    /** What registering a building did. */
    public enum Registration {
        /** A new building, or a different kind of building replacing one at the same sign. */
        REGISTERED,
        /** The same kind of building was already registered at that sign. */
        ALREADY_REGISTERED,
        /** The settlement already has {@link #MAX_BUILDINGS}. */
        TOO_MANY
    }

    /**
     * R2.1: registers a building at its sign, and writes it into the history. A second registration at
     * the same sign replaces the first if it names a different kind of building, and is ignored if not.
     */
    public Registration registerBuilding(Building building) {
        Building existing = buildings.get(building.key());
        if (existing != null && existing.type() == building.type()) {
            return Registration.ALREADY_REGISTERED;
        }
        if (existing == null && buildings.size() >= MAX_BUILDINGS) {
            return Registration.TOO_MANY;
        }
        buildings.put(building.key(), building);
        recordBuildingChange(building.registeredDay(), building.registeredBy(), building.registeredBy() + " registered a "
                + building.type().label().toLowerCase(java.util.Locale.ROOT) + " in " + name + ".");
        return Registration.REGISTERED;
    }

    /** R2.1: removes the building whose sign is at a position (it was broken or changed), and notes it in the history. */
    public Optional<Building> removeBuilding(int x, int y, int z, long day) {
        Building removed = buildings.remove(Building.key(x, y, z));
        if (removed != null) {
            recordBuildingChange(day, removed.registeredBy(), "The " + removed.type().label().toLowerCase(java.util.Locale.ROOT)
                    + " registered by " + removed.registeredBy() + " in " + name + " was taken down.");
        }
        return Optional.ofNullable(removed);
    }

    /**
     * Writes a building change into the history, merging it into the same player's earlier building
     * change from the last {@code DONATION_MERGE_DAYS} days ("Skye changed 14 buildings this week"), so a
     * player toggling signs cannot push everything else out of the bounded history.
     */
    private void recordBuildingChange(long day, String actor, String text) {
        day = effectiveDay(day);
        for (int i = history.size() - 1; i >= 0; i--) {
            HistoryEvent event = history.get(i);
            if (event.day() <= day - DONATION_MERGE_DAYS) {
                break;
            }
            if (event.kind() == HistoryEvent.Kind.BUILDING && actor.equals(event.actor())) {
                int count = event.count() + 1;
                history.set(i, new HistoryEvent(event.day(), event.kind(),
                        actor + " changed " + count + " buildings this week.", count, actor));
                return;
            }
        }
        add(new HistoryEvent(day, HistoryEvent.Kind.BUILDING, text, 1, actor));
    }

    /** R2.2: the beds in this settlement, counted by the Minecraft layer. */
    public Housing housing() {
        return housing;
    }

    /** R2.2: how many people the beds can house. */
    public int housingCapacity() {
        return housing.capacity();
    }

    /** R2.2: beds nobody lives in yet: capacity less the population, and 0 if it is already crowded. */
    public int freeBeds() {
        return Math.max(0, housing.capacity() - residents.size());
    }

    /** Every registered building, in the order they were registered. */
    public Collection<Building> buildings() {
        return Collections.unmodifiableCollection(buildings.values());
    }

    /** How many buildings of a kind are registered. */
    public int buildingCount(BuildingType type) {
        return (int) buildings.values().stream().filter(b -> b.type() == type).count();
    }

    public boolean hasBuildingAt(int x, int y, int z) {
        return buildings.containsKey(Building.key(x, y, z));
    }

    /** Loads a saved building without writing history. */
    void addBuilding(Building building) {
        buildings.put(building.key(), building);
    }

    /** R3.3: what the village is asking for right now, at most one request per resource. */
    public Collection<Request> requests() {
        return Collections.unmodifiableCollection(requests.values());
    }

    Map<ResourceType, Request> requestMap() {
        return requests;
    }

    /**
     * R4.15: residents who died of old age and the day they did, kept so their villager entity is
     * removed when it next loads instead of being enrolled again as a stranger. Saved.
     */
    Map<UUID, Long> departed() {
        return departed;
    }

    /** True if this id belonged to a resident who has died of old age. */
    public boolean hasDeparted(UUID id) {
        return departed.containsKey(id);
    }

    /** Ids that have died of old age since the last call, for the Minecraft layer to remove their entities. */
    public List<UUID> drainNewlyDeparted() {
        List<UUID> out = new ArrayList<>(newlyDeparted);
        newlyDeparted.clear();
        return out;
    }

    void markDeparted(UUID id, long day) {
        departed.put(id, day);
        newlyDeparted.add(id);
    }

    /** How many residents are currently zombie villagers who could still be cured. */
    public int turnedCount() {
        return turned.size();
    }

    Map<UUID, Resident> turned() {
        return turned;
    }

    public ResourceFlow flow() {
        return flow;
    }

    /** How many days of flow data the settlement has, up to {@code ResourceFlow.WINDOW_DAYS}, at least 1. */
    public int flowDays() {
        return (int) Math.max(1, Math.min(ResourceFlow.WINDOW_DAYS, lastSimulatedDay - foundedDay));
    }

    public List<HistoryEvent> history() {
        return Collections.unmodifiableList(history);
    }

    public void record(long day, HistoryEvent.Kind kind, String text) {
        add(new HistoryEvent(effectiveDay(day), kind, text));
    }

    /**
     * Records a donation, merging it into the donor's earlier donation from the last
     * {@code DONATION_MERGE_DAYS} days ("Skye made 14 donations this week") so a busy
     * donor doesn't fill the history.
     */
    public void recordDonation(long day, String donor, int amount, String itemName) {
        day = effectiveDay(day);
        for (int i = history.size() - 1; i >= 0; i--) {
            HistoryEvent event = history.get(i);
            if (event.day() <= day - DONATION_MERGE_DAYS) {
                break;
            }
            if (event.kind() == HistoryEvent.Kind.DONATION && donor.equals(event.actor())) {
                int count = event.count() + 1;
                history.set(i, new HistoryEvent(event.day(), event.kind(),
                        donor + " made " + count + " donations this week.", count, donor));
                return;
            }
        }
        add(new HistoryEvent(day, HistoryEvent.Kind.DONATION,
                donor + " gave " + amount + " " + itemName + " to the village.", 1, donor));
    }

    /**
     * R1.23: the day to file an event under. If the world clock has gone backwards (e.g. after
     * {@code /time set}), the settlement's own day wins, so nothing is recorded out of order.
     */
    public long effectiveDay(long worldDay) {
        long latest = history.isEmpty() ? 0 : history.get(history.size() - 1).day();
        return Math.max(worldDay, Math.max(lastSimulatedDay, latest));
    }

    /** Adds an event as given (loading a save uses this) and, if the history is over {@code MAX_HISTORY}, drops the oldest minor one, else the oldest non-founding one. */
    void add(HistoryEvent event) {
        history.add(event);
        while (history.size() > MAX_HISTORY) {
            int drop = -1;
            for (int i = 0; i < history.size(); i++) {
                if (!history.get(i).kind().isMajor()) {
                    drop = i;
                    break;
                }
            }
            if (drop < 0) {
                drop = history.get(0).kind() == HistoryEvent.Kind.FOUNDED ? 1 : 0;
            }
            history.remove(drop);
        }
    }

    /** The most recent event of the given kind on or after {@code sinceDay}, if any. */
    public Optional<HistoryEvent> latest(HistoryEvent.Kind kind, long sinceDay) {
        for (int i = history.size() - 1; i >= 0; i--) {
            HistoryEvent event = history.get(i);
            if (event.day() < sinceDay) {
                break;
            }
            if (event.kind() == kind) {
                return Optional.of(event);
            }
        }
        return Optional.empty();
    }

    /** True once R1.5 has marked this settlement abandoned: empty for {@code ABANDONMENT_DAYS} days straight. */
    public boolean isAbandoned() {
        return hasCondition("abandoned");
    }

    public boolean hasCondition(String key) {
        return conditions.containsKey(key);
    }

    Map<String, Long> conditions() {
        return conditions;
    }

    public long distanceSquared(int x, int z) {
        long dx = x - centerX;
        long dz = z - centerZ;
        return dx * dx + dz * dz;
    }
}
