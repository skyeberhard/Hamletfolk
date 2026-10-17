package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * R6.5: trade between villages. A village with more of something than it keeps sends it, by caravan, to a neighbour that is short of it, and the
 * neighbour sends back what it can spare that the first lacks: the units of a category go from one ledger to the other commodity by commodity,
 * valued at the fair price (what the village's own merchants get), and only the difference is paid, in emeralds, from the debtor's treasury. Both
 * histories say so. Two villages trade at most once every {@link #COOLDOWN_DAYS} days, only in the same world, within a range the server sets
 * ({@link #DEFAULT_RANGE} blocks unless configured). A merchant living in either village keeps the load whole; without one, the carriers keep
 * a fifth of it on the road ({@link #CARRIER_SHARE_PERCENT}), so merchants make trade worth more. A neighbour in a famine is sent food whether it can
 * pay or not (relief), if the sender can spare it.
 *
 * <p>Pure rules: the Paper layer calls {@link #run} once for each village it has just simulated, so nothing here costs anything in the daily loop.
 * Nothing is saved apart from the days of the last trades, kept in the villages' conditions.
 */
public final class Exchange {
    /** Most blocks between two village centres for a caravan to go between them, unless the server sets another. */
    public static final int DEFAULT_RANGE = 800;
    /** Village days between two trades of the same pair. */
    public static final int COOLDOWN_DAYS = 5;
    /** Most units of one category a caravan carries each way. */
    public static final int CARAVAN_UNITS = 60;
    /** The share of a load the carriers keep on the road when no merchant lives in either village. */
    public static final int CARRIER_SHARE_PERCENT = 20;
    /** The condition prefix that holds the day of the last trade with another village. */
    static final String TRADED = "exchangeAt:";

    private Exchange() {
    }

    /**
     * One load.
     *
     * @param units    what the sender gives up
     * @param received what reaches the neighbour (all of it with a merchant, four fifths without)
     * @param worth    what the load is worth at the fair price, in emeralds (0 for relief)
     */
    public record Shipment(Settlement from, Settlement to, ResourceType type, int units, int received, int worth, boolean relief) {
    }

    /** What a caravan does between two villages: the loads each way and the emeralds that change hands to settle them. */
    public record Plan(Settlement a, Settlement b, List<Shipment> loads, Settlement payer, int cash) {
    }

    /** True if a caravan can go between the two: both alive, the same world, in range, and not traded lately. */
    public static boolean linked(Settlement a, Settlement b, long day, int range) {
        if (a == b || a.id().equals(b.id()) || a.isAbandoned() || b.isAbandoned() || a.population() == 0 || b.population() == 0
                || !a.world().equals(b.world())) {
            return false;
        }
        long dx = a.centerX() - b.centerX();
        long dz = a.centerZ() - b.centerZ();
        if (dx * dx + dz * dz > (long) range * range) {
            return false;
        }
        Long last = a.conditions().get(TRADED + b.id());
        return last == null || day - last >= COOLDOWN_DAYS;
    }

    static boolean hasMerchant(Settlement s) {
        for (Resident r : s.residents()) {
            if (r.adult() && r.occupation() == Occupation.MERCHANT) {
                return true;
            }
        }
        return false;
    }

    /** The share of a load that reaches the neighbour: all with a merchant in either village, else what the carriers leave. */
    static int received(int units, boolean merchant) {
        return merchant ? units : units * (100 - CARRIER_SHARE_PERCENT) / 100;
    }

    /**
     * What a caravan would carry between two villages today: the category each is most short of that the other can spare, and the emeralds that settle
     * the difference (as many as the debtor has and the creditor's treasury can bank; a load is cut back if they cannot cover it). Does not change anything.
     */
    public static Plan plan(Settlement a, Settlement b, ToIntFunction<Settlement> treasuryRoom) {
        boolean merchant = hasMerchant(a) || hasMerchant(b);
        Shipment there = best(a, b, merchant);
        Shipment back = best(b, a, merchant);
        int aToB = there == null ? 0 : there.worth();
        int bToA = back == null ? 0 : back.worth();
        int owedByB = aToB - bToA; // what b owes a, if positive
        Settlement payer = owedByB > 0 ? b : a;
        Settlement payee = payer == b ? a : b;
        int owed = Math.abs(owedByB);
        int cash = Math.min(owed, Math.min(payer.ledger().treasury(), treasuryRoom.applyAsInt(payee)));
        if (cash < owed) { // cut back the load that is worth more until the difference can be paid
            if (owedByB > 0) {
                there = cut(there, there.worth() - (owed - cash), merchant);
            } else {
                back = cut(back, back.worth() - (owed - cash), merchant);
            }
        }
        List<Shipment> loads = new ArrayList<>();
        if (there != null) {
            loads.add(there);
        }
        if (back != null) {
            loads.add(back);
        }
        aToB = there == null ? 0 : there.worth();
        bToA = back == null ? 0 : back.worth();
        payer = aToB - bToA > 0 ? b : a;
        int paid = loads.isEmpty() ? 0 : Math.min(Math.abs(aToB - bToA), cash);
        return new Plan(a, b, loads, payer, paid);
    }

    /** The load cut back to be worth {@code worth} emeralds, or null if that is less than one emerald's worth of the category. */
    private static Shipment cut(Shipment s, int worth, boolean merchant) {
        if (s == null) {
            return null;
        }
        if (s.relief()) {
            return s;
        }
        if (worth <= 0) {
            return null;
        }
        int upe = SettlementSimulator.unitsPerEmerald(s.type());
        int received = Math.min(s.received(), worth * upe);
        if (received < upe) {
            return null;
        }
        int units = merchant ? received : (int) Math.ceil(received * 100.0 / (100 - CARRIER_SHARE_PERCENT));
        units = Math.min(units, s.units());
        int arrive = received(units, merchant);
        return new Shipment(s.from(), s.to(), s.type(), units, arrive, arrive / upe, false);
    }

    /** The best load from {@code from} to {@code to}: the category the neighbour is most short of that the sender can spare, ignoring money. */
    private static Shipment best(Settlement from, Settlement to, boolean merchant) {
        Shipment best = null;
        int bestDeficit = 0;
        boolean famine = to.hasCondition("famine");
        for (ResourceType type : ResourceType.values()) {
            int upe = SettlementSimulator.unitsPerEmerald(type);
            int deficit = SettlementSimulator.wantedLevel(to, type) - to.ledger().get(type);
            int spare = SettlementSimulator.surplus(from, type);
            int fits = SettlementSimulator.room(to, type);
            int want = Math.max(0, Math.min(deficit, fits));
            int units = Math.min(Math.min(spare, CARAVAN_UNITS), merchant ? want : (int) Math.ceil(want * 100.0 / (100 - CARRIER_SHARE_PERCENT)));
            int received = received(units, merchant);
            if (received < upe || deficit <= bestDeficit) {
                continue;
            }
            boolean relief = famine && type == ResourceType.FOOD;
            best = new Shipment(from, to, type, units, received, relief ? 0 : received / upe, relief);
            bestDeficit = deficit;
        }
        return best;
    }

    /** Carries a plan out: units out of one ledger and into the other commodity by commodity, then the difference in emeralds. */
    static void deliver(Plan plan) {
        for (Shipment s : plan.loads()) {
            int left = s.units();
            int arrive = s.received();
            for (Commodity c : Commodity.of(s.type())) {
                if (left == 0) {
                    break;
                }
                int taken = s.from().ledger().take(c, left);
                left -= taken;
                int delivered = Math.min(taken, arrive);
                arrive -= delivered;
                s.to().ledger().add(c, delivered);
            }
        }
        if (plan.cash() > 0) {
            Settlement payee = plan.payer() == plan.a() ? plan.b() : plan.a();
            if (plan.payer().ledger().spendTreasury(plan.cash())) {
                payee.ledger().addTreasury(plan.cash());
            }
        }
    }

    /**
     * Has the village that was just simulated trade with the neighbours it is linked to, one visit with each, then notes the day. Call once for each
     * village per simulation pass. Returns what was carried.
     *
     * @param day          the day of the villages' world (both are in it, so the rest between trades is kept on one clock, whatever each village's own is)
     * @param treasuryRoom how many more emeralds a village's treasury can bank (R2.6)
     * @param range        most blocks between two villages
     * @param active       whether a village is being simulated (its world loaded and in scope): one that is not is left alone, as initiator and as neighbour
     */
    public static List<Plan> run(SettlementRegistry registry, Settlement settlement, long day, ToIntFunction<Settlement> treasuryRoom, int range,
            java.util.function.Predicate<Settlement> active) {
        List<Plan> made = new ArrayList<>();
        if (settlement.isAbandoned() || settlement.population() == 0 || !active.test(settlement)) {
            return made;
        }
        List<Settlement> neighbours = new ArrayList<>(registry.settlements());
        neighbours.sort(Comparator.comparingLong((Settlement n) -> {
            long dx = n.centerX() - settlement.centerX();
            long dz = n.centerZ() - settlement.centerZ();
            return dx * dx + dz * dz;
        }).thenComparing(Settlement::id));
        for (Settlement other : neighbours) {
            if (!linked(settlement, other, day, range) || !active.test(other)) {
                continue;
            }
            Plan plan = plan(settlement, other, treasuryRoom);
            if (plan.loads().isEmpty()) {
                continue;
            }
            deliver(plan);
            made.add(plan);
            settlement.conditions().put(TRADED + other.id(), day);
            other.conditions().put(TRADED + settlement.id(), day);
            record(plan);
        }
        return made;
    }

    /** One line in each village's history for the visit (filed under its own day, so a village whose clock differs is not given a date from another's). */
    private static void record(Plan plan) {
        for (Settlement village : new Settlement[] {plan.a(), plan.b()}) {
            Settlement other = village == plan.a() ? plan.b() : plan.a();
            List<String> parts = new ArrayList<>();
            for (Shipment s : plan.loads()) {
                String what = (s.from() == village ? s.units() : s.received()) + " " + s.type().name().toLowerCase(Locale.ROOT);
                String worth = s.relief() ? " as relief" : "";
                parts.add((s.from() == village ? "sent " : "brought ") + what + worth);
            }
            String money = plan.cash() == 0 ? "" : plan.payer() == village ? ", and paid " + plan.cash() + " emeralds to settle" : ", and was paid " + plan.cash() + " emeralds to settle";
            village.record(village.lastSimulatedDay(), HistoryEvent.Kind.MILESTONE,
                    "A caravan went between " + village.name() + " and " + other.name() + ": " + String.join("; ", parts) + money + ".");
        }
    }

    /** Why two villages are not trading, in words for an admin: distance, merchants, and what each lacks or could spare. */
    public static String explain(Settlement a, Settlement b, int range) {
        long dx = a.centerX() - b.centerX();
        long dz = a.centerZ() - b.centerZ();
        long blocks = Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        StringBuilder out = new StringBuilder(a.name()).append(" and ").append(b.name()).append(" are ").append(blocks).append(" blocks apart");
        out.append(blocks > range ? " (out of reach: " + range + " at most)" : "").append("; ")
                .append(hasMerchant(a) || hasMerchant(b) ? "a merchant keeps the loads whole" : "no merchant, so the carriers keep a fifth").append(". ");
        for (Settlement[] pair : new Settlement[][] {{a, b}, {b, a}}) {
            List<String> parts = new ArrayList<>();
            for (ResourceType type : ResourceType.values()) {
                int spare = SettlementSimulator.surplus(pair[0], type);
                int deficit = SettlementSimulator.wantedLevel(pair[1], type) - pair[1].ledger().get(type);
                if (spare > 0 && deficit > 0) {
                    parts.add(type.name().toLowerCase(Locale.ROOT) + " (spare " + spare + ", wanted " + deficit + ")");
                }
            }
            out.append(pair[0].name()).append(" to ").append(pair[1].name()).append(": ").append(parts.isEmpty() ? "nothing spare that the other lacks" : String.join(", ", parts)).append(". ");
        }
        out.append("Treasuries: ").append(a.name()).append(" ").append(a.ledger().treasury()).append(", ").append(b.name()).append(" ").append(b.ledger().treasury()).append(".");
        return out.toString();
    }

    /** Forgets when the village last traded with each neighbour (an admin's test of a caravan). */
    public static void reset(Settlement settlement, SettlementRegistry registry) {
        settlement.conditions().keySet().removeIf(k -> k.startsWith(TRADED));
        for (Settlement other : registry.settlements()) {
            other.conditions().remove(TRADED + settlement.id());
        }
    }

    /** The nearest neighbour a caravan could reach, for a report. */
    public static Optional<Settlement> nearest(SettlementRegistry registry, Settlement settlement, int range) {
        Settlement best = null;
        long bestSquared = Long.MAX_VALUE;
        for (Settlement n : registry.settlements()) {
            if (n == settlement || !n.world().equals(settlement.world()) || n.isAbandoned() || n.population() == 0) {
                continue;
            }
            long dx = n.centerX() - settlement.centerX();
            long dz = n.centerZ() - settlement.centerZ();
            long d = dx * dx + dz * dz;
            if (d <= (long) range * range && d < bestSquared) {
                bestSquared = d;
                best = n;
            }
        }
        return Optional.ofNullable(best);
    }
}
