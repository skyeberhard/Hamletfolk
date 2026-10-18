package io.github.skyeberhard.hamletfolk.core;

/**
 * R4.62: the quality of a village's goods. The ledger counts units, not individual items, so quality is a property of the village's stores of a
 * category: the skill of the best hand who makes it. Level 1 and 2 goods are plain, level 3 good, level 4 fine, level 5 masterwork. The Paper layer
 * shows it on the items in the store barrels (a name and the maker in the lore); nothing is saved.
 */
public final class Quality {
    /** What a category's stores are like: the label ("" for plain), the best maker's name (null if there is none) and their level. */
    public record Grade(String label, String maker, int level) {
        public boolean plain() {
            return label.isEmpty();
        }
    }

    private Quality() {
    }

    /** The grade for a level of skill. */
    public static String label(int level) {
        return switch (Math.max(1, level)) {
            case 1, 2 -> "";
            case 3 -> "Good";
            case 4 -> "Fine";
            default -> "Masterwork";
        };
    }

    /** Whether a category is made, so quality means something: raw materials and mixed food are always plain. */
    public static boolean graded(ResourceType category) {
        return category == ResourceType.TOOLS || category == ResourceType.GOODS;
    }

    /** The grade of a village's stores of this category: set by its best adult maker of it (plain for a category that is not graded). */
    public static Grade of(Settlement settlement, ResourceType category) {
        if (!graded(category)) {
            return new Grade("", null, 1);
        }
        Resident best = null;
        for (Resident r : settlement.residents()) {
            if (r.adult() && r.occupation().produces() == category && (best == null || r.level() > best.level())) {
                best = r;
            }
        }
        if (best == null) {
            return new Grade("", null, 1);
        }
        String label = label(best.level());
        return new Grade(label, label.isEmpty() ? null : best.fullName(), best.level());
    }
}
