package io.github.skyeberhard.hamletfolk.core;

import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

/**
 * R8.3, R8.4: the ground a village is planned on: the height of the surface at a column, and whether that column is
 * water. The Paper layer answers from the world (only where chunks are loaded); tests answer from a formula. A column
 * the source cannot answer for reports {@link #UNKNOWN}, which counts as ground nobody can build on.
 */
public interface HeightSource {
    /** The height of a column nobody could measure. */
    int UNKNOWN = Integer.MIN_VALUE;

    /** The height of the highest solid block (or water surface) at this column, or {@link #UNKNOWN}. */
    int height(int x, int z);

    /** True if the surface at this column is water. */
    default boolean water(int x, int z) {
        return false;
    }

    /** The height of the solid ground at this column: under the water if it is wet, otherwise the surface. */
    default int floor(int x, int z) {
        return height(x, z);
    }

    static HeightSource flat(int height) {
        return (x, z) -> height;
    }

    static HeightSource of(IntBinaryOperator height, BiPredicate<Integer, Integer> water) {
        return new HeightSource() {
            @Override
            public int height(int x, int z) {
                return height.applyAsInt(x, z);
            }

            @Override
            public boolean water(int x, int z) {
                return water.test(x, z);
            }
        };
    }

    /** Ground with a water surface above a solid floor (a lake bed): the first function is the surface, the last the bed. */
    static HeightSource withFloor(IntBinaryOperator height, BiPredicate<Integer, Integer> water, IntBinaryOperator floor) {
        return new HeightSource() {
            @Override
            public int height(int x, int z) {
                return height.applyAsInt(x, z);
            }

            @Override
            public boolean water(int x, int z) {
                return water.test(x, z);
            }

            @Override
            public int floor(int x, int z) {
                return floor.applyAsInt(x, z);
            }
        };
    }

    static HeightSource of(IntBinaryOperator height) {
        return of(height, (x, z) -> false);
    }
}
