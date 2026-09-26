package io.github.skyeberhard.societies.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts settlements to and from plain maps, lists, strings and numbers, so any
 * serializer (Gson, YAML, NBT) can store them. Numbers are read through {@link Number}
 * because serializers disagree on whether 3 comes back as an int, long or double.
 */
public final class SettlementCodec {
    public static final int FORMAT_VERSION = 1;

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

        List<Object> residents = new ArrayList<>();
        for (Resident r : s.residents()) {
            residents.add(encodeResident(r));
        }
        map.put("residents", residents);

        List<Object> history = new ArrayList<>();
        for (HistoryEvent e : s.history()) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("day", e.day());
            event.put("kind", e.kind().name());
            event.put("text", e.text());
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

    public static Settlement decode(Map<?, ?> map) {
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

        for (Object o : asList(map.get("residents"))) {
            s.addResident(decodeResident(asMap(o)));
        }

        for (Object o : asList(map.get("history"))) {
            Map<?, ?> event = asMap(o);
            s.record(num(event, "day").longValue(), HistoryEvent.Kind.valueOf(str(event, "kind")), str(event, "text"));
        }
        return s;
    }

    private static Resident decodeResident(Map<?, ?> map) {
        List<?> t = asList(map.get("traits"));
        List<?> n = asList(map.get("needs"));
        Resident r = new Resident(
                UUID.fromString(str(map, "id")),
                str(map, "givenName"),
                str(map, "familyName"),
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
