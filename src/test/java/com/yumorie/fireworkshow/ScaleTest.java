package com.yumorie.fireworkshow;

import com.yumorie.fireworkshow.spatial.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScaleTest {
    private Transform3D configured(String scale) throws Exception {
        var yaml=new YamlConfiguration();
        yaml.loadFromString("transform:\n  position: {x: 3, y: 2, z: 1}\n  rotation: {x: 20, y: 30, z: 40}\n"+(scale==null?"":"  scale: "+scale+"\n"));
        return SpatialConfig.read(yaml);
    }
    @Test void omittedAndUnitScaleMatchExistingOutput() throws Exception {
        Vec3 point=new Vec3(4,-2,7);
        var omitted=configured(null);var explicit=configured("1");
        assertEquals(1,omitted.scale());assertFalse(omitted.hasScale());
        assertEquals(omitted.apply(point),explicit.apply(point));
        assertEquals(Transform3D.of(new Vec3(3,2,1),new Rotation3D(20,30,40)).apply(point),explicit.apply(point));
        assertEquals(Transform3D.IDENTITY.apply(point),Transform3D.of(Vec3.ZERO,Rotation3D.ZERO,1).apply(point));
    }
    @Test void ownPositionIsNotScaledButParentScalesChildPosition() {
        var track=Transform3D.of(new Vec3(10,0,0),Rotation3D.ZERO,2);
        var clip=Transform3D.of(new Vec3(3,0,0),Rotation3D.ZERO,0.5);
        SpatialTest.vector(new Vec3(10,0,0),track.apply(Vec3.ZERO));
        SpatialTest.vector(new Vec3(3,0,0),clip.apply(Vec3.ZERO));
        var combined=track.child(clip);
        assertEquals(1,combined.scale());
        SpatialTest.vector(new Vec3(16,0,0),combined.apply(Vec3.ZERO));
        SpatialTest.vector(new Vec3(17,2,3),combined.apply(new Vec3(1,2,3)));
    }
    @Test void rotationsAndScalesComposeWithTranslationInParentFrame() {
        var parent=Transform3D.of(new Vec3(10,0,0),new Rotation3D(0,0,90),2);
        var child=Transform3D.of(new Vec3(3,0,0),new Rotation3D(0,0,90),0.5);
        SpatialTest.vector(new Vec3(9,4,3),parent.child(child).apply(new Vec3(1,2,3)));
        var spatial=SpatialContext.at(Vec3.ZERO,0,Rotation3D.ZERO)
                .child(Transform3D.of(new Vec3(3,2,0),Rotation3D.ZERO,1.5))
                .child(Transform3D.of(new Vec3(0,1,0),Rotation3D.ZERO,0.8));
        assertEquals(1.2,spatial.world().scale(),1e-12);
        SpatialTest.vector(new Vec3(-3,3.5,0),spatial.apply(Vec3.ZERO));
        SpatialTest.vector(new Vec3(-5.4,4.7,0),spatial.imagePoint(2,1,0));
    }
    @Test void invalidAndOutOfRangeScaleRejectedAtConfigAndFactory() {
        for(String value:new String[]{"0","-1",".nan",".inf","-.inf","0.049","20.001","\"1.5\"","false","{}","[1,1,1]"})
            assertThrows(IllegalArgumentException.class,()->configured(value),value);
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,0.01,100})
            assertThrows(IllegalArgumentException.class,()->Transform3D.of(Vec3.ZERO,Rotation3D.ZERO,value));
    }
    @Test void perLevelLimitsAllowCorrectComposedScale() throws Exception {
        assertEquals(0.05,configured("0.05").scale());assertEquals(20,configured("20").scale());
        assertEquals(400,Transform3D.of(Vec3.ZERO,Rotation3D.ZERO,20).child(Transform3D.of(Vec3.ZERO,Rotation3D.ZERO,20)).scale());
        assertEquals(0.0025,Transform3D.of(Vec3.ZERO,Rotation3D.ZERO,0.05).child(Transform3D.of(Vec3.ZERO,Rotation3D.ZERO,0.05)).scale(),1e-12);
    }
    @Test void libraryShapeScaleIsDetectedIndependentlyOfRotation() {
        var bridge=new ParticleLibTest().adapter();
        var root=SpatialContext.at(Vec3.ZERO,90,Rotation3D.ZERO);
        var scaled=root.child(Transform3D.of(new Vec3(1,0,0),Rotation3D.ZERO,2));
        assertFalse(scaled.requestsRotation());assertTrue(scaled.requestsScale());
        assertDoesNotThrow(()->bridge.validateSpatial(scaled,"origin-only"));
        assertThrows(IllegalArgumentException.class,()->bridge.validateSpatial(scaled,"require-full"));
        var cancelled=scaled.child(Transform3D.of(Vec3.ZERO,Rotation3D.ZERO,0.5));
        assertFalse(cancelled.requestsShapeTransform());assertDoesNotThrow(()->bridge.validateSpatial(cancelled,"require-full"));
    }
}
