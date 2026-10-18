package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * R4.58: a master (level 4 or more, still working at the trade) trains a pupil. The pupil is their own child or, failing that, the least
 * experienced adult of the same trade who has no master in the family; for as long as the master lives and works the pupil earns a
 * day's experience in two days over (half as fast again), until they are an expert themselves. A master's child who takes up the
 * parent's trade starts with {@link #HEAD_START} days of experience. Nothing is saved: the pairing is worked out each day from the
 * trades and the parents, and only a marker ({@code apprentice:<id>}, the day) stops the history saying so twice.
 */
public final class Apprenticeships {
    /** Days of experience a master's child brings to the parent's trade (level 2). */
    static final int HEAD_START = 10;
    static final String MARKER = "apprentice:";

    private Apprenticeships() {
    }

    static boolean teaches(Resident r) {
        Occupation job = r.occupation();
        return r.adult() && r.level() >= TradeLevel.EXPERT && job.produces() != null && job != Occupation.GUARD && job != Occupation.BUILDER;
    }

    /** Whether a resident of this trade is still learning (an adult, not yet an expert). */
    static boolean learning(Resident r) {
        return r.adult() && r.level() < TradeLevel.EXPERT && r.occupation().produces() != null
                && r.occupation() != Occupation.GUARD && r.occupation() != Occupation.BUILDER;
    }

    /** The master teaching this pupil, a parent first, or null. {@code taken} holds masters already teaching an outsider. */
    private static Resident masterOf(Settlement s, Resident pupil, List<Resident> masters) {
        for (Resident m : masters) {
            if (m.occupation() == pupil.occupation() && (m.id().equals(pupil.parentA()) || m.id().equals(pupil.parentB()))) {
                return m;
            }
        }
        return null;
    }

    /** Each learner's experience before the day's work, so the bonus goes only to those who worked; empty if the village has no master. */
    static java.util.Map<UUID, Integer> snapshot(Settlement s) {
        java.util.Map<UUID, Integer> before = new java.util.HashMap<>();
        boolean master = false;
        for (Resident r : s.residents()) {
            master |= teaches(r);
            if (learning(r)) {
                before.put(r.id(), r.xp());
            }
        }
        if (!master) {
            before.clear();
        }
        return before;
    }

    static void run(Settlement s, long day) {
        run(s, day, null);
    }

    /**
     * Runs the day (after the work): gives each pupil a master's extra experience on every second day, and notes new pupils in the
     * history. With {@code before} (see {@link #snapshot}), only a pupil whose experience rose today, that is one who worked, is taught.
     */
    static void run(Settlement s, long day, java.util.Map<UUID, Integer> before) {
        List<Resident> masters = null;
        for (Resident r : s.residents()) {
            if (teaches(r)) {
                if (masters == null) {
                    masters = new ArrayList<>();
                }
                masters.add(r);
            }
        }
        if (masters == null) {
            return;
        }
        masters.sort(Comparator.comparing(Resident::id));
        Set<UUID> busy = new HashSet<>(); // masters with a pupil of the family
        List<Resident> outsiders = new ArrayList<>();
        for (Resident r : s.residents()) {
            if (!learning(r) || s.isPinned(r.id()) || before != null && r.xp() <= before.getOrDefault(r.id(), Integer.MAX_VALUE)) {
                continue;
            }
            Resident master = masterOf(s, r, masters);
            if (master != null) {
                busy.add(master.id());
                teach(s, master, r, day);
            } else {
                outsiders.add(r);
            }
        }
        outsiders.sort(Comparator.comparingInt(Resident::xp).thenComparing(Resident::id));
        Set<UUID> taught = new HashSet<>();
        for (Resident master : masters) {
            if (busy.contains(master.id())) {
                continue;
            }
            for (Resident pupil : outsiders) {
                if (pupil.occupation() == master.occupation() && taught.add(pupil.id())) {
                    teach(s, master, pupil, day);
                    break;
                }
            }
        }
    }

    private static void teach(Settlement s, Resident master, Resident pupil, long day) {
        if (day % 2 == 0) {
            pupil.addXp(1);
        }
        if (!s.hasCondition(MARKER + pupil.id())) {
            s.noteApprentice(pupil.id(), day);
            s.record(day, HistoryEvent.Kind.MILESTONE, pupil.fullName() + " is learning the " + pupil.occupation().title().toLowerCase(java.util.Locale.ROOT)
                    + "'s trade from " + master.fullName() + ".");
        }
    }

    /** The experience a child brings to a trade taken up from a parent who is a master of it. */
    static int headStart(Settlement s, Resident child, Occupation trade) {
        if (child.xpTrade() == trade && child.xpRaw() >= HEAD_START) {
            return 0; // they already have more than that in this trade
        }
        for (UUID parent : new UUID[] {child.parentA(), child.parentB()}) {
            Resident p = parent == null ? null : s.resident(parent).orElse(null);
            if (p != null && p.occupation() == trade && teaches(p)) {
                return HEAD_START;
            }
        }
        return 0;
    }
}
