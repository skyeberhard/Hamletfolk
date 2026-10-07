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
    /**
     * R4.15: ages are in in-game days, and these are the values at a lifespan scale of 1. Use
     * {@link #elderAge()} and the other accessors, which apply the scale.
     */
    public static final int ELDER_AGE = 60;
    public static final int MAX_AGE_MIN = 90;
    public static final int MAX_AGE_MAX = 110;
    /** A resident brought back by a cure has at least this many days left, however long they were a zombie. */
    static final int CURE_GRACE_DAYS = 20;
    static final int ADULT_AGE_MIN = 12;
    static final int ADULT_AGE_MAX = 51;

    /**
     * How much longer than the base numbers a life lasts. One value for every resident, set once at
     * startup before any save is loaded (a settlement's ages are only meaningful against it), so it
     * is global; tests that change it must put it back.
     */
    private static volatile double lifespanScale = 1.0;

    public static void setLifespanScale(double scale) {
        lifespanScale = Math.max(0.1, scale);
    }

    public static double lifespanScale() {
        return lifespanScale;
    }

    private static int scaled(int days) {
        return (int) Math.round(days * lifespanScale);
    }

    /** The age at which an adult becomes an elder. */
    public static int elderAge() {
        return scaled(ELDER_AGE);
    }

    static int adultAgeMin() {
        return scaled(ADULT_AGE_MIN);
    }

    static int adultAgeMax() {
        return scaled(ADULT_AGE_MAX);
    }

    static int cureGraceDays() {
        return scaled(CURE_GRACE_DAYS);
    }

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
    /** R3.5: what they have put by, in hundredths of an emerald. */
    private int wealth;
    /** R4.21: buildings and upgrades this resident has finished as a builder. */
    private int built;

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

    /** R3.5: what this resident has put by, in hundredths of an emerald. */
    public int wealth() {
        return wealth;
    }

    /** R4.21: buildings and upgrades this resident has finished as a builder. */
    public int built() {
        return built;
    }

    void setBuilt(int built) {
        this.built = Math.max(0, built);
    }

    /** R3.5: adds to what they have put by (never below nothing, never above {@link Wealth#MAX}). */
    void addWealth(int hundredths) {
        wealth = (int) Math.max(0, Math.min(Wealth.MAX, (long) wealth + hundredths));
    }

    /** R3.5: pays for something out of what they have put by, as far as it goes; returns what was paid. */
    int spendWealth(int hundredths) {
        int paid = Math.min(wealth, Math.max(0, hundredths));
        wealth -= paid;
        return paid;
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
        return age(day) >= elderAge() ? LifeStage.ELDER : LifeStage.ADULT;
    }

    /**
     * The age, in days, at which this resident dies of old age, between MAX_AGE_MIN and MAX_AGE_MAX.
     * Derived from their name, not their id, so it survives a cure (which gives them a new entity id).
     */
    public int maxAge() {
        int min = scaled(MAX_AGE_MIN);
        return min + Math.floorMod((givenName + "|" + familyName).hashCode(), scaled(MAX_AGE_MAX) - min + 1);
    }

    /** A plausible age for an adult who was already grown when first seen: young enough never to arrive as an elder. */
    static int adultAgeFrom(Random random) {
        return adultAgeMin() + random.nextInt(adultAgeMax() - adultAgeMin() + 1);
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
        long rebased = Math.max(bornDay, today - (maxAge() - cureGraceDays()));
        Resident copy = new Resident(newId, givenName, familyName, gender, traits, occupation, adult, rebased,
                parentA, parentB, new Needs(needs.food(), needs.safety(), needs.purpose()));
        copy.lastBlockedDay = lastBlockedDay;
        copy.wealth = wealth; // R3.5: a cured villager keeps what they had put by
        copy.built = built; // R4.21: and their skill
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
