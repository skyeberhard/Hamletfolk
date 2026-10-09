package io.github.skyeberhard.hamletfolk.core;

import java.util.Random;
import java.util.UUID;

/**
 * R4.30: how a villager sounds. Worked out each time from who they are and never stored: a tone from their traits (with a
 * little that is theirs alone, drawn from their id) shifted by mood, age, trade level and the village's temperament, and one
 * habit of speech from their id. The same villager in the same state always has the same voice.
 */
public record Voice(Tone tone, Quirk quirk) {

    /** One stable habit of speech: something said before or after a line now and then. */
    public enum Quirk {
        HM("Ahem. ", ""), AYE("Aye. ", ""), WELL("So. ", ""), LISTEN("Listen. ", ""), LOOK("Look. ", ""),
        MARK("", " Mark my words."), SO_THEY_SAY("", " Or so they say."), THAT_IS_HOW("", " That's how it is."),
        HEH("", " Heh."), MIND_YOU("", " Mind you."), WEATHER("", " Such is life."), ANYWAY("", " Anyway.");

        private final String prefix;
        private final String suffix;

        Quirk(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }

        /** The line with this habit worked in. */
        public String apply(String line) {
            return prefix + line + suffix;
        }
    }

    /**
     * Added to each tone's score, in {@link Tone} order, so that villagers with the usual spread of traits come out about
     * one in six for each tone (without it the blunt and the warm crowd out the dry and the gentle).
     */
    private static final double[] BIAS = {-5.3, -7.3, 2.7, -1.9, 8.0, 3.7};

    /** Chance (percent) that a line carries the speaker's habit. */
    static final int QUIRK_PERCENT = 45;

    /** The voice of a resident today. {@code settlement} may be null (no village temperament then). */
    public static Voice of(Resident resident, Settlement settlement, long day) {
        Traits t = resident.traits();
        UUID id = resident.id();
        Random jitter = new Random(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17));
        double[] score = new double[Tone.values().length];
        score[Tone.WARM.ordinal()] = t.sociability();
        score[Tone.GRUFF.ordinal()] = (100 - t.sociability()) * 0.6 + t.bravery() * 0.5;
        score[Tone.ANXIOUS.ordinal()] = (100 - t.bravery()) * 0.9;
        score[Tone.PROUD.ordinal()] = t.ambition() * 0.8 + t.bravery() * 0.2;
        score[Tone.DRY.ordinal()] = (100 - t.workEthic()) * 0.5 + (100 - t.sociability()) * 0.4;
        score[Tone.GENTLE.ordinal()] = (100 - t.ambition()) * 0.4 + (100 - t.bravery()) * 0.3 + t.sociability() * 0.3;
        int best = 0;
        for (int i = 0; i < score.length; i++) {
            score[i] += BIAS[i] + jitter.nextInt(15); // a little that is theirs alone
            if (score[i] > score[best]) {
                best = i;
            }
        }
        Tone tone = Tone.values()[best];

        if (!resident.adult()) {
            tone = tone == Tone.ANXIOUS ? Tone.ANXIOUS : Tone.WARM; // children are excitable
        } else if (resident.level() >= TradeLevel.MASTER && tone != Tone.ANXIOUS) {
            tone = Tone.PROUD; // a master speaks with authority (an expert, one below, keeps their own tone)
        }
        int mood = resident.needs().mood();
        if (mood < 35) { // a starving cheerful one is subdued
            tone = switch (tone) {
                case WARM -> Tone.GENTLE;
                case PROUD -> Tone.GRUFF;
                case GENTLE -> Tone.ANXIOUS;
                default -> tone;
            };
        } else if (mood >= 80) { // a gloomy one with a full larder lightens
            tone = switch (tone) {
                case ANXIOUS -> Tone.GENTLE;
                case GRUFF -> Tone.DRY;
                default -> tone;
            };
        }
        if (settlement != null) {
            switch (VillageCharacter.temperament(settlement)) {
                case WARY -> tone = tone == Tone.WARM ? Tone.DRY : tone;
                case MARTIAL -> tone = tone == Tone.GENTLE ? Tone.GRUFF : tone;
                case WELCOMING -> tone = tone == Tone.DRY ? Tone.WARM : tone;
                default -> {
                }
            }
        }
        Quirk[] quirks = Quirk.values();
        Quirk quirk = quirks[(int) Math.floorMod(id.getLeastSignificantBits() ^ (id.getMostSignificantBits() >>> 7), (long) quirks.length)];
        return new Voice(tone, quirk);
    }

    /** The line with the speaker's habit worked in, now and then. */
    public String flavour(String line, Random random) {
        return random.nextInt(100) < QUIRK_PERCENT ? quirk.apply(line) : line;
    }
}
