package com.yumorie.fireworkshow.spatial;

/** Immutable coordinates in a right-handed orthonormal frame. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Coordinates must be finite");
    }
    public Vec3 add(Vec3 other) { return new Vec3(x + other.x, y + other.y, z + other.z); }
}
