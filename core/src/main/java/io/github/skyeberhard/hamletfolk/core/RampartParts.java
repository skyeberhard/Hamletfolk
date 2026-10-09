package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * R5.11: where an admin's own gatehouse and tower go on the rampart's ring, instead of the generated pillars and towers.
 * A captured gatehouse is built with its passage running north to south and its outside to the south; a captured tower is
 * built as the north-west one, its outer corner at its north-west corner (block 0, 0). Each is turned by whole quarter turns
 * to face outward at the gate or corner it is put on, centred on a gate or with its outer corner on the ring's corner.
 * Pure geometry: the Paper layer finds the ground under the anchor and places the blocks.
 */
public final class RampartParts {
    private RampartParts() {
    }

    /**
     * One captured part put on the ring: the blueprint already turned, the world position of its block (0, 0), and the
     * anchor column (a gate's middle, or a corner) whose ground its floor row sits on.
     */
    public record Placement(BuildingType part, int originX, int originZ, int anchorX, int anchorZ, int turns, Blueprint blueprint) {
        /** True if the part's footprint covers this column. */
        public boolean covers(int x, int z) {
            return x >= originX && x < originX + blueprint.width() && z >= originZ && z < originZ + blueprint.depth();
        }

        /** A key for this placement, unique among the parts of one rampart. */
        public long key() {
            return cell(anchorX, anchorZ);
        }
    }

    /** A column as one number, for sets and maps. */
    public static long cell(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** The gatehouses, one at the middle of each gate, turned so the outside faces away from the village. */
    public static List<Placement> gates(VillagePlan plan, int stage, Blueprint gatehouse) {
        Optional<Works.Ring> found = gatehouse == null || plan == null ? Optional.empty() : Works.ring(plan, stage);
        List<Placement> out = new ArrayList<>();
        if (found.isEmpty()) {
            return out;
        }
        Works.Ring ring = found.get();
        int n = ring.cells().size();
        for (int i = 0; i < n; i++) {
            if (!ring.gap()[i] || ring.gap()[(i - 1 + n) % n]) {
                continue; // (not the start of a run of gap cells)
            }
            int length = 1;
            while (ring.gap()[(i + length) % n] && length < n) {
                length++;
            }
            Works.Spot middle = ring.cells().get((i + (length - 1) / 2) % n);
            Rect rect = ring.rect();
            boolean north = middle.z() == rect.z();
            boolean south = middle.z() == rect.maxZ();
            boolean west = middle.x() == rect.x();
            boolean east = middle.x() == rect.maxX();
            if ((north || south) == (west || east)) {
                continue; // a corner, or off the ring: not a place for a gate
            }
            int turns = south ? 0 : west ? 1 : north ? 2 : 3; // outside is south as built; a clockwise turn takes south to west
            Blueprint turned = gatehouse.rotated(turns);
            out.add(new Placement(BuildingType.GATEHOUSE, middle.x() - (turned.width() - 1) / 2, middle.z() - (turned.depth() - 1) / 2,
                    middle.x(), middle.z(), turns, turned));
        }
        return out;
    }

    /** The towers, one at each corner of the ring that is not a gate, with the outer corner of each on the ring's corner. */
    public static List<Placement> towers(VillagePlan plan, int stage, Blueprint tower) {
        Optional<Works.Ring> found = tower == null || plan == null ? Optional.empty() : Works.ring(plan, stage);
        List<Placement> out = new ArrayList<>();
        if (found.isEmpty()) {
            return out;
        }
        Works.Ring ring = found.get();
        for (int i = 0; i < ring.cells().size(); i++) {
            if (!ring.corner(i) || ring.gap()[i]) {
                continue;
            }
            Works.Spot corner = ring.cells().get(i);
            boolean west = corner.x() == ring.rect().x();
            boolean north = corner.z() == ring.rect().z();
            int turns = north ? (west ? 0 : 1) : (west ? 3 : 2); // as built it is the north-west one
            Blueprint turned = tower.rotated(turns);
            int originX = west ? corner.x() : corner.x() - (turned.width() - 1);
            int originZ = north ? corner.z() : corner.z() - (turned.depth() - 1);
            out.add(new Placement(BuildingType.TOWER, originX, originZ, corner.x(), corner.z(), turns, turned));
        }
        return out;
    }

    /** Every column any of the placements covers. */
    public static Set<Long> covered(List<Placement> placements) {
        Set<Long> out = new HashSet<>();
        for (Placement p : placements) {
            for (int x = p.originX(); x < p.originX() + p.blueprint().width(); x++) {
                for (int z = p.originZ(); z < p.originZ() + p.blueprint().depth(); z++) {
                    out.add(cell(x, z));
                }
            }
        }
        return out;
    }
}
