package com.yumorie.fireworkshow;
import com.yumorie.fireworkshow.spatial.*;
import org.junit.jupiter.api.Test;
class OrientationTest {
    @Test void cameraRightMatchesMinecraftYaw() {
        for(int i=0;i<4;i++) {
            float yaw=i*90;double angle=Math.toRadians(yaw);
            SpatialTest.vector(new Vec3(-Math.cos(angle),2,-Math.sin(angle)),SpatialContext.at(Vec3.ZERO,yaw,Rotation3D.ZERO).imagePoint(1,2,0));
        }
    }
}
