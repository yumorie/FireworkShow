package com.yumorie.fireworkshow;

import com.yumorie.fireworkshow.spatial.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialTest {
    static void vector(Vec3 expected,Vec3 actual) {
        assertEquals(expected.x(),actual.x(),1e-9);assertEquals(expected.y(),actual.y(),1e-9);assertEquals(expected.z(),actual.z(),1e-9);
    }
    @Test void rootFrameIsRightHandedForAllHeadings() {
        for(float yaw:new float[]{0,45,90,180,-90}) {
            double a=Math.toRadians(yaw),x=2,y=3,z=0.5;
            var spatial=SpatialContext.at(new Vec3(10,20,30),yaw,Rotation3D.ZERO).child(Transform3D.IDENTITY).child(Transform3D.IDENTITY);
            vector(new Vec3(10-Math.cos(a)*x+Math.sin(a)*z,23,30-Math.sin(a)*x-Math.cos(a)*z),spatial.imagePoint(x,y,z));
            assertFalse(spatial.requestsRotation());
        }
    }
    @Test void independentRightHandAxes() {
        vector(new Vec3(0,0,1),new Rotation3D(90,0,0).transform().apply(new Vec3(0,1,0)));
        vector(new Vec3(1,0,0),new Rotation3D(0,90,0).transform().apply(new Vec3(0,0,1)));
        vector(new Vec3(0,1,0),new Rotation3D(0,0,90).transform().apply(new Vec3(1,0,0)));
    }
    @Test void rotationOrderAndNestedCompositionCannotBeReplacedByAngleAddition() {
        var xyz=new Rotation3D(90,90,90).transform();
        vector(new Vec3(0,1,0),xyz.apply(new Vec3(0,1,0)));
        var parent=new Rotation3D(90,0,0).transform();var child=new Rotation3D(0,90,0).transform();
        vector(new Vec3(1,0,0),parent.child(child).apply(new Vec3(0,0,1)));
        vector(new Vec3(0,-1,0),new Rotation3D(90,90,0).transform().apply(new Vec3(0,0,1)));
    }
    @Test void parentRotationAffectsChildOffsetAndClipInheritsTrack() {
        var root=SpatialContext.at(new Vec3(10,20,30),0,new Rotation3D(0,0,90));
        var track=root.child(Transform3D.of(new Vec3(2,0,0),new Rotation3D(90,0,0)));
        var clip=track.child(Transform3D.of(new Vec3(0,1,0),Rotation3D.ZERO));
        vector(new Vec3(10,22,29),clip.apply(Vec3.ZERO));
        var other=root.child(Transform3D.of(new Vec3(-2,0,0),Rotation3D.ZERO));
        vector(new Vec3(10,18,30),other.apply(Vec3.ZERO));
    }
    @Test void ninetyDegreePitchMakesHorizontalImageReadableFromBelow() {
        for(float yaw:new float[]{0,90,180,-90}) {
            var context=SpatialContext.at(Vec3.ZERO,yaw,new Rotation3D(90,0,0));
            double a=Math.toRadians(yaw);
            // Pitching the camera upward keeps right unchanged; camera up becomes opposite the horizontal heading.
            vector(new Vec3(-Math.cos(a),0,-Math.sin(a)),context.imagePoint(1,0,0));
            vector(new Vec3(Math.sin(a),0,-Math.cos(a)),context.imagePoint(0,1,0));
        }
    }
    @Test void invalidRotationsAndTransformsAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class,()->Rotation3D.parse("NaN,0,0"));
        assertThrows(IllegalArgumentException.class,()->Rotation3D.parse("1,2"));
        var yaml=new org.bukkit.configuration.file.YamlConfiguration();
        yaml.loadFromString("transform:\n  position: {x: 1, y: 2, z: 3}\n  rotation: {x: 0, y: 0, z: 90}\n");
        vector(new Vec3(1,3,3),SpatialConfig.read(yaml).apply(new Vec3(1,0,0)));
        yaml.set("transform.position.x",Double.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class,()->SpatialConfig.read(yaml));
    }
}
