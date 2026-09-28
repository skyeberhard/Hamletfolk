package io.github.skyeberhard.hamletfolk.core;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * All known settlements, plus an index from resident id to settlement. This is the
 * entry point the Minecraft layer uses to add, move and remove people.
 */
public final class SettlementRegistry {
    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();
    private final Map<UUID, UUID> residentIndex = new HashMap<>();
    /** Zombie villager id to the settlement holding the resident they used to be. */
    private final Map<UUID, UUID> turnedIndex = new HashMap<>();

    public Collection<Settlement> settlements() {
        return Collections.unmodifiableCollection(settlements.values());
    }

    public void add(Settlement settlement) {
        settlements.put(settlement.id(), settlement);
        for (Resident resident : settlement.residents()) {
            residentIndex.put(resident.id(), settlement.id());
        }
        for (UUID zombieId : settlement.turned().keySet()) {
            turnedIndex.put(zombieId, settlement.id());
        }
    }

    public Optional<Settlement> settlementOf(UUID residentId) {
        UUID settlementId = residentIndex.get(residentId);
        return settlementId == null ? Optional.empty() : Optional.ofNullable(settlements.get(settlementId));
    }

    public Optional<Resident> resident(UUID residentId) {
        return settlementOf(residentId).flatMap(s -> s.resident(residentId));
    }

    /** The nearest settlement in {@code world} whose center is within {@code radius} blocks. */
    public Optional<Settlement> nearest(String world, int x, int z, int radius) {
        long limit = (long) radius * radius;
        Settlement best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Settlement settlement : settlements.values()) {
            if (!settlement.world().equals(world)) {
                continue;
            }
            long distance = settlement.distanceSquared(x, z);
            if (distance <= limit && distance < bestDistance) {
                best = settlement;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Creates and registers a new settlement with a generated name. */
    public Settlement found(String world, int x, int z, long day) {
        UUID id = UUID.randomUUID();
        Random random = new Random(id.getLeastSignificantBits());
        Settlement settlement = new Settlement(id, NameGenerator.settlementName(random), world, x, z, day);
        settlement.record(day, HistoryEvent.Kind.FOUNDED, settlement.name() + " was first recorded.");
        add(settlement);
        return settlement;
    }

    /**
     * Creates a resident with a generated identity. Children of known parents inherit a
     * family name and blended traits.
     */
    public Resident enroll(Settlement settlement, UUID residentId, Occupation occupation, boolean adult,
                           long day, UUID parentA, UUID parentB) {
        Random random = new Random(residentId.getMostSignificantBits() ^ residentId.getLeastSignificantBits());
        Resident mother = parentA == null ? null : resident(parentA).orElse(null);
        Resident father = parentB == null ? null : resident(parentB).orElse(null);
        String familyName = mother != null ? mother.familyName()
                : father != null ? father.familyName()
                : NameGenerator.familyName(random);
        Traits traits = mother != null && father != null
                ? Traits.inherit(mother.traits(), father.traits(), random)
                : Traits.roll(random);
        Resident resident = new Resident(residentId, NameGenerator.givenName(random), familyName, traits,
                occupation, adult, settlement.effectiveDay(day), parentA, parentB, Needs.initial());
        settlement.addResident(resident);
        residentIndex.put(residentId, settlement.id());
        return resident;
    }

    /**
     * A resident became a zombie villager. They leave the population but are remembered
     * under the zombie's id, so a cure can bring them back.
     */
    public Optional<Resident> turn(UUID residentId, UUID zombieId) {
        UUID settlementId = residentIndex.get(residentId);
        Optional<Resident> resident = remove(residentId);
        resident.ifPresent(r -> {
            settlements.get(settlementId).turned().put(zombieId, r);
            turnedIndex.put(zombieId, settlementId);
        });
        return resident;
    }

    /** The resident a zombie villager used to be, if they were one of ours. */
    public Optional<Resident> turnedResident(UUID zombieId) {
        UUID settlementId = turnedIndex.get(zombieId);
        return settlementId == null ? Optional.empty()
                : Optional.ofNullable(settlements.get(settlementId).turned().get(zombieId));
    }

    public Optional<Settlement> settlementOfTurned(UUID zombieId) {
        UUID settlementId = turnedIndex.get(zombieId);
        return settlementId == null ? Optional.empty() : Optional.ofNullable(settlements.get(settlementId));
    }

    /**
     * A zombie villager was cured into a new villager entity. If it used to be a resident,
     * that same person returns to their settlement under the new villager's id.
     */
    public Optional<Resident> cure(UUID zombieId, UUID villagerId) {
        UUID settlementId = turnedIndex.remove(zombieId);
        if (settlementId == null) {
            return Optional.empty();
        }
        Settlement settlement = settlements.get(settlementId);
        Resident resident = settlement.turned().remove(zombieId).withId(villagerId);
        settlement.addResident(resident);
        residentIndex.put(villagerId, settlementId);
        return Optional.of(resident);
    }

    /** A zombie villager died; the resident it used to be can no longer be cured. */
    public Optional<Resident> forgetTurned(UUID zombieId) {
        UUID settlementId = turnedIndex.remove(zombieId);
        return settlementId == null ? Optional.empty()
                : Optional.ofNullable(settlements.get(settlementId).turned().remove(zombieId));
    }

    /** Removes a resident from wherever they live and returns them. */
    public Optional<Resident> remove(UUID residentId) {
        UUID settlementId = residentIndex.remove(residentId);
        if (settlementId == null) {
            return Optional.empty();
        }
        Settlement settlement = settlements.get(settlementId);
        return settlement == null ? Optional.empty() : Optional.ofNullable(settlement.removeResident(residentId));
    }
}
