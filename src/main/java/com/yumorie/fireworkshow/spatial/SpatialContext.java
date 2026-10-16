package com.yumorie.fireworkshow.spatial;

/** World origin/heading -> global -> track -> clip. No additive Euler composition. */
public record SpatialContext(Transform3D world, Transform3D requested) {
    public static SpatialContext at(Vec3 origin, float yaw, Rotation3D global) {
        // yaw=0: camera right=-X, up=+Y, toward viewer=-Z; determinant +1.
        Transform3D base=Transform3D.of(origin,new Rotation3D(0,180-yaw,0));
        var rotation=global.transform();
        return new SpatialContext(base.child(rotation),rotation);
    }
    public SpatialContext child(Transform3D local) {
        return new SpatialContext(world.child(local),requested.child(local).linearOnly());
    }
    public Vec3 apply(Vec3 point) { return world.apply(point); }
    public boolean requestsRotation() { return requested.hasRotation(); }
    public boolean requestsScale() { return requested.hasScale(); }
    public boolean requestsShapeTransform() { return requestsRotation() || requestsScale(); }
    public Vec3 imagePoint(double x,double y,double z) { return apply(new Vec3(x,y,z)); }
}
