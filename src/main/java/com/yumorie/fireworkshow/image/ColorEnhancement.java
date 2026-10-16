package com.yumorie.fireworkshow.image;

public final class ColorEnhancement {
    private ColorEnhancement() {}
    /** Hue-preserving gain: reduce the gain uniformly when the brightest channel would overflow. */
    public static int brighten(int rgb,double brightness) {
        if(!Double.isFinite(brightness)||brightness<0.1||brightness>3) throw new IllegalArgumentException("brightness: expected 0.1..3");
        int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255,max=Math.max(r,Math.max(g,b));
        double gain=max==0?brightness:Math.min(brightness,255.0/max);
        return ((int)Math.round(r*gain)<<16)|((int)Math.round(g*gain)<<8)|(int)Math.round(b*gain);
    }
}
