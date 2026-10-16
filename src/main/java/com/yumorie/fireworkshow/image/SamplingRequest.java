package com.yumorie.fireworkshow.image;

/** Grid density is world-space; output coordinates remain in unscaled image-local units. */
public record SamplingRequest(double width,double scale,double spacing,int alpha,int maxPoints,double brightness) {
    public SamplingRequest {
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(scale)||scale<=0||!Double.isFinite(width*scale)) throw new IllegalArgumentException("Invalid effective image width");
        if(!Double.isFinite(spacing)||spacing<0.02||spacing>2) throw new IllegalArgumentException("particle-spacing: expected 0.02..2 blocks");
        if(alpha<0||alpha>255||maxPoints<1||maxPoints>5000) throw new IllegalArgumentException("Invalid alpha/max-points");
        if(!Double.isFinite(brightness)||brightness<0.1||brightness>3) throw new IllegalArgumentException("brightness: expected 0.1..3");
    }
    public double effectiveWidth() { return width*scale; }
    public SamplingRequest atScale(double scale) { return new SamplingRequest(width,scale,spacing,alpha,maxPoints,brightness); }
}
