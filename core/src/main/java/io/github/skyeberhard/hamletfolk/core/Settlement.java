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
    private final UUID id;
    private final String name;
    private final String world;
    private final int centerX;
    private final int centerZ;
    private final long foundedDay;
    private long lastSimulatedDay;
    private double threat;
    private final Map<UUID, Resident> residents = new LinkedHashMap<>();
    private final Ledger ledger = new Ledger();
    private final List<HistoryEvent> history = new ArrayList<>();
    /** Residents turned into zombie villagers, keyed by the zombie's entity id. They can be cured. */
    private final Map<UUID, Resident> turned = new LinkedHashMap<>();
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

    /** How many residents are currently zombie villagers who could still be cured. */
    public int turnedCount() {
        return turned.size();
    }

    Map<UUID, Resident> turned() {
        return turned;
    }

    public List<HistoryEvent> history() {
        return Collections.unmodifiableList(history);
    }

    public void record(long day, HistoryEvent.Kind kind, String text) {
        history.add(new HistoryEvent(day, kind, text));
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
