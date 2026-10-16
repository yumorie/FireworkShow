package com.yumorie.fireworkshow.spatial;

/** Rigid transform p' = R*p+t. parent.child(local) composes parent * local. */
public final class Transform3D {
    public static final Transform3D IDENTITY = new Transform3D(new double[]{1,0,0,0,1,0,0,0,1},Vec3.ZERO);
    private final double[] rotation;
    private final Vec3 translation;
    private Transform3D(double[] rotation, Vec3 translation) { this.rotation=rotation.clone();this.translation=translation; }
    static Transform3D rotation(double[] matrix) { return new Transform3D(matrix,Vec3.ZERO); }
    public static Transform3D of(Vec3 position, Rotation3D rotation) { return new Transform3D(rotation.transform().rotation,position); }
    public Vec3 rotate(Vec3 p) {
        return new Vec3(rotation[0]*p.x()+rotation[1]*p.y()+rotation[2]*p.z(),
                rotation[3]*p.x()+rotation[4]*p.y()+rotation[5]*p.z(),rotation[6]*p.x()+rotation[7]*p.y()+rotation[8]*p.z());
    }
    public Vec3 apply(Vec3 point) { return rotate(point).add(translation); }
    public Transform3D child(Transform3D local) {
        double[] product=new double[9];
        for(int row=0;row<3;row++) for(int col=0;col<3;col++) for(int k=0;k<3;k++) product[row*3+col]+=rotation[row*3+k]*local.rotation[k*3+col];
        return new Transform3D(product,apply(local.translation));
    }
    public boolean hasRotation() {
        for(int i=0;i<9;i++) if(Math.abs(rotation[i]-(i%4==0?1:0))>1e-9) return true;
        return false;
    }
    public Transform3D rotationOnly() { return new Transform3D(rotation,Vec3.ZERO); }
}
