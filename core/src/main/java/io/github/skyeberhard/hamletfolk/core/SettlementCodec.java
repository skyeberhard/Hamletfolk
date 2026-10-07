package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Converts settlements to and from plain maps, lists, strings and numbers, so any
 * serializer (Gson, YAML, NBT) can store them. Numbers are read through {@link Number}
 * because serializers disagree on whether 3 comes back as an int, long or double.
 */
public final class SettlementCodec {
    // 1: initial format. 2: added "turned" (R1.2, zombie villagers awaiting a cure).
    // 3: history events may carry "count" and "actor" (R1.21, merged donations).
    // 4: added "flow" (R3.7, 7-day produced/consumed totals).
    public static final int FORMAT_VERSION = 22;

    private SettlementCodec() {
    }

    public static Map<String, Object> encode(Settlement s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("format", FORMAT_VERSION);
        map.put("id", s.id().toString());
        map.put("name", s.name());
        map.put("world", s.world());
        map.put("centerX", s.centerX());
        map.put("centerZ", s.centerZ());
        map.put("foundedDay", s.foundedDay());
        map.put("lastSimulatedDay", s.lastSimulatedDay());
        map.put("threat", s.threat());

        Map<String, Object> stock = new LinkedHashMap<>();
        s.ledger().stock().forEach((type, amount) -> stock.put(type.name(), amount));
        map.put("stock", stock);
        map.put("treasury", s.ledger().treasury());

        map.put("conditions", new LinkedHashMap<>(s.conditions()));

        Map<String, Object> flow = new LinkedHashMap<>();
        s.flow().days().forEach((type, byDay) -> {
            Map<String, Object> rows = new LinkedHashMap<>();
            byDay.forEach((day, row) -> rows.put(day.toString(), List.of(row[0], row[1])));
            flow.put(type.name(), rows);
        });
        map.put("flow", flow);

        List<Object> residents = new ArrayList<>();
        for (Resident r : s.residents()) {
            residents.add(encodeResident(r));
        }
        map.put("residents", residents);

        Map<String, Object> turned = new LinkedHashMap<>();
        s.turned().forEach((zombieId, r) -> turned.put(zombieId.toString(), encodeResident(r)));
        map.put("turned", turned);

        List<Object> history = new ArrayList<>();
        for (HistoryEvent e : s.history()) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("day", e.day());
            event.put("kind", e.kind().name());
            event.put("text", e.text());
            if (e.count() != 1) {
                event.put("count", e.count());
            }
            if (e.actor() != null) {
                event.put("actor", e.actor());
            }
            history.add(event);
        }
        Map<String, Object> departed = new LinkedHashMap<>();
        s.departed().forEach((id, day) -> departed.put(id.toString(), day));
        map.put("departed", departed);
        List<Object> requests = new ArrayList<>();
        for (Request r : s.requests()) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("type", r.type().name());
            request.put("wanted", r.wanted());
            request.put("filled", r.filled());
            request.put("reward", r.reward());
            request.put("paid", r.paid());
            request.put("postedDay", r.postedDay());
            requests.add(request);
        }
        map.put("requests", requests);
        List<Object> buildings = new ArrayList<>();
        for (Building b : s.buildings()) {
            Map<String, Object> building = new LinkedHashMap<>();
            building.put("type", b.type().name());
            building.put("x", b.x());
            building.put("y", b.y());
            building.put("z", b.z());
            building.put("day", b.registeredDay());
            building.put("by", b.registeredBy());
            buildings.add(building);
        }
        map.put("buildings", buildings);
        if (!s.projects().isEmpty()) {
            List<Object> projects = new ArrayList<>();
            for (ConstructionProject p : s.projects()) {
                Map<String, Object> project = new LinkedHashMap<>();
                project.put("id", p.id());
                project.put("type", p.type().name());
                project.put("tier", p.tier());
                project.put("previousTier", p.previousTier());
                project.put("biome", p.biomeSet());
                project.put("x", p.x());
                project.put("y", p.y());
                project.put("z", p.z());
                project.put("lot", p.lotId());
                project.put("queuedDay", p.queuedDay());
                project.put("status", p.status().name());
                if (p.builder() != null) {
                    project.put("builder", p.builder().toString());
                }
                project.put("finishedDay", p.finishedDay());
                project.put("siteChecked", p.siteChecked());
                project.put("turns", p.turns());
                project.put("oldTurns", p.oldTurns());
                project.put("graded", p.graded());
                project.put("shiftX", p.shiftX());
                project.put("shiftZ", p.shiftZ());
                if (p.signY() != ConstructionProject.NO_SIGN) {
                    project.put("sign", List.of(p.signX(), p.signY(), p.signZ()));
                }
                Map<String, Object> credit = new LinkedHashMap<>();
                p.credit().forEach((type, halves) -> credit.put(type.name(), halves));
                project.put("credit", credit);
                projects.add(project);
            }
            map.put("projects", projects);
        }
        map.put("beds", new LinkedHashMap<>(s.housing().asMap()));
        Map<String, Object> reputation = new LinkedHashMap<>();
        s.reputation().forEach((player, score) -> reputation.put(player.toString(), score));
        map.put("reputation", reputation);
        if (!s.incidents().isEmpty()) {
            map.put("incidents", new ArrayList<>(s.incidents()));
        }
        if (s.plan() != null) {
            map.put("plan", s.plan().toMap());
        }
        if (!s.decisions().isEmpty()) {
            List<Object> decisions = new ArrayList<>();
            for (Planner.Decision d : s.decisions()) {
                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("day", d.day());
                decision.put("tier", d.tier().name());
                decision.put("key", d.key());
                decision.put("text", d.text());
                decisions.add(decision);
            }
            map.put("decisions", decisions);
        }
        map.put("history", history);
        return map;
    }

    private static Map<String, Object> encodeResident(Resident r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.id().toString());
        map.put("givenName", r.givenName());
        map.put("familyName", r.familyName());
        map.put("gender", r.gender().name());
        map.put("occupation", r.occupation().name());
        map.put("adult", r.adult());
        map.put("bornDay", r.bornDay());
        if (r.parentA() != null) {
            map.put("parentA", r.parentA().toString());
        }
        if (r.parentB() != null) {
            map.put("parentB", r.parentB().toString());
        }
        map.put("lastBlockedDay", r.lastBlockedDay());
        if (r.wealth() != 0) {
            map.put("wealth", r.wealth());
        }
        if (r.built() != 0) {
            map.put("built", r.built());
        }
        Traits t = r.traits();
        map.put("traits", List.of(t.workEthic(), t.sociability(), t.ambition(), t.bravery()));
        Needs n = r.needs();
        map.put("needs", List.of(n.food(), n.safety(), n.purpose()));
        Map<String, Object> familiarity = new LinkedHashMap<>();
        r.familiarity().forEach((player, count) -> familiarity.put(player.toString(), count));
        map.put("familiarity", familiarity);
        return map;
    }

    public static Settlement decode(Map<?, ?> raw) {
        Map<?, ?> map = migrate(raw);
        Settlement s = new Settlement(
                UUID.fromString(str(map, "id")),
                str(map, "name"),
                str(map, "world"),
                num(map, "centerX").intValue(),
                num(map, "centerZ").intValue(),
                num(map, "foundedDay").longValue());
        s.setLastSimulatedDay(num(map, "lastSimulatedDay").longValue());
        s.setThreat(num(map, "threat").doubleValue());

        for (Map.Entry<?, ?> entry : asMap(map.get("stock")).entrySet()) {
            s.ledger().add(ResourceType.valueOf(entry.getKey().toString()), ((Number) entry.getValue()).intValue());
        }
        s.ledger().setTreasury(num(map, "treasury").intValue());

        for (Map.Entry<?, ?> entry : asMap(map.get("conditions")).entrySet()) {
            s.conditions().put(entry.getKey().toString(), ((Number) entry.getValue()).longValue());
        }

        for (Map.Entry<?, ?> entry : asMap(map.get("flow")).entrySet()) {
            ResourceType type = ResourceType.valueOf(entry.getKey().toString());
            for (Map.Entry<?, ?> row : asMap(entry.getValue()).entrySet()) {
                List<?> amounts = asList(row.getValue());
                long day = Long.parseLong(row.getKey().toString());
                s.flow().recordProduced(type, day, intAt(amounts, 0));
                s.flow().recordConsumed(type, day, intAt(amounts, 1));
            }
        }

        for (Object o : asList(map.get("residents"))) {
            s.addResident(decodeResident(asMap(o)));
        }

        for (Map.Entry<?, ?> entry : asMap(map.get("turned")).entrySet()) {
            s.turned().put(UUID.fromString(entry.getKey().toString()), decodeResident(asMap(entry.getValue())));
        }

        // v8 -> v9: "buildings" (R2.1) is optional, so an old save simply has none registered.
        for (Object o : asList(map.get("buildings"))) {
            Map<?, ?> b = asMap(o);
            s.addBuilding(new Building(BuildingType.valueOf(str(b, "type")), num(b, "x").intValue(),
                    num(b, "y").intValue(), num(b, "z").intValue(), num(b, "day").longValue(), str(b, "by")));
        }

        // v10 -> v11: "reputation" (R3.4) is optional, so an old save has no opinions: everyone is a stranger.
        for (Map.Entry<?, ?> entry : asMap(map.get("reputation")).entrySet()) {
            if (entry.getValue() instanceof Number score && Reputation.clamp(score.intValue()) != 0) {
                s.reputation().put(UUID.fromString(entry.getKey().toString()), Reputation.clamp(score.intValue()));
            }
        }

        // v9 -> v10: "beds" (R2.2) is optional, so an old save has no housing until its chunks are next loaded.
        for (Map.Entry<?, ?> entry : asMap(map.get("beds")).entrySet()) {
            if (entry.getValue() instanceof Number count) {
                s.housing().load(entry.getKey().toString(), count.intValue());
            }
        }

        // v7 -> v8: "requests" (R3.3) is optional, so an old save simply has none open.
        for (Object o : asList(map.get("requests"))) {
            Map<?, ?> r = asMap(o);
            ResourceType type = ResourceType.valueOf(str(r, "type"));
            // A damaged file must not mint or destroy emeralds: keep the numbers consistent, and if the
            // same resource appears twice, give the earlier one's unpaid reward back to the treasury.
            int wanted = Math.max(1, num(r, "wanted").intValue());
            int filled = Math.max(0, Math.min(wanted, num(r, "filled").intValue()));
            int reward = Math.max(0, num(r, "reward").intValue());
            int paid = Math.max(0, Math.min(reward, num(r, "paid").intValue()));
            Request earlier = s.requestMap().put(type,
                    new Request(type, wanted, filled, reward, paid, num(r, "postedDay").longValue()));
            if (earlier != null) {
                s.ledger().addTreasury(earlier.unpaid());
            }
        }

        for (Map.Entry<?, ?> entry : asMap(map.get("departed")).entrySet()) {
            s.departed().put(UUID.fromString(entry.getKey().toString()), ((Number) entry.getValue()).longValue());
        }

        for (Object o : asList(map.get("incidents"))) {
            if (o instanceof Number day) {
                s.recordIncident(day.longValue()); // the oldest go first if a hand-edited save holds too many, as when recording
            }
        }

        // v19 -> v20: projects gained "turns", "oldTurns" and "graded" (the way a building is turned, and whether its ground is
        // levelled); all optional, so a v19 project is unturned and ungraded. An older build must refuse a save with turned projects.
        // v18 -> v19: "projects" (R4.7, R4.8) is optional, so an old save has none.
        for (Object o : asList(map.get("projects"))) {
            try {
                Map<?, ?> p = asMap(o);
                for (String required : new String[] {"id", "tier", "x", "y", "z", "lot", "queuedDay"}) {
                    if (!(p.get(required) instanceof Number)) {
                        throw new IllegalArgumentException("project without " + required);
                    }
                }
                ConstructionProject project = new ConstructionProject(num(p, "id").intValue(),
                        BuildingType.valueOf(str(p, "type")), Math.max(1, num(p, "tier").intValue()),
                        Math.max(0, num(p, "previousTier").intValue()), str(p, "biome"), num(p, "x").intValue(),
                        num(p, "y").intValue(), num(p, "z").intValue(), num(p, "lot").intValue(), num(p, "queuedDay").longValue());
                UUID builder = p.get("builder") == null ? null : UUID.fromString(p.get("builder").toString());
                ConstructionProject.Status status = ConstructionProject.Status.valueOf(str(p, "status"));
                // An active project needs its builder; without one it goes back in the queue.
                if (status == ConstructionProject.Status.ACTIVE && builder == null) {
                    status = ConstructionProject.Status.QUEUED;
                }
                // The village builds one thing at a time: a hand-edited second open project is dropped.
                if ((status == ConstructionProject.Status.ACTIVE || status == ConstructionProject.Status.QUEUED)
                        && s.openProject().isPresent()) {
                    status = ConstructionProject.Status.CANCELLED;
                    builder = null;
                }
                project.setTurns(p.get("turns") instanceof Number t ? t.intValue() : 0);
                project.setOldTurns(p.get("oldTurns") instanceof Number t ? t.intValue() : 0);
                project.setGraded(Boolean.TRUE.equals(p.get("graded")));
                project.setShift(p.get("shiftX") instanceof Number sx ? sx.intValue() : 0,
                        p.get("shiftZ") instanceof Number sz ? sz.intValue() : 0);
                project.restore(status, builder, num(p, "finishedDay").longValue(), Boolean.TRUE.equals(p.get("siteChecked")));
                if (p.get("sign") instanceof List<?> sign && sign.size() == 3) {
                    project.setSign(((Number) sign.get(0)).intValue(), ((Number) sign.get(1)).intValue(), ((Number) sign.get(2)).intValue());
                }
                for (Map.Entry<?, ?> credit : asMap(p.get("credit")).entrySet()) {
                    project.credit().put(ResourceType.valueOf(credit.getKey().toString()),
                            Math.max(0, ((Number) credit.getValue()).intValue()));
                }
                s.addProject(project);
            } catch (RuntimeException e) {
                // a damaged project is dropped: the village decides again what to build
            }
        }
        if (map.get("plan") instanceof Map<?, ?> plan) {
            try {
                s.setPlan(VillagePlan.fromMap(plan));
            } catch (IllegalArgumentException | ClassCastException e) {
                // a damaged plan is dropped: the ground is surveyed again and a new one made
            }
        }

        for (Object o : asList(map.get("decisions"))) {
            Map<?, ?> d = asMap(o);
            try {
                s.recordDecision(num(d, "day").longValue(), Planner.Tier.valueOf(str(d, "tier")),
                        d.containsKey("key") ? str(d, "key") : str(d, "text"), str(d, "text"), 0);
            } catch (IllegalArgumentException e) {
                // a damaged or unknown entry: skip it
            }
        }

        for (Object o : asList(map.get("history"))) {
            Map<?, ?> event = asMap(o);
            Object actor = event.get("actor");
            s.add(new HistoryEvent(num(event, "day").longValue(), HistoryEvent.Kind.valueOf(str(event, "kind")),
                    str(event, "text"), event.containsKey("count") ? num(event, "count").intValue() : 1,
                    actor == null ? null : actor.toString()));
        }
        return s;
    }

    /**
     * Brings an older save up to {@link #FORMAT_VERSION}, one step at a time. Refuses to load
     * a save written by a newer version of the plugin rather than silently misreading it.
     */
    private static Map<?, ?> migrate(Map<?, ?> raw) {
        int version = raw.containsKey("format") ? num(raw, "format").intValue() : 1;
        if (version > FORMAT_VERSION) {
            throw new IllegalArgumentException("settlements.json was written by a newer version of this plugin "
                    + "(format " + version + "; this build supports up to " + FORMAT_VERSION + "). "
                    + "Update the plugin before loading it, or restore an older copy from backups/.");
        }
        if (version >= FORMAT_VERSION) {
            return raw;
        }
        Map<String, Object> migrated = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            migrated.put(entry.getKey().toString(), entry.getValue());
        }
        if (version < 2) {
            // v1 -> v2: "turned" (R1.2) didn't exist yet; an old save has none.
            migrated.putIfAbsent("turned", new LinkedHashMap<>());
        }
        // v2 -> v3: "count" and "actor" on history events are optional; old events read as count 1, no actor.
        // v3 -> v4: "flow" (R3.7) is optional; an old save simply starts with no flow history.
        // v8 -> v9: "buildings" (R2.1) is optional, so an old save simply has none registered.
        // v9 -> v10: "beds" (R2.2) is optional, so an old save has no housing until its chunks are next loaded.
        // v10 -> v11: "reputation" (R3.4) is optional, so an old save has no opinions.
        // v11 -> v12: "wealth" (R3.5) on residents is optional, so an old save's residents start with nothing put by.
        // v12 -> v13: history gained the DEPARTURE kind (R4.2); an old save has none, so nothing to convert, but an
        // older build must refuse a save that may contain it rather than fail on an unknown kind.
        // v14 -> v15: occupations gained GUARD (R5.1); an old save has none, so nothing to convert, but an older
        // build must refuse a save that may contain it rather than fail on an unknown occupation.
        // v15 -> v16: "incidents" (R5.5, the days the village was attacked) is optional, so an old save has none.
        // v18 -> v19: "projects" (R4.7, R4.8, construction) is optional, so an old save has none; occupations gained BUILDER, so
        // an older build must refuse a save that may contain it.
        // v17 -> v18: "plan" (R8.3, the streets and lots) is optional, so an old save has none until the ground is surveyed.
        // v16 -> v17: "decisions" (R8.1, the planner's log) is optional, so an old save has none; the planner fills it in.
        // v21 -> v22: construction projects may be the works STREET_LIGHTS and PALISADE (R5.6, no lot, no sign); an older build
        // would fail on the unknown type, so it must refuse the save.
        // v20 -> v21: "built" (R4.21, the buildings a resident has finished) is optional, so an old save's builders start at 0.
        // v13 -> v14: the treasury got a limit (R2.6). So that no existing village loses emeralds, what it holds now (the
        // treasury and the rewards set aside for open requests) is kept as the room it may keep ("treasuryLegacy"); a
        // new village starts with just the base amount.
        if (version < 5) {
            // v4 -> v5: residents gain a gender (R4.14).
            migrated.put("residents", withGenders(migrated.get("residents")));
            Map<String, Object> turned = new LinkedHashMap<>();
            asMap(migrated.get("turned")).forEach((zombie, resident) ->
                    turned.put(zombie.toString(), withGender(asMap(resident))));
            migrated.put("turned", turned);
        }
        if (version < 6) {
            // v5 -> v6: ages matter now (R4.15). "departed" is optional, so an old save has none, but
            // residents whose birth day is the founding day would all be ancient: rebase them.
            long today = num(raw, "lastSimulatedDay").longValue();
            migrated.put("residents", withPlausibleAges(migrated.get("residents"), today));
            Map<String, Object> turned = new LinkedHashMap<>();
            asMap(migrated.get("turned")).forEach((zombie, resident) ->
                    turned.put(zombie.toString(), withPlausibleAge(asMap(resident), today)));
            migrated.put("turned", turned);
        }
        // v7 -> v8: "requests" (R3.3) is optional, so an old save simply has none open.
        if (version < 7) {
            // v6 -> v7: there are two genders (R4.14). A resident an earlier build saved as
            // nonbinary gets one from their name, or from their id if the name is unrecognised.
            List<Object> residents = new ArrayList<>();
            for (Object resident : asList(migrated.get("residents"))) {
                residents.add(withTwoGenders(asMap(resident)));
            }
            migrated.put("residents", residents);
            Map<String, Object> turned = new LinkedHashMap<>();
            asMap(migrated.get("turned")).forEach((zombie, resident) ->
                    turned.put(zombie.toString(), withTwoGenders(asMap(resident))));
            migrated.put("turned", turned);
        }
        if (version < 14) {
            long held = num(raw, "treasury").longValue();
            for (Object o : asList(raw.get("requests"))) {
                Map<?, ?> request = asMap(o);
                held += Math.max(0, num(request, "reward").longValue() - num(request, "paid").longValue());
            }
            if (held > 0) {
                Map<String, Object> conditions = new LinkedHashMap<>(asStringKeyed(asMap(migrated.get("conditions"))));
                conditions.put(SettlementSimulator.TREASURY_LEGACY, held);
                migrated.put("conditions", conditions);
            }
        }
        migrated.put("format", FORMAT_VERSION);
        return migrated;
    }

    private static Map<String, Object> withTwoGenders(Map<?, ?> resident) {
        if (!"NONBINARY".equals(String.valueOf(resident.get("gender")))) {
            return new LinkedHashMap<>(asStringKeyed(resident));
        }
        Map<String, Object> copy = new LinkedHashMap<>(asStringKeyed(resident));
        copy.remove("gender");
        return withGender(copy);
    }

    private static Map<String, Object> asStringKeyed(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(key.toString(), value));
        return copy;
    }

    private static List<Object> withPlausibleAges(Object residents, long today) {
        List<Object> out = new ArrayList<>();
        for (Object resident : asList(residents)) {
            out.add(withPlausibleAge(asMap(resident), today));
        }
        return out;
    }

    /**
     * A resident whose recorded age is beyond what a newly seen adult could be is given a plausible
     * one, derived from their id. Nobody is made older. The child flag is ignored: it is only
     * refreshed when a villager loads, so a recorded "child" that old has long since grown up.
     */
    private static Map<String, Object> withPlausibleAge(Map<?, ?> resident, long today) {
        Map<String, Object> copy = new LinkedHashMap<>();
        resident.forEach((key, value) -> copy.put(key.toString(), value));
        long born = num(resident, "bornDay").longValue();
        if (today - born > Resident.adultAgeMax()) {
            UUID id = UUID.fromString(str(resident, "id"));
            long rebased = today - Resident.adultAgeFrom(
                    new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits()));
            copy.put("bornDay", Math.max(born, rebased));
        }
        return copy;
    }

    private static List<Object> withGenders(Object residents) {
        List<Object> out = new ArrayList<>();
        for (Object resident : asList(residents)) {
            out.add(withGender(asMap(resident)));
        }
        return out;
    }

    /**
     * A copy of an old resident with a gender: the one their given name belongs to, or, for a
     * unrecognised name, one drawn deterministically from their id. Names are kept.
     */
    private static Map<String, Object> withGender(Map<?, ?> resident) {
        Map<String, Object> copy = new LinkedHashMap<>();
        resident.forEach((key, value) -> copy.put(key.toString(), value));
        if (!copy.containsKey("gender")) {
            Gender named = NameGenerator.genderOf(str(resident, "givenName"));
            if (named == null) {
                UUID id = UUID.fromString(str(resident, "id"));
                named = NameGenerator.gender(new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits()));
            }
            copy.put("gender", named.name());
        }
        return copy;
    }

    /** R4.14: the saved gender; a missing one (a format-4 save) comes from the given name, see {@link #migrate}. */
    private static Gender gender(Map<?, ?> map) {
        return Gender.valueOf(str(map, "gender"));
    }

    private static Resident decodeResident(Map<?, ?> map) {
        List<?> t = asList(map.get("traits"));
        List<?> n = asList(map.get("needs"));
        Resident r = new Resident(
                UUID.fromString(str(map, "id")),
                str(map, "givenName"),
                str(map, "familyName"),
                gender(map),
                new Traits(intAt(t, 0), intAt(t, 1), intAt(t, 2), intAt(t, 3)),
                Occupation.valueOf(str(map, "occupation")),
                Boolean.TRUE.equals(map.get("adult")),
                num(map, "bornDay").longValue(),
                uuidOrNull(map.get("parentA")),
                uuidOrNull(map.get("parentB")),
                new Needs(intAt(n, 0), intAt(n, 1), intAt(n, 2)));
        r.setLastBlockedDay(num(map, "lastBlockedDay").longValue());
        // v11 -> v12: "wealth" (R3.5) is optional, so an old save's residents start with nothing put by.
        r.addWealth(Math.max(0, num(map, "wealth").intValue()));
        r.setBuilt(num(map, "built").intValue()); // v20 -> v21: optional, 0 if missing
        for (Map.Entry<?, ?> entry : asMap(map.get("familiarity")).entrySet()) {
            r.familiarity().put(UUID.fromString(entry.getKey().toString()), ((Number) entry.getValue()).intValue());
        }
        return r;
    }

    private static String str(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing field: " + key);
        }
        return value.toString();
    }

    private static Number num(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number : 0;
    }

    private static int intAt(List<?> list, int index) {
        return ((Number) list.get(index)).intValue();
    }

    private static UUID uuidOrNull(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }
}
