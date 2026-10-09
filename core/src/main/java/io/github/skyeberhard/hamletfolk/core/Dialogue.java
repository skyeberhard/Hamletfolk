package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Builds what a resident says from the actual simulation state. The most pressing
 * concern wins; otherwise they make small talk drawn from their job, personality and
 * their settlement's history. R4.30: what is said is chosen here, how it sounds comes from the resident's
 * {@link Voice} and the {@link Lines} library.
 */
public final class Dialogue {
    private static Lines library = Lines.builtIn();

    private Dialogue() {
    }

    /** R4.30: the lines in use (the built-in set unless the Paper layer has laid a server's folder over it). */
    public static Lines library() {
        return library;
    }

    /** Replaces the library; null puts the built-in lines back. A test that changes it must restore it. */
    public static void setLibrary(Lines lines) {
        library = lines == null ? Lines.builtIn() : lines;
    }

    /** Something to say: a situation with the facts for its slots, and the plain wording to use if there are no lines for it. */
    private record Option(Situation situation, Map<String, String> slots, String plain) {
        static Option plain(String text) {
            return new Option(null, Map.of(), text);
        }

        static Option of(Situation situation, String plain, String... slotPairs) {
            Map<String, String> slots = new HashMap<>();
            for (int i = 0; i + 1 < slotPairs.length; i += 2) {
                slots.put(slotPairs[i], slotPairs[i + 1]);
            }
            return new Option(situation, slots, plain);
        }
    }

    /** R4.31: what the sky and the clock look like where the villager stands (Paper fills it in; null when unknown). */
    public record Ambient(String sky, String time) {
    }

    private static String render(Option option, Voice voice, Settlement settlement, Random random) {
        if (option.situation() == null) {
            // R4.31: a remark with no lines of its own still comes out in the speaker's tone, the fact unchanged
            return library.pick(Situation.REMARK, voice.tone(), Map.of("statement", option.plain()), random).orElse(option.plain());
        }
        Map<String, String> slots = new HashMap<>(option.slots());
        if (settlement != null) {
            slots.putIfAbsent("village", settlement.name());
        }
        return library.pick(option.situation(), voice.tone(), slots, random).orElse(option.plain());
    }

    public static String greeting(Resident resident, UUID playerId, String playerName) {
        int times = resident.familiarityWith(playerId);
        Situation situation = times == 0 ? Situation.GREETING_STRANGER : times < 5 ? Situation.GREETING_KNOWN : Situation.GREETING_FRIEND;
        return greet(resident, null, situation, playerId, playerName);
    }

    /**
     * R3.4: the greeting for a player the settlement has an opinion of. Strangers get the plain greeting;
     * those it dislikes or admires are greeted accordingly. R4.30: in the resident's own voice, the same one each time
     * (the line is drawn from the resident and how often they have met, so it does not change between calls).
     */
    public static String greeting(Resident resident, Settlement settlement, UUID playerId, String playerName) {
        Situation situation = switch (Reputation.standing(settlement.reputationOf(playerId))) {
            case HOSTILE -> Situation.GREETING_HOSTILE;
            case WARY -> Situation.GREETING_WARY;
            case FRIENDLY -> Situation.GREETING_FRIENDLY;
            case HONOURED -> Situation.GREETING_HONOURED;
            case STRANGER -> {
                int times = resident.familiarityWith(playerId);
                yield times == 0 ? Situation.GREETING_STRANGER : times < 5 ? Situation.GREETING_KNOWN : Situation.GREETING_FRIEND;
            }
        };
        return greet(resident, settlement, situation, playerId, playerName);
    }

    private static String greet(Resident resident, Settlement settlement, Situation situation, UUID playerId, String playerName) {
        Voice voice = Voice.of(resident, settlement, 0);
        String plain = switch (situation) {
            case GREETING_STRANGER -> resident.traits().sociability() >= 50
                    ? "Hello there, stranger. I'm " + resident.givenName() + "." : "...Can I help you?";
            case GREETING_KNOWN -> "Oh, it's you again, " + playerName + ".";
            case GREETING_FRIEND -> "Good to see you, " + playerName + "!";
            default -> playerName + ".";
        };
        Option option = Option.of(situation, plain, "player", playerName, "name", resident.givenName());
        Random random = new Random(resident.id().getLeastSignificantBits() * 31 + resident.familiarityWith(playerId) + situation.ordinal());
        return render(option, voice, settlement, random);
    }

    /** R4.30: what a resident says as a player walks away, in their voice. */
    public static String farewell(Resident resident, Settlement settlement, String playerName, Random random) {
        return render(Option.of(Situation.FAREWELL, "Goodbye, " + playerName + ".", "player", playerName),
                Voice.of(resident, settlement, 0), settlement, random);
    }

    public static String speak(Resident resident, Settlement settlement, long day, Random random) {
        return speak(resident, settlement, day, random, null);
    }

    /** As above; with an {@link Ambient} they may remark on the weather and the time of day too (R4.31). */
    public static String speak(Resident resident, Settlement settlement, long day, Random random, Ambient ambient) {
        Voice voice = Voice.of(resident, settlement, day);
        String base = "";
        for (int attempt = 0; attempt < 8; attempt++) { // R4.30: never the same thing twice running
            base = urgentOption(resident, settlement, day).map(o -> render(o, voice, settlement, random))
                    .orElseGet(() -> smallTalk(resident, settlement, day, random, ambient));
            if (!base.equals(resident.lastLine())) {
                break;
            }
        }
        resident.setLastLine(base);
        return voice.flavour(base, random);
    }

    static Optional<String> urgentConcern(Resident resident, Settlement settlement, long day) {
        Random random = new Random(resident.id().getLeastSignificantBits() + day);
        return urgentOption(resident, settlement, day).map(o -> render(o, Voice.of(resident, settlement, day), settlement, random));
    }

    private static Optional<Option> urgentOption(Resident resident, Settlement settlement, long day) {
        if (settlement.hasCondition("famine")) {
            return Optional.of(Option.of(Situation.FAMINE,
                    "There's no food left in " + settlement.name() + ". We're rationing what little we find."));
        }
        ResourceType input = resident.occupation().consumes();
        if (input != null && resident.lastBlockedDay() >= day - 1) {
            String name = input.name().toLowerCase(Locale.ROOT);
            return Optional.of(Option.of(Situation.BLOCKED, "I haven't worked in days. There's no " + name + " anywhere in "
                    + settlement.name() + ".", "input", name));
        }
        if (resident.occupation().usesTools() && settlement.hasCondition("shortage:tools")) {
            return Optional.of(Option.of(Situation.TOOLS,
                    "Our tools are worn to nothing. Everything is slower in " + settlement.name() + "."));
        }
        Optional<HistoryEvent> loss = settlement.latest(HistoryEvent.Kind.DEATH, day - 7);
        if (loss.isPresent()) {
            return Optional.of(Option.of(Situation.LOSS, "Did you hear? " + loss.get().text() + " It's been hard on everyone.",
                    "text", loss.get().text()));
        }
        if (settlement.threat() >= 50) {
            return Optional.of(Option.of(Situation.DANGER, resident.traits().bravery() >= 60
                    ? "Something's hunting us. If it comes back, I'll be ready."
                    : "I don't go out after dark anymore. Not after what's happened."));
        }
        return Optional.empty();
    }

    /** R8.11: what residents say about the kind of place the village is: its land, its history and its size. */
    static List<String> characterLines(Settlement settlement) {
        String name = settlement.name();
        List<String> lines = new ArrayList<>();
        switch (settlement.leaning()) {
            case TIMBER -> lines.add("We live by the forest here. Most of what stands in " + name + " began as a tree.");
            case MINING -> lines.add("" + name + " is a mining place at heart. The hills give us stone and ore, and we give them back sweat.");
            case FARMING -> lines.add("Good, open farmland. The fields are what " + name + " is about.");
            case FISHING -> lines.add("The water is our living in " + name + ". We were fishers before we were anything else.");
            case PASTORAL -> lines.add("Sheep and cattle graze all round " + name + ". It's herders' country.");
            case SCHOLARLY -> lines.add("Reed beds and cattle: paper and leather, so " + name + " has always had maps and books.");
            case CRAFT -> lines.add("There is sand for the glass all round " + name + ", and a glassblower can make anything of it.");
            case TRADING -> lines.add("A bit of everything grows or grazes near " + name + ", so there is always something to trade.");
            default -> {
            }
        }
        switch (VillageCharacter.temperament(settlement)) {
            case MARTIAL -> lines.add("We've come through enough raids in " + name + " to know how to hold the line.");
            case HARD_PRESSED -> lines.add("These are lean years. Nobody in " + name + " plans far ahead any more.");
            case WARY -> lines.add("Since the raiders overran " + name + ", we watch every stranger on the road.");
            case PROSPEROUS -> lines.add("Full stores and a heavy treasury. " + name + " has never done better.");
            case WELCOMING -> lines.add("Folk keep arriving in " + name + ". There's always room for one more.");
            case STEADY -> {
            }
        }
        switch (VillageCharacter.Stage.of(settlement.population())) {
            case HAMLET -> lines.add(name + " is a small place, but we know every face in it.");
            case TOWN -> lines.add("There are too many of us to know by name now. " + name + " is a town.");
            case CITY -> lines.add(name + " is a city now! I still get lost in the lanes.");
            case VILLAGE -> {
            }
        }
        return lines;
    }

    static String smallTalk(Resident resident, Settlement settlement, long day, Random random) {
        return smallTalk(resident, settlement, day, random, null);
    }

    static String smallTalk(Resident resident, Settlement settlement, long day, Random random, Ambient ambient) {
        List<Option> options = new ArrayList<>();
        Traits traits = resident.traits();
        Occupation occupation = resident.occupation();

        if (!resident.adult()) {
            String trade = randomTrade(random);
            options.add(Option.of(Situation.CHILD, "When I grow up I want to be the best " + trade + " in " + settlement.name() + "!",
                    "trade", trade));
        } else if (occupation == Occupation.UNEMPLOYED) {
            options.add(Option.plain("There's not enough work around here. I might have to move on."));
            options.add(Option.plain("No trade of my own yet, so I forage what I can along the edges of the fields. It keeps "
                    + "the larder from running bare while I wait for work."));
        } else if (occupation == Occupation.NITWIT) {
            options.add(Option.plain("Work? No, no. I'm more of a thinker."));
        } else if (occupation == Occupation.BUILDER) {
            options.add(Option.plain("There's a building to put up in " + settlement.name() + ". One block at a time, that's how it gets done."));
            if (settlement.openProject().filter(p -> p.waitingFor() != null).isPresent()) {
                options.add(Option.plain("I'm waiting on " + settlement.openProject().get().waitingFor().name().toLowerCase(java.util.Locale.ROOT)
                        + " before I can lay another block."));
            }
        } else if (occupation == Occupation.GUARD) {
            options.add(Option.plain("Someone has to watch the road. It might as well be me."));
            if (settlement.ledger().get(ResourceType.TOOLS) == 0) {
                options.add(Option.plain("I'm standing watch with nothing in my hands. " + settlement.name() + " needs tools."));
            }
            if (settlement.threat() < 10) {
                options.add(Option.plain("It's been quiet. I don't mind the quiet."));
            }
        } else {
            options.add(Option.plain("I'm " + resident.age(day) + " days old, and the " + occupation.title() + "'s trade is all I know."));
            if (occupation == Occupation.MERCHANT) {
                options.add(Option.plain("A spare plank or a block of stone is just clutter until someone buys it. I make sure someone does."));
            }
            if (resident.stage(day) == LifeStage.ELDER) {
                options.add(Option.of(Situation.ELDER, "I've lived " + resident.age(day) + " days, and my knees tell me I won't see many more.",
                        "days", String.valueOf(resident.age(day))));
            }
            if (traits.ambition() >= 65) {
                options.add(Option.plain("One day I'll run the biggest " + occupation.title() + "'s shop in the land."));
            }
            if (traits.workEthic() < 35) {
                options.add(Option.plain("Work can wait. It's a lovely day."));
            }
        }

        parentLine(resident, settlement).ifPresent(l -> options.add(Option.plain(l)));
        if (resident.adult()) {
            workOption(resident, settlement).ifPresent(options::add); // R4.31
        }
        if (ambient != null) {
            options.add(Option.of(Situation.WEATHER, "It's " + ambient.time() + ", and " + ambient.sky() + ".",
                    "sky", ambient.sky(), "time", ambient.time()));
        }
        if (resident.adult()) {
            characterLines(settlement).forEach(l -> options.add(Option.plain(l))); // R8.11
            neighbour(resident, settlement, random).ifPresent(options::add); // R4.30
        }
        if (resident.adult() && !settlement.decisions().isEmpty()) {
            Planner.Decision latest = settlement.decisions().get(settlement.decisions().size() - 1);
            if (day - latest.day() <= 14) {
                options.add(Option.of(Situation.TALK, "There's talk in " + settlement.name() + ". " + latest.text(), "text", latest.text()));
            }
        }
        if (resident.adult() && occupation != Occupation.NITWIT) {
            wealthLine(resident, occupation).ifPresent(l -> options.add(Option.plain(l)));
        }

        settlement.requests().stream().findFirst().ifPresent(request -> options.add(Option.of(Situation.REQUEST,
                "We're short of " + request.type().name().toLowerCase(Locale.ROOT) + ". " + settlement.name() + " will pay "
                        + request.unpaid() + " emeralds for " + request.remaining() + " more, if a traveller can spare some.",
                "item", request.type().name().toLowerCase(Locale.ROOT), "pay", String.valueOf(request.unpaid()),
                "left", String.valueOf(request.remaining()))));

        int food = settlement.ledger().get(ResourceType.FOOD);
        if (food < settlement.population() * 3) {
            options.add(Option.of(Situation.THIN, "Stores are running thin. A few more farmers wouldn't hurt."));
        } else if (settlement.hasCondition("surplus")) {
            options.add(Option.of(Situation.PLENTY, "We've more food than we can eat. Good years don't last forever, though."));
        }

        if (traits.sociability() >= 65 && settlement.population() > 1) {
            options.add(Option.plain("There are " + settlement.population() + " of us in " + settlement.name()
                    + " now. I know every one of them by name."));
        }

        List<HistoryEvent> history = settlement.history();
        List<HistoryEvent> memorable = history.stream()
                .filter(e -> e.kind() == HistoryEvent.Kind.RAID || e.kind() == HistoryEvent.Kind.FAMINE
                        || e.kind() == HistoryEvent.Kind.DONATION || e.kind() == HistoryEvent.Kind.CURE)
                .filter(e -> day - e.day() > 7)
                .toList();
        if (!memorable.isEmpty()) {
            HistoryEvent event = memorable.get(random.nextInt(memorable.size()));
            Situation kind = event.kind() == HistoryEvent.Kind.RAID ? Situation.MEMORY_RAID
                    : event.kind() == HistoryEvent.Kind.DONATION ? Situation.MEMORY_GIFT : Situation.MEMORY;
            options.add(Option.of(kind, "I still think about day " + event.day() + ". " + event.text(),
                    "day", String.valueOf(event.day()), "text", event.text()));
        } else {
            options.add(Option.plain(settlement.name() + " has stood for " + (day - settlement.foundedDay())
                    + " days, as far as anyone's written down."));
        }

        return render(options.get(random.nextInt(options.size())), Voice.of(resident, settlement, day), settlement, random);
    }

    /** R4.31: talk about their own trade in the trade's words, and more of it once they are an expert or a master. */
    private static Optional<Option> workOption(Resident resident, Settlement settlement) {
        Map<String, String> words = library.vocab("trade", resident.occupation().name());
        if (words.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> slots = new HashMap<>(words);
        slots.put("days", String.valueOf(resident.xp()));
        Situation situation = resident.level() >= 4 ? Situation.WORK_EXPERT : Situation.WORK;
        return Optional.of(new Option(situation, slots, "I work at " + words.getOrDefault("craft", "my trade") + " in " + settlement.name() + "."));
    }

    /** R4.30: a word about a neighbour, by name and trade: another grown resident with a trade. */
    private static Optional<Option> neighbour(Resident resident, Settlement settlement, Random random) {
        List<Resident> others = settlement.residents().stream()
                .filter(r -> !r.id().equals(resident.id()) && r.adult()
                        && r.occupation() != Occupation.UNEMPLOYED && r.occupation() != Occupation.NITWIT)
                .sorted(java.util.Comparator.comparing(Resident::fullName)) // a stable order, so a seeded draw is repeatable
                .toList();
        if (others.isEmpty()) {
            return Optional.empty();
        }
        Resident other = others.get(random.nextInt(others.size()));
        return Optional.of(Option.of(Situation.NEIGHBOUR, "Have you met " + other.fullName() + "? The " + other.occupation().title() + ".",
                "neighbour", other.fullName(), "trade", other.occupation().title()));
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
