package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Builds what a resident says from the actual simulation state. The most pressing
 * concern wins; otherwise they make small talk drawn from their job, personality and
 * their settlement's history.
 */
public final class Dialogue {
    private Dialogue() {
    }

    public static String greeting(Resident resident, UUID playerId, String playerName) {
        int times = resident.familiarityWith(playerId);
        if (times == 0) {
            return resident.traits().sociability() >= 50
                    ? "Hello there, stranger. I'm " + resident.givenName() + "."
                    : "...Can I help you?";
        }
        if (times < 5) {
            return "Oh, it's you again, " + playerName + ".";
        }
        return "Good to see you, " + playerName + "!";
    }

    public static String speak(Resident resident, Settlement settlement, long day, Random random) {
        return urgentConcern(resident, settlement, day).orElseGet(() -> smallTalk(resident, settlement, day, random));
    }

    static Optional<String> urgentConcern(Resident resident, Settlement settlement, long day) {
        if (settlement.hasCondition("famine")) {
            return Optional.of("There's no food left in " + settlement.name() + ". We're rationing what little we find.");
        }
        ResourceType input = resident.occupation().consumes();
        if (input != null && resident.lastBlockedDay() >= day - 1) {
            return Optional.of("I haven't worked in days. There's no " + input.name().toLowerCase(Locale.ROOT)
                    + " anywhere in " + settlement.name() + ".");
        }
        Optional<HistoryEvent> loss = settlement.latest(HistoryEvent.Kind.DEATH, day - 7);
        if (loss.isPresent()) {
            return Optional.of("Did you hear? " + loss.get().text() + " It's been hard on everyone.");
        }
        if (settlement.threat() >= 50) {
            return Optional.of(resident.traits().bravery() >= 60
                    ? "Something's hunting us. If it comes back, I'll be ready."
                    : "I don't go out after dark anymore. Not after what's happened.");
        }
        return Optional.empty();
    }

    static String smallTalk(Resident resident, Settlement settlement, long day, Random random) {
        List<String> options = new ArrayList<>();
        Traits traits = resident.traits();
        Occupation occupation = resident.occupation();

        if (!resident.adult()) {
            options.add("When I grow up I want to be the best " + randomTrade(random) + " in " + settlement.name() + "!");
        } else if (occupation == Occupation.UNEMPLOYED) {
            options.add("There's not enough work around here. I might have to move on.");
        } else if (occupation == Occupation.NITWIT) {
            options.add("Work? No, no. I'm more of a thinker.");
        } else {
            options.add("I've been the " + occupation.title() + " here for " + (day - resident.bornDay()) + " days now.");
            if (traits.ambition() >= 65) {
                options.add("One day I'll run the biggest " + occupation.title() + "'s shop in the land.");
            }
            if (traits.workEthic() < 35) {
                options.add("Work can wait. It's a lovely day.");
            }
        }

        int food = settlement.ledger().get(ResourceType.FOOD);
        if (food < settlement.population() * 3) {
            options.add("Stores are running thin. A few more farmers wouldn't hurt.");
        } else if (settlement.hasCondition("surplus")) {
            options.add("We've more food than we can eat. Good years don't last forever, though.");
        }

        if (traits.sociability() >= 65 && settlement.population() > 1) {
            options.add("There are " + settlement.population() + " of us in " + settlement.name()
                    + " now. I know every one of them by name.");
        }

        List<HistoryEvent> history = settlement.history();
        List<HistoryEvent> memorable = history.stream()
                .filter(e -> e.kind() == HistoryEvent.Kind.RAID || e.kind() == HistoryEvent.Kind.FAMINE
                        || e.kind() == HistoryEvent.Kind.DONATION || e.kind() == HistoryEvent.Kind.CURE)
                .filter(e -> day - e.day() > 7)
                .toList();
        if (!memorable.isEmpty()) {
            HistoryEvent event = memorable.get(random.nextInt(memorable.size()));
            options.add("I still think about day " + event.day() + ". " + event.text());
        } else {
            options.add(settlement.name() + " has stood for " + (day - settlement.foundedDay()) + " days, as far as anyone's written down.");
        }

        return options.get(random.nextInt(options.size()));
    }

    private static String randomTrade(Random random) {
        Occupation[] trades = {Occupation.FARMER, Occupation.MASON, Occupation.TOOLSMITH, Occupation.LIBRARIAN,
                Occupation.FISHERMAN, Occupation.CARTOGRAPHER};
        return trades[random.nextInt(trades.length)].title();
    }
}
