package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;

/**
 * R8.11: a village's size (its stage) and temperament, and the little each does. Temperament is read from the village's
 * last {@link #WINDOW_DAYS} days of history and its state each time it is asked, never stored: a village that has come
 * through its famines becomes steady again once they are two months behind it. The leaning (what the land makes it) is stored with the settlement; see {@link Leaning}.
 */
public final class VillageCharacter {
    private VillageCharacter() {
    }

    /** How big a village is. */
    public enum Stage {
        HAMLET("hamlet", 0), VILLAGE("village", 8), TOWN("town", 20), CITY("city", 50);

        private final String label;
        private final int from;

        Stage(String label, int from) {
            this.label = label;
            this.from = from;
        }

        public String label() {
            return label;
        }

        /** The population it begins at. */
        public int from() {
            return from;
        }

        public static Stage of(int population) {
            Stage found = HAMLET;
            for (Stage stage : values()) {
                if (population >= stage.from) {
                    found = stage;
                }
            }
            return found;
        }
    }

    /** What a village's history has made it. */
    public enum Temperament {
        HARD_PRESSED("hard-pressed"), MARTIAL("martial"), WARY("wary"), PROSPEROUS("prosperous"), WELCOMING("welcoming"),
        STEADY("steady");

        private final String label;

        Temperament(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Days between two wanted upgrades: four for the prosperous, fourteen for the hard-pressed, a week otherwise. */
        public int upgradeEveryDays() {
            return switch (this) {
                case PROSPEROUS -> 4;
                case HARD_PRESSED -> 14;
                default -> Construction.UPGRADE_EVERY_DAYS;
            };
        }

        /** Days between two newcomers: two for the welcoming, six for the wary, three otherwise. */
        public int newcomerCooldownDays() {
            return switch (this) {
                case WELCOMING -> 2;
                case WARY -> 6;
                default -> SettlementSimulator.NEWCOMER_COOLDOWN_DAYS;
            };
        }

        /** Iron golems a village of this temperament keeps beyond the usual: one more for the martial. */
        public int extraGolems() {
            return this == MARTIAL ? 1 : 0;
        }
    }

    /** How far back a village's history counts toward its temperament, in days. */
    public static final int WINDOW_DAYS = 60;
    /** Days on which a village must have been attacked to be martial. */
    static final int MARTIAL_ATTACK_DAYS = 3;
    /** Famines that make a village hard-pressed (a famine going on now does at once). */
    static final int HARD_FAMINES = 2;
    /**
     * Emeralds banked, and food a head, before a village is prosperous. The food is below what a merchant leaves in the
     * stores (24 a head), so selling surplus does not flip a village in and out of prosperity.
     */
    static final int PROSPEROUS_EMERALDS = 100;
    static final int PROSPEROUS_FOOD_PER_HEAD = SettlementSimulator.NEWCOMER_FOOD_PER_HEAD;
    static final int WELCOMING_ARRIVALS = 3;

    /**
     * The village's temperament now, from the last {@link #WINDOW_DAYS} days: the first that fits, in the order
     * hard-pressed, martial, wary, prosperous, welcoming.
     */
    public static Temperament temperament(Settlement settlement) {
        long since = settlement.lastSimulatedDay() - WINDOW_DAYS;
        long famines = settlement.history().stream().filter(e -> e.kind() == HistoryEvent.Kind.FAMINE && e.day() >= since).count();
        if (settlement.hasCondition("famine") || famines >= HARD_FAMINES) {
            return Temperament.HARD_PRESSED;
        }
        if (settlement.incidentDaysSince(since) >= MARTIAL_ATTACK_DAYS) {
            return Temperament.MARTIAL;
        }
        if (settlement.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.RAID && e.day() >= since
                && e.text().contains("overran"))) {
            return Temperament.WARY;
        }
        if (SettlementSimulator.banked(settlement) >= PROSPEROUS_EMERALDS && settlement.population() > 0
                && settlement.ledger().get(ResourceType.FOOD) >= PROSPEROUS_FOOD_PER_HEAD * settlement.population()) {
            return Temperament.PROSPEROUS;
        }
        long arrivals = settlement.history().stream().filter(e -> e.kind() == HistoryEvent.Kind.ARRIVAL && e.day() >= since).count();
        return arrivals >= WELCOMING_ARRIVALS ? Temperament.WELCOMING : Temperament.STEADY;
    }

    /** "a prosperous timber town", the village in a phrase. */
    public static String describe(Settlement settlement) {
        String temper = temperament(settlement).label();
        Leaning leaning = settlement.leaning();
        String kind = leaning == Leaning.ALL_ROUND ? "" : leaning.label() + " ";
        return article(temper) + " " + temper + " " + kind + Stage.of(settlement.population()).label();
    }

    private static String article(String word) {
        return "aeiou".indexOf(word.toLowerCase(Locale.ROOT).charAt(0)) >= 0 ? "an" : "a";
    }
}
