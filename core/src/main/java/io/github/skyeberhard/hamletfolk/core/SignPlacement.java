package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * R2.9: where a building the village raises carries its name. A house has none (it is known by its bed and its door); a business has a
 * hanging sign over its door; a field, pen or pit has a sign on a fence post beside it. Pure geometry on the blueprint (coordinates
 * relative to the building's corner, already turned to the way it faces): the Paper layer checks each spot against the world and, where
 * none will do, falls back on a sign standing on the ground beside the building, as before.
 */
public final class SignPlacement {
    /** How a kind of building is marked. */
    public enum Form {
        /** No sign: the building is registered where its bed is. */
        NONE,
        /** A hanging sign over (or beside) its door. */
        HANGING,
        /** A sign on a fence post by its field or pit. */
        POST
    }

    /**
     * A wall hanging sign: the cell it fills, the wall block it hangs from, and the way its faces point (0 north, 1 east, 2 south, 3 west;
     * a board hangs across the wall, so it faces along it).
     */
    public record Hanging(int x, int y, int z, int supportX, int supportY, int supportZ, int facing) {
    }

    private SignPlacement() {
    }

    /** How a kind of building is marked. */
    public static Form formOf(BuildingType type) {
        return switch (type) {
            case HOUSE -> Form.NONE;
            case FARM, PENS, APIARY, HARBOUR -> Form.POST;
            default -> Form.HANGING;
        };
    }

    /**
     * Where a hanging sign could go on this building, best first: over the door (hung from the wall above it), then at either side of it
     * at the height of the door's upper half. Empty if the blueprint has no door or does not say which way it faces.
     */
    public static List<Hanging> hangingSpots(Blueprint bp) {
        Optional<Blueprint.Block> door = bp.door();
        java.util.OptionalInt front = bp.front();
        if (door.isEmpty() || front.isEmpty()) {
            return List.of();
        }
        int out = front.getAsInt();
        int dx = out == 1 ? 1 : out == 3 ? -1 : 0;
        int dz = out == 2 ? 1 : out == 0 ? -1 : 0;
        int facing = dz != 0 ? 1 : 0; // across the wall
        int px = dz != 0 ? 1 : 0;
        int pz = dx != 0 ? 1 : 0;
        Blueprint.Block d = door.get();
        List<Hanging> spots = new ArrayList<>();
        spots.add(new Hanging(d.x() + dx, d.y() + 2, d.z() + dz, d.x(), d.y() + 2, d.z(), facing));
        for (int side = -1; side <= 1; side += 2) {
            int sx = d.x() + side * px;
            int sz = d.z() + side * pz;
            spots.add(new Hanging(sx + dx, d.y() + 1, sz + dz, sx, d.y() + 1, sz, facing));
        }
        // outside the building: a door inside it (the lowest door may be an inner one) has its "out" in a room, where a sign is no use
        spots.removeIf(s -> s.x() >= 0 && s.x() < bp.width() && s.z() >= 0 && s.z() < bp.depth());
        return spots;
    }

    /** The block that marks a house instead of a sign: where a bed lies (its head, if the blueprint says), if it holds one. */
    public static Optional<Blueprint.Block> marker(Blueprint bp) {
        return bp.bed();
    }
}
