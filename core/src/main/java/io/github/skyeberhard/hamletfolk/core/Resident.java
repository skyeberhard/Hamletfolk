package io.github.skyeberhard.hamletfolk.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A persistent person. On the Paper side the id is the villager entity's UUID, but the
 * resident exists as data whether or not its entity is loaded.
 */
public final class Resident {
    private final UUID id;
    private String givenName;
    private String familyName;
    private final Traits traits;
    private final long bornDay;
    private final UUID parentA;
    private final UUID parentB;
    private final Needs needs;
    private final Map<UUID, Integer> familiarity = new HashMap<>();
    private Occupation occupation;
    private boolean adult;
    /** Day this resident last could not work because an input was missing, or -1. */
    private long lastBlockedDay = -1;

    public Resident(UUID id, String givenName, String familyName, Traits traits, Occupation occupation,
                    boolean adult, long bornDay, UUID parentA, UUID parentB, Needs needs) {
        this.id = id;
        this.givenName = givenName;
        this.familyName = familyName;
        this.traits = traits;
        this.occupation = occupation;
        this.adult = adult;
        this.bornDay = bornDay;
        this.parentA = parentA;
        this.parentB = parentB;
        this.needs = needs;
    }

    public UUID id() {
        return id;
    }

    public String givenName() {
        return givenName;
    }

    public String familyName() {
        return familyName;
    }

    public String fullName() {
        return givenName + " " + familyName;
    }

    public void rename(String givenName, String familyName) {
        this.givenName = givenName;
        this.familyName = familyName;
    }

    public Traits traits() {
        return traits;
    }

    public Occupation occupation() {
        return occupation;
    }

    public void setOccupation(Occupation occupation) {
        this.occupation = occupation;
    }

    public boolean adult() {
        return adult;
    }

    public void setAdult(boolean adult) {
        this.adult = adult;
    }

    public long bornDay() {
        return bornDay;
    }

    public UUID parentA() {
        return parentA;
    }

    public UUID parentB() {
        return parentB;
    }

    public Needs needs() {
        return needs;
    }

    public long lastBlockedDay() {
        return lastBlockedDay;
    }

    void setLastBlockedDay(long day) {
        this.lastBlockedDay = day;
    }

    /**
     * The same person under a new entity id. Converting a mob (e.g. curing a zombie villager)
     * creates a new entity, so the identity has to move to its UUID.
     */
    Resident withId(UUID newId) {
        Resident copy = new Resident(newId, givenName, familyName, traits, occupation, adult, bornDay,
                parentA, parentB, new Needs(needs.food(), needs.safety(), needs.purpose()));
        copy.lastBlockedDay = lastBlockedDay;
        copy.familiarity.putAll(familiarity);
        return copy;
    }

    /** How many times this resident has spoken with a given player. */
    public int familiarityWith(UUID playerId) {
        return familiarity.getOrDefault(playerId, 0);
    }

    public void recordConversation(UUID playerId) {
        familiarity.merge(playerId, 1, Integer::sum);
    }

    Map<UUID, Integer> familiarity() {
        return familiarity;
    }
}
