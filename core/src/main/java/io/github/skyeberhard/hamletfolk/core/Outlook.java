package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * R1.42: what a village's numbers mean over the days to come. For each resource: what it holds, what it aims for, what it makes and
 * uses each day (the last {@link ResourceFlow#WINDOW_DAYS} days of the village's own accounts, {@link ResourceFlow}), and from those how
 * many days until it runs out, or until it is back to its aim; and for the people, whether the village can grow, is held back, or is
 * heading for a famine. An estimate from the recent past, not a prediction: a building to be paid for or a donation changes it. Pure
 * and read-only: it changes nothing, and is worked out again from the saved accounts each time.
 */
public final class Outlook {
    /** Days a runway is worth a warning: beyond this a falling stock is "falling slowly". */
    public static final int WARN_DAYS = 30;
    /** A famine within this many days is called out for the people. */
    public static final int FAMINE_WARN_DAYS = 14;
    /** A net change smaller than this a day (of whatever it is) counts as steady. */
    static final double STEADY = 0.05;

    /** The resources shown, in order. */
    static final ResourceType[] SHOWN = {ResourceType.FOOD, ResourceType.WOOD, ResourceType.STONE, ResourceType.METAL, ResourceType.TOOLS, ResourceType.FUEL};

    private Outlook() {
    }

    /**
     * One resource: its stock, aim, daily making and using and the net, the days until it is gone ({@code daysLeft}, -1 if it is not
     * falling) or back to its aim ({@code daysToAim}, -1 if not recovering), and a sentence.
     */
    public record Line(String resource, int stock, int wanted, double produced, double consumed, double perDay, int daysLeft, int daysToAim,
            String verdict) {
    }

    /** What the people can expect: a headline and the reasons behind it. */
    public record People(String verdict, List<String> reasons) {
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** The lines for a village on {@code day}. */
    public static List<Line> lines(Settlement s, long day) {
        int window = s.flowDays();
        List<Line> out = new ArrayList<>();
        for (ResourceType type : SHOWN) {
            int stock = s.ledger().get(type);
            int wanted = type == ResourceType.FOOD ? s.population() * SettlementSimulator.foodAim(s)
                    : type == ResourceType.FUEL ? 0 : SettlementSimulator.wantedLevel(s, type);
            double produced = s.flow().produced(type, day) / (double) window;
            double consumed = s.flow().consumed(type, day) / (double) window;
            double net = produced - consumed;
            int daysLeft = -1;
            int daysToAim = -1;
            String verdict;
            String name = type.name().toLowerCase(Locale.ROOT);
            if (net < -STEADY) {
                daysLeft = stock <= 0 ? 0 : (int) Math.min(999, Math.ceil(stock / -net));
                verdict = stock <= 0 ? "none left and still being used" : daysLeft <= WARN_DAYS ? "runs out in about " + daysLeft + (daysLeft == 1 ? " day" : " days")
                        : "falling slowly";
            } else if (net > STEADY) {
                if (stock < wanted) {
                    daysToAim = (int) Math.min(999, Math.ceil((wanted - stock) / net));
                    verdict = "recovering: back to its aim in about " + daysToAim + (daysToAim == 1 ? " day" : " days");
                } else {
                    verdict = "growing";
                }
            } else if (stock <= 0 && wanted > 0) {
                verdict = "none, and none coming in";
            } else if (stock < wanted) {
                verdict = "short of its aim, and not recovering";
            } else {
                verdict = "steady";
            }
            out.add(new Line(name, stock, wanted, round1(produced), round1(consumed), round1(net), daysLeft, daysToAim, verdict));
        }
        return out;
    }

    /** Whether the village can grow, is held back, or is heading for a famine, from its beds, its food and the food's runway. */
    public static People people(Settlement s, long day, List<Line> lines) {
        List<String> reasons = new ArrayList<>();
        Line food = lines.get(0);
        if (s.population() == 0) {
            return new People("empty", List.of("nobody lives here"));
        }
        if (s.hasCondition("famine")) {
            return new People("in famine", List.of("the food has run out and people are going hungry"));
        }
        if (food.daysLeft >= 0 && food.daysLeft <= FAMINE_WARN_DAYS) {
            reasons.add("food falls " + Math.abs(food.perDay) + " a day and runs out in about " + food.daysLeft + (food.daysLeft == 1 ? " day" : " days"));
            return new People("heading for famine", reasons);
        }
        if (s.freeBeds() <= 0) {
            reasons.add("every bed is taken, so no newcomer can settle and no child can be born");
            return new People("stalled", reasons);
        }
        if (food.stock < food.wanted) {
            reasons.add("food " + food.stock + " of the " + food.wanted + " a newcomer needs"
                    + (food.daysToAim >= 0 ? ", back to it in about " + food.daysToAim + " days" : ", and not recovering"));
            return new People("held back", reasons);
        }
        reasons.add(s.freeBeds() + (s.freeBeds() == 1 ? " bed is" : " beds are") + " free and food is above its aim");
        return new People("can grow", reasons);
    }

    /** The outlook as plain data for the dashboard. */
    public static Map<String, Object> asData(Settlement s, long day) {
        List<Line> lines = lines(s, day);
        People people = people(s, day, lines);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("windowDays", s.flowDays());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Line l : lines) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resource", l.resource());
            row.put("stock", l.stock());
            row.put("wanted", l.wanted());
            row.put("produced", l.produced());
            row.put("consumed", l.consumed());
            row.put("perDay", l.perDay());
            row.put("daysLeft", l.daysLeft());
            row.put("daysToAim", l.daysToAim());
            row.put("verdict", l.verdict());
            rows.add(row);
        }
        out.put("lines", rows);
        Map<String, Object> folk = new LinkedHashMap<>();
        folk.put("verdict", people.verdict());
        folk.put("reasons", people.reasons());
        out.put("people", folk);
        return out;
    }
}
