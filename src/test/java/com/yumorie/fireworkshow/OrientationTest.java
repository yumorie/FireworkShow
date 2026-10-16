package com.yumorie.fireworkshow;
import com.yumorie.fireworkshow.render.PointCloudRenderer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrientationTest {
    @Test void cameraRightMatchesMinecraftYaw() {
        assertArrayEquals(new double[]{-1,2,0},PointCloudRenderer.offset(1,2,0,0),1e-10);
        assertArrayEquals(new double[]{0,2,-1},PointCloudRenderer.offset(1,2,0,90),1e-10);
        assertArrayEquals(new double[]{1,2,0},PointCloudRenderer.offset(1,2,0,180),1e-10);
        assertArrayEquals(new double[]{0,2,1},PointCloudRenderer.offset(1,2,0,-90),1e-10);
    }
}
