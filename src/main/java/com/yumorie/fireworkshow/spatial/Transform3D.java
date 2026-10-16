package com.yumorie.fireworkshow.spatial;

/** Uniform similarity transform p' = scale*R*p+t. Composition is parent * local. */
public final class Transform3D {
    public static final double MIN_SCALE=0.05, MAX_SCALE=20;
    public static final Transform3D IDENTITY = new Transform3D(new double[]{1,0,0,0,1,0,0,0,1},Vec3.ZERO,1);
    private final double[] rotation;
    private final Vec3 translation;
    private final double scale;
    private Transform3D(double[] rotation, Vec3 translation,double scale) {
        if(!Double.isFinite(scale) || scale<=0) throw new IllegalArgumentException("Invalid composed scale");
        this.rotation=rotation.clone();this.translation=translation;this.scale=scale;
    }
    static Transform3D rotation(double[] matrix) { return new Transform3D(matrix,Vec3.ZERO,1); }
    public static Transform3D of(Vec3 position, Rotation3D rotation) { return of(position,rotation,1); }
    public static Transform3D of(Vec3 position,Rotation3D rotation,double scale) {
        if(!Double.isFinite(scale) || scale<MIN_SCALE || scale>MAX_SCALE)
            throw new IllegalArgumentException("scale: expected finite number in ["+MIN_SCALE+", "+MAX_SCALE+"]");
        return new Transform3D(rotation.transform().rotation,position,scale);
    }
    public double scale() { return scale; }
    public Vec3 rotate(Vec3 p) {
        return new Vec3(rotation[0]*p.x()+rotation[1]*p.y()+rotation[2]*p.z(),
                rotation[3]*p.x()+rotation[4]*p.y()+rotation[5]*p.z(),rotation[6]*p.x()+rotation[7]*p.y()+rotation[8]*p.z());
    }
    public Vec3 apply(Vec3 point) {
        var rotated=rotate(point);
        return new Vec3(rotated.x()*scale,rotated.y()*scale,rotated.z()*scale).add(translation);
    }
    public Transform3D child(Transform3D local) {
        double[] product=new double[9];
        for(int row=0;row<3;row++) for(int col=0;col<3;col++) for(int k=0;k<3;k++) product[row*3+col]+=rotation[row*3+k]*local.rotation[k*3+col];
        return new Transform3D(product,apply(local.translation),scale*local.scale);
    }
    public boolean hasRotation() {
        for(int i=0;i<9;i++) if(Math.abs(rotation[i]-(i%4==0?1:0))>1e-9) return true;
        return false;
    }
    public boolean hasScale() { return Math.abs(scale-1)>1e-9; }
    public Transform3D linearOnly() { return new Transform3D(rotation,Vec3.ZERO,scale); }
}
