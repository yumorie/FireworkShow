package com.yumorie.fireworkshow.spatial;

/** Degrees, active right-hand rotations applied X, then Y, then Z: Rz * Ry * Rx. */
public record Rotation3D(double x, double y, double z) {
    public static final Rotation3D ZERO = new Rotation3D(0, 0, 0);
    public Rotation3D {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 360000 || Math.abs(y) > 360000 || Math.abs(z) > 360000)
            throw new IllegalArgumentException("rotation: expected finite degrees within +/-360000");
    }
    public static Rotation3D parse(String text) {
        String[] parts = text.split(",", -1);
        if (parts.length != 3) throw new IllegalArgumentException("rotation: expected x,y,z in degrees");
        try { return new Rotation3D(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim())); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("rotation: expected three finite numbers", error); }
    }
    public String encode() { return x + "," + y + "," + z; }
    public Transform3D transform() {
        double a=Math.toRadians(x%360), b=Math.toRadians(y%360), c=Math.toRadians(z%360);
        double sx=Math.sin(a),cx=Math.cos(a),sy=Math.sin(b),cy=Math.cos(b),sz=Math.sin(c),cz=Math.cos(c);
        return Transform3D.rotation(new double[]{cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx,
                sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx,-sy,cy*sx,cy*cx});
    }
}
