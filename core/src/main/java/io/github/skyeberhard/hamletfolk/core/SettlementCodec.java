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
    public static final int FORMAT_VERSION = 5;

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
        if (version < 5) {
            // v4 -> v5: residents gain a gender (R4.14).
            migrated.put("residents", withGenders(migrated.get("residents")));
            Map<String, Object> turned = new LinkedHashMap<>();
            asMap(migrated.get("turned")).forEach((zombie, resident) ->
                    turned.put(zombie.toString(), withGender(asMap(resident))));
            migrated.put("turned", turned);
        }
        migrated.put("format", FORMAT_VERSION);
        return migrated;
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
     * neutral or unrecognised name, one drawn deterministically from their id. Names are kept.
     */
    private static Map<String, Object> withGender(Map<?, ?> resident) {
        Map<String, Object> copy = new LinkedHashMap<>();
        resident.forEach((key, value) -> copy.put(key.toString(), value));
        if (!copy.containsKey("gender")) {
            Gender named = NameGenerator.genderOf(str(resident, "givenName"));
            if (named == null || named == Gender.NONBINARY) {
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
