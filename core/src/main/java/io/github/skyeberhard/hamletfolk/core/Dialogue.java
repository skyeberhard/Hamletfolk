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

    /**
     * R3.4: the greeting for a player the settlement has an opinion of. Strangers get the plain greeting;
     * those it dislikes or admires are greeted accordingly.
     */
    public static String greeting(Resident resident, Settlement settlement, UUID playerId, String playerName) {
        return switch (Reputation.standing(settlement.reputationOf(playerId))) {
            case HOSTILE -> "You. " + settlement.name() + " has not forgotten what you did, " + playerName + ".";
            case WARY -> "Oh. " + playerName + ". I'm watching you.";
            case FRIENDLY -> "Good to see you, " + playerName + ". " + settlement.name() + " remembers your kindness.";
            case HONOURED -> "Welcome, " + playerName + "! Everyone in " + settlement.name() + " speaks well of you.";
            case STRANGER -> greeting(resident, playerId, playerName);
        };
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
        if (resident.occupation().usesTools() && settlement.hasCondition("shortage:tools")) {
            return Optional.of("Our tools are worn to nothing. Everything is slower in " + settlement.name() + ".");
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
            options.add("No trade of my own yet, so I forage what I can along the edges of the fields. It keeps "
                    + "the larder from running bare while I wait for work.");
        } else if (occupation == Occupation.NITWIT) {
            options.add("Work? No, no. I'm more of a thinker.");
        } else if (occupation == Occupation.BUILDER) {
            options.add("There's a building to put up in " + settlement.name() + ". One block at a time, that's how it gets done.");
            if (settlement.openProject().filter(p -> p.waitingFor() != null).isPresent()) {
                options.add("I'm waiting on " + settlement.openProject().get().waitingFor().name().toLowerCase(java.util.Locale.ROOT)
                        + " before I can lay another block.");
            }
        } else if (occupation == Occupation.GUARD) {
            options.add("Someone has to watch the road. It might as well be me.");
            if (settlement.ledger().get(ResourceType.TOOLS) == 0) {
                options.add("I'm standing watch with nothing in my hands. " + settlement.name() + " needs tools.");
            }
            if (settlement.threat() < 10) {
                options.add("It's been quiet. I don't mind the quiet.");
            }
        } else {
            options.add("I'm " + resident.age(day) + " days old, and the " + occupation.title() + "'s trade is all I know.");
            if (occupation == Occupation.MERCHANT) {
                options.add("A spare plank or a block of stone is just clutter until someone buys it. I make sure someone does.");
            }
            if (resident.stage(day) == LifeStage.ELDER) {
                options.add("I've lived " + resident.age(day) + " days, and my knees tell me I won't see many more.");
            }
            if (traits.ambition() >= 65) {
                options.add("One day I'll run the biggest " + occupation.title() + "'s shop in the land.");
            }
            if (traits.workEthic() < 35) {
                options.add("Work can wait. It's a lovely day.");
            }
        }

        parentLine(resident, settlement).ifPresent(options::add);
        if (resident.adult() && !settlement.decisions().isEmpty()) {
            Planner.Decision latest = settlement.decisions().get(settlement.decisions().size() - 1);
            if (day - latest.day() <= 14) {
                options.add("There's talk in " + settlement.name() + ". " + latest.text());
            }
        }
        if (resident.adult() && occupation != Occupation.NITWIT) {
            wealthLine(resident, occupation).ifPresent(options::add);
        }

        settlement.requests().stream().findFirst().ifPresent(request -> options.add("We're short of "
                + request.type().name().toLowerCase(Locale.ROOT) + ". " + settlement.name() + " will pay "
                + request.unpaid() + " emeralds for " + request.remaining()
                + " more, if a traveller can spare some."));

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

    /** R3.5: what a resident says about money, by how much they have put by. */
    static Optional<String> wealthLine(Resident resident, Occupation occupation) {
        return Optional.of(switch (Wealth.tier(resident.wealth())) {
            case BROKE -> occupation == Occupation.UNEMPLOYED
                    ? "I haven't a coin to my name. Work would fix that."
                    : "Every emerald I earn goes on food. There's nothing put by.";
            case MODEST -> "I get by. A few emeralds put away for a bad winter.";
            case COMFORTABLE -> "I've done all right: about " + resident.wealth() / Wealth.PER_EMERALD
                    + " emeralds put by, and a roof over my head.";
            case WEALTHY -> "Business has been good to me. I could buy half this street, if anyone were selling.";
        });
    }

    /** R4.14: a line about a parent still in the settlement, in the words their gender calls for. */
    private static Optional<String> parentLine(Resident resident, Settlement settlement) {
        for (UUID id : new UUID[] {resident.parentA(), resident.parentB()}) {
            Optional<Resident> parent = id == null ? Optional.empty() : settlement.resident(id);
            if (parent.isPresent()) {
                Gender gender = parent.get().gender();
                String word = switch (gender) {
                    case FEMALE -> "mother";
                    case MALE -> "father";
                };
                return Optional.of("My " + word + ", " + parent.get().givenName() + ", raised me here. Everyone says I got my laugh from "
                        + gender.object() + ".");
            }
        }
        return Optional.empty();
    }

    private static String randomTrade(Random random) {
        Occupation[] trades = {Occupation.FARMER, Occupation.MASON, Occupation.TOOLSMITH, Occupation.LIBRARIAN,
                Occupation.FISHERMAN, Occupation.CARTOGRAPHER, Occupation.LUMBERJACK};
        return trades[random.nextInt(trades.length)].title();
    }
}
