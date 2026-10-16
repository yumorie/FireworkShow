package com.yumorie.fireworkshow;
import com.yumorie.fireworkshow.image.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ImageTest {
    @TempDir Path root;
    final ImageAssetService service=new ImageAssetService();
    final ImageAssetService.Limits limits=new ImageAssetService.Limits(2000000,512,512,262144);
    Path png(BufferedImage image) throws Exception { Path p=root.resolve("image.png"); ImageIO.write(image,"png",p.toFile()); return p; }
    @Test void transparentPixelsNeverRender() throws Exception {
        var image=new BufferedImage(3,3,BufferedImage.TYPE_INT_ARGB);image.setRGB(1,1,0xffff0000);
        var cloud=service.load(png(image),limits,3,0,700,1);
        assertEquals(1,cloud.points().size());var p=cloud.points().getFirst();
        assertEquals(0xff0000,p.rgb());assertEquals(0,p.x());assertEquals(0,p.y());
    }
    @Test void alphaBoundaries() throws Exception {
        var image=new BufferedImage(4,1,BufferedImage.TYPE_INT_ARGB);
        int[] alphas={0,15,16,255};for(int i=0;i<4;i++) image.setRGB(i,0,(alphas[i]<<24)|0xff0000);
        Path p=png(image);assertEquals(2,service.load(p,limits,4,16,700,1).points().size());
        assertEquals(3,service.load(p,limits,4,0,700,1).points().size());
    }
    @Test void samplingAndAspectAreStable() throws Exception {
        var image=new BufferedImage(8,4,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<4;y++)for(int x=0;x<8;x++)image.setRGB(x,y,0xffabcdef);
        Path p=png(image);var a=service.load(p,limits,8,0,5,1);assertEquals(5,a.points().size());assertEquals(a,service.load(p,limits,8,0,5,1));
        var full=service.load(p,limits,8,0,100,1);assertEquals(7,full.points().stream().mapToDouble(v->v.x()).max().orElseThrow()-full.points().stream().mapToDouble(v->v.x()).min().orElseThrow());
        assertEquals(3,full.points().stream().mapToDouble(v->v.y()).max().orElseThrow()-full.points().stream().mapToDouble(v->v.y()).min().orElseThrow());
        assertThrows(UnsupportedOperationException.class,()->a.points().clear());
    }
    @Test void pathsAndDimensionsAreBounded() throws Exception {
        assertThrows(Exception.class,()->ImageAssetService.resolve(root,"../evil.png"));
        assertThrows(Exception.class,()->ImageAssetService.resolve(root,"https://evil/a.png"));
        assertThrows(Exception.class,()->ImageAssetService.resolve(root,"C:/evil.png"));
        Path p=png(new BufferedImage(3,3,BufferedImage.TYPE_INT_ARGB));
        assertThrows(Exception.class,()->service.load(p,new ImageAssetService.Limits(200000,2,2,4),1,0,1,1));
        assertThrows(Exception.class,()->service.load(p,new ImageAssetService.Limits(1,512,512,10000),1,0,1,1));
    }
    @Test void bundledHeartIsRealTransparentMulticolorPng() throws Exception {
        try(var in=getClass().getResourceAsStream("/images/heart.png")) {
            var image=ImageIO.read(in);assertNotNull(image);assertEquals(0,image.getRGB(0,0)>>>24);
            assertNotEquals(image.getRGB(30,30),image.getRGB(35,35));
        }
    }
    @Test void emptyAndSinglePointImagesRemainValidAndCentered() throws Exception {
        var empty=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        assertTrue(service.load(png(empty),limits,5,0,1,1).points().isEmpty());
        empty.setRGB(0,0,0xffffffff);
        var point=service.load(png(empty),limits,5,0,1,1).points().getFirst();
        assertEquals(0,point.x());assertEquals(0,point.y());
    }
}
