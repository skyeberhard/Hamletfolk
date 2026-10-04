package io.github.skyeberhard.hamletfolk.core;

/** R8.3: a rectangle of blocks on the ground, {@code width} east-west (x) and {@code depth} north-south (z). */
public record Rect(int x, int z, int width, int depth) {
    public Rect {
        if (width < 1 || depth < 1) {
            throw new IllegalArgumentException("a rectangle needs a size");
        }
    }

    public int maxX() {
        return x + width - 1;
    }

    public int maxZ() {
        return z + depth - 1;
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerZ() {
        return z + depth / 2;
    }

    public boolean overlaps(Rect other) {
        return x <= other.maxX() && maxX() >= other.x && z <= other.maxZ() && maxZ() >= other.z;
    }

    /** This rectangle grown by {@code margin} blocks on every side. */
    public Rect inflated(int margin) {
        return new Rect(x - margin, z - margin, width + 2 * margin, depth + 2 * margin);
    }

    public boolean contains(int px, int pz) {
        return px >= x && px <= maxX() && pz >= z && pz <= maxZ();
    }
}
