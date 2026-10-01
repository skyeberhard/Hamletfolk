package io.github.skyeberhard.hamletfolk.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * A persistent person. On the Paper side the id is the villager entity's UUID, but the
 * resident exists as data whether or not its entity is loaded.
 */
public final class Resident {
    /** R4.15: ages are in in-game days. */
    public static final int ELDER_AGE = 60;
    public static final int MAX_AGE_MIN = 90;
    public static final int MAX_AGE_MAX = 110;
    /** A resident brought back by a cure has at least this many days left, however long they were a zombie. */
    static final int CURE_GRACE_DAYS = 20;
    static final int ADULT_AGE_MIN = 12;
    static final int ADULT_AGE_MAX = 51;

    private final UUID id;
    private String givenName;
    private String familyName;
    private final Gender gender;
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

    public Resident(UUID id, String givenName, String familyName, Gender gender, Traits traits,
                    Occupation occupation, boolean adult, long bornDay, UUID parentA, UUID parentB, Needs needs) {
        this.id = id;
        this.givenName = givenName;
        this.familyName = familyName;
        this.gender = gender;
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

    public Gender gender() {
        return gender;
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

    /** R4.3: a vanilla profession fills in a missing occupation but never replaces one the resident has. */
    public void seedOccupation(Occupation seed) {
        if (occupation == Occupation.UNEMPLOYED) {
            occupation = seed;
        }
    }

    /** Age in days on {@code day}, never negative (a clock that went backwards, R1.23). */
    public long age(long day) {
        return Math.max(0, day - bornDay);
    }

    /** Children follow the villager (vanilla decides when they grow up); adults become elders with age. */
    public LifeStage stage(long day) {
        if (!adult) {
            return LifeStage.CHILD;
        }
        return age(day) >= ELDER_AGE ? LifeStage.ELDER : LifeStage.ADULT;
    }

    /**
     * The age, in days, at which this resident dies of old age, between MAX_AGE_MIN and MAX_AGE_MAX.
     * Derived from their name, not their id, so it survives a cure (which gives them a new entity id).
     */
    public int maxAge() {
        return MAX_AGE_MIN + Math.floorMod((givenName + "|" + familyName).hashCode(), MAX_AGE_MAX - MAX_AGE_MIN + 1);
    }

    /** A plausible age for an adult who was already grown when first seen: young enough never to arrive as an elder. */
    static int adultAgeFrom(Random random) {
        return ADULT_AGE_MIN + random.nextInt(ADULT_AGE_MAX - ADULT_AGE_MIN + 1);
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
    Resident withId(UUID newId, long today) {
        // Years spent as a zombie shouldn't mean they die the moment they are cured (R4.15).
        long rebased = Math.max(bornDay, today - (maxAge() - CURE_GRACE_DAYS));
        Resident copy = new Resident(newId, givenName, familyName, gender, traits, occupation, adult, rebased,
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
