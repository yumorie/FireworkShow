package com.yumorie.fireworkshow;

import com.yumorie.fireworkshow.image.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RawPixelTest {
    @TempDir Path root;
    final ImageAssetService.Limits limits=new ImageAssetService.Limits(16777216,4096,4096,4194304);
    BufferedImage pixels(int width,int height) {
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)image.setRGB(x,y,0xff123456);
        return image;
    }
    ImagePointCloud build(BufferedImage image,int threshold) { return new RawPixelCloudBuilder().build(image,10,threshold,1,2000000,()->{}); }
    @Test void tenByTenOpaquePixelsProduceExactlyOneHundredPoints() { assertEquals(100,build(pixels(10,10),16).points().size()); }
    @Test void twentyFiveNonTransparentPixelsProduceExactlyTwentyFivePoints() {
        var image=new BufferedImage(10,10,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<5;y++)for(int x=0;x<5;x++)image.setRGB(x,y,0x10123456);
        assertEquals(25,build(image,16).points().size());assertEquals(0,build(image,17).points().size());
    }
    @Test void originalRgbAlphaAndPixelCentersCorrespondExactly() {
        var image=new BufferedImage(10,5,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0xff010203);image.setRGB(9,4,0x10123456);image.setRGB(4,2,0x00ffffff);
        var cloud=build(image,16);assertEquals(2,cloud.points().size());
        var byIndex=new HashMap<Integer,ImagePointCloud.Point>();for(var p:cloud.points())byIndex.put(p.index(),p);
        assertEquals(-4.5,byIndex.get(0).x(),1e-12);assertEquals(2,byIndex.get(0).y(),1e-12);assertEquals(0x010203,byIndex.get(0).rgb());
        assertEquals(4.5,byIndex.get(49).x(),1e-12);assertEquals(-2,byIndex.get(49).y(),1e-12);assertEquals(0x123456,byIndex.get(49).rgb());assertEquals(16,byIndex.get(49).alpha());
    }
    @Test void scalingSpacingAndPointLimitNeverChangeOrSplitRawCloud() throws Exception {
        Path file=root.resolve("raw.png");ImageIO.write(pixels(10,10),"PNG",file.toFile());
        try(var assets=new ImageAssetService()) {
            var a=assets.loadRaw(file,limits,new SamplingRequest(10,1,.02,16,1,1));
            var b=assets.loadRaw(file,limits,new SamplingRequest(10,20,2,16,5000,1));
            var c=assets.loadRaw(file,limits,new SamplingRequest(10,.05,.12,16,3,1));
            assertEquals(100,a.points().size());assertSame(a,b);assertSame(a,c);
            for(int tick=0;tick<100;tick++)assertSame(a,assets.loadRaw(file,limits,new SamplingRequest(10,1,.12,16,1,1)));
            assertEquals(1,assets.stats().decodes());assertEquals(1,assets.stats().variants());
        }
    }
    @Test void memoryLimitRejectsWholeAssetAndNeverReturnsTruncatedCloud() {
        var error=assertThrows(IllegalArgumentException.class,()->new RawPixelCloudBuilder().build(pixels(10,10),10,16,1,99,()->{}));
        assertTrue(error.getMessage().contains("100 points"));assertTrue(error.getMessage().contains("no truncation"));
    }
    @Test void largeOriginalDimensionsWorkWithoutGridOrLod() {
        var image=pixels(512,512);var cloud=build(image,16);assertEquals(262144,cloud.points().size());
    }
    @Test void revealOrderingPreservesEverySourceIndex() {
        var cloud=build(pixels(10,10),16);var seen=new HashSet<Integer>();double previous=-1;
        for(var point:cloud.points()) { assertTrue(seen.add(point.index()));double distance=point.x()*point.x()+point.y()*point.y();assertTrue(distance>=previous);previous=distance; }
        assertEquals(100,seen.size());assertTrue(seen.contains(0));assertTrue(seen.contains(99));
    }
}
