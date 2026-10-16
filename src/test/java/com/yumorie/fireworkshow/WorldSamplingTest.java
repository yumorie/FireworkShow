package com.yumorie.fireworkshow;

import com.yumorie.fireworkshow.image.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class WorldSamplingTest {
    @TempDir Path root;
    final ImageAssetService.Limits limits=new ImageAssetService.Limits(16777216,4096,4096,4194304);
    SamplingRequest request(double width,double scale) { return new SamplingRequest(width,scale,.2,16,5000,1); }
    Path solid(String name,int width,int height,int color) throws Exception {
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)image.setRGB(x,y,color);
        Path file=root.resolve(name);ImageIO.write(image,"PNG",file.toFile());return file;
    }
    @Test void resolutionDoesNotDeterminePointCountAndScaleDoes() throws Exception {
        var a=solid("a.png",1024,1024,0xff204060);var b=solid("b.png",2048,2048,0xff204060);
        try(var assets=new ImageAssetService()) {
            var small=assets.sample(a,limits,request(8,.5));
            var high=assets.sample(b,limits,request(4,1));
            var large=assets.sample(a,limits,request(8,1));
            assertEquals(400,small.points().size());assertEquals(400,high.points().size());assertEquals(1600,large.points().size());
            assertTrue(small.points().stream().allMatch(p->p.rgb()==0x204060));
        }
    }
    @Test void cacheHitsDoNotDecodeAndClosingReleasesSource() throws Exception {
        var file=solid("a.png",64,32,0xff123456);var assets=new ImageAssetService();
        var cloud=assets.sample(file,limits,request(4,1));
        for(int tick=0;tick<100;tick++)assertSame(cloud,assets.sample(file,limits,request(4,1)));
        assertEquals(1,assets.stats().decodes());assertEquals(1,assets.stats().variants());assertTrue(assets.retainsDecodedImage());
        assertEquals(200,cloud.points().size());
        double maxX=cloud.points().stream().mapToDouble(p->Math.abs(p.x())).max().orElseThrow();
        double maxY=cloud.points().stream().mapToDouble(p->Math.abs(p.y())).max().orElseThrow();
        assertEquals(1.9,maxX,1e-9);assertEquals(.9,maxY,1e-9);
        assets.close();assertFalse(assets.retainsDecodedImage());assertThrows(IllegalStateException.class,()->assets.sample(file,limits,request(4,1)));
    }
    @Test void transparentPaddingCannotPolluteThinColoredStrokes() {
        var image=new BufferedImage(100,100,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<100;y++)for(int x=0;x<100;x++)image.setRGB(x,y,0x00ffffff);
        for(int y=0;y<100;y++)image.setRGB(11,y,0x804080c0);
        var sampler=new ImageSampler();var req=request(2,1);
        var cloud=sampler.sample(image,req,sampler.grid(100,100,req,10000),()->{});
        assertEquals(10,cloud.points().size());
        for(var p:cloud.points()) { assertEquals(0x4080c0,p.rgb());assertEquals(128,p.alpha());assertEquals((11.5/100-.5)*2,p.x(),1e-9); }
        var cut=new SamplingRequest(2,1,.2,129,5000,1);
        assertTrue(sampler.sample(image,cut,sampler.grid(100,100,cut,10000),()->{}).points().isEmpty());
    }
    @Test void alphaWeightedColorsAndDeterministicCap() {
        var image=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,0xffff0000);image.setRGB(1,0,0x800000ff);
        var sampler=new ImageSampler();var req=new SamplingRequest(1,1,2,0,1,1);
        var p=sampler.sample(image,req,sampler.grid(2,1,req,100),()->{}).points().getFirst();
        assertEquals(0xaa0055,p.rgb());assertEquals(192,p.alpha());
        var many=new BufferedImage(30,30,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<30;y++)for(int x=0;x<30;x++)many.setRGB(x,y,0xff123456);
        req=new SamplingRequest(4,1,.1,16,17,1);var grid=sampler.grid(30,30,req,10000);
        assertEquals(sampler.sample(many,req,grid,()->{}),sampler.sample(many,req,grid,()->{}));
        assertEquals(17,sampler.sample(many,req,grid,()->{}).points().size());
    }
    @Test void headerAndWorkLimitsRejectBeforeUnboundedProcessing() throws Exception {
        var file=solid("a.png",64,64,0xff123456);
        try(var assets=new ImageAssetService()) {
            assertThrows(Exception.class,()->assets.sample(file,new ImageAssetService.Limits(1,4096,4096,4194304),request(4,1)));
            assertThrows(Exception.class,()->assets.sample(file,new ImageAssetService.Limits(16777216,32,32,4194304),request(4,1)));
            assertEquals(0,assets.stats().decodes());
            assertThrows(IllegalArgumentException.class,()->assets.sample(file,limits,request(10000,1)));
        }
        var tiny=new ImageAssetService.PreprocessLimits(1,10000,100000,10000,1,5000,1,10000);
        try(var assets=new ImageAssetService(tiny)) { assertThrows(Exception.class,()->assets.sample(file,limits,request(4,1)));assertEquals(0,assets.stats().decodes()); }
        var bounded=new ImageAssetService.PreprocessLimits(100000,10000,100000,10000,1,5000,1,10000);
        try(var assets=new ImageAssetService(bounded)) {
            assets.sample(file,limits,request(4,1));
            assertTrue(assertThrows(IllegalArgumentException.class,()->assets.sample(file,limits,request(8,1))).getMessage().contains("max-sampling-variants"));
        }
    }
    @Test void samplingParametersRejectNonFiniteAndOutOfRangeValues() {
        for(double spacing:new double[]{0,-1,.001,3,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->new SamplingRequest(4,1,spacing,16,300,1));
        for(double brightness:new double[]{0,-1,3.1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->new SamplingRequest(4,1,.12,16,300,brightness));
    }
    @Test void cumulativeWorkAndCachePointBudgetsAreEnforced() throws Exception {
        var file=solid("a.png",64,64,0xff123456);
        var work=new ImageAssetService.PreprocessLimits(100000,10000,1,10000,10,5000,1,10000);
        try(var assets=new ImageAssetService(work)) {
            assertTrue(assertThrows(IllegalArgumentException.class,()->assets.sample(file,limits,request(4,1))).getMessage().contains("max-pixel-visits"));
            assertEquals(0,assets.stats().decodes());
        }
        var points=new ImageAssetService.PreprocessLimits(100000,10000,100000,10000,10,1,1,10000);
        try(var assets=new ImageAssetService(points)) {
            assertTrue(assertThrows(IllegalArgumentException.class,()->assets.sample(file,limits,request(4,1))).getMessage().contains("max-cached-points"));
        }
    }
}
