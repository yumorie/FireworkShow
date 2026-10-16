package com.yumorie.fireworkshow;

import com.yumorie.fireworkshow.config.*;
import com.yumorie.fireworkshow.image.*;
import com.yumorie.fireworkshow.render.*;
import com.yumorie.fireworkshow.runtime.ShowContext;
import com.yumorie.fireworkshow.spatial.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class GlowRenderingTest {
    @Test void brightnessPreservesColorHueAndAlphaWithoutOverflow() {
        assertEquals(0x204060,ColorEnhancement.brighten(0x204060,1));
        assertEquals(0x306090,ColorEnhancement.brighten(0x204060,1.5));
        assertEquals(0xff8040,ColorEnhancement.brighten(0xff8040,3));
        assertEquals(0,ColorEnhancement.brighten(0,3));
        var image=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,0x80204060);
        var sampler=new ImageSampler();var a=new SamplingRequest(1,1,1,16,100,1);var b=new SamplingRequest(1,1,1,16,100,1.5);
        var first=sampler.sample(image,a,sampler.grid(1,1,a,100),()->{}).points().getFirst();
        var second=sampler.sample(image,b,sampler.grid(1,1,b,100),()->{}).points().getFirst();
        assertEquals(first.alpha(),second.alpha());assertEquals(128,second.alpha());assertEquals(0x306090,second.rgb());
    }
    ImagePointCloud cloud() { return new ImagePointCloud(IntStream.range(0,100).mapToObj(i->new ImagePointCloud.Point(i*.01,0,0x123456,255,i)).toList()); }
    @Test void glowSelectionIsSparseStableAndDisabledByDefault() {
        var cloud=cloud();assertTrue(GlowDefinition.OFF.select(cloud).isEmpty());
        var glow=new GlowDefinition(true,.03);assertEquals(3,glow.select(cloud).size());assertEquals(glow.select(cloud),glow.select(cloud));
        for(double density:new double[]{-.1,.2,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->new GlowDefinition(true,density));
        assertEquals(Void.class,Particle.GLOW.getDataType());
    }
    ShowContext context(boolean glow,RenderBudget budget,List<Particle> emitted) {
        List<Player> players=new ArrayList<>();
        World world=(World)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{World.class},(p,m,a)->switch(m.getName()) {
            case "getPlayers" -> players;case "equals" -> p==a[0];case "hashCode" -> 1;default -> throw new UnsupportedOperationException(m.getName());
        });
        for(int viewer=0;viewer<2;viewer++)players.add((Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()) {
            case "isOnline" -> true;case "getLocation" -> new Location(world,0,0,0);case "spawnParticle" -> { emitted.add((Particle)a[0]);yield null; }
            case "equals" -> p==a[0];case "hashCode" -> 2;default -> throw new UnsupportedOperationException(m.getName());
        }));
        var image=new ImageDefinition(cloud(),.9f,Path.of("unused.png"),new SamplingRequest(4,1,.12,16,100,1),new GlowDefinition(glow,.03));
        var show=new ShowDefinition("test","test",Map.of("test",image),"fixed",0,List.of());
        return new ShowContext(show,Settings.load(new YamlConfiguration()),new Location(world,0,0,0),null,budget,null,"test",SpatialContext.at(Vec3.ZERO,0,Rotation3D.ZERO),42);
    }
    @Test void disabledGlowEmitsOnlyColoredDust() {
        var emitted=new ArrayList<Particle>();var budget=new RenderBudget();budget.reset(300);
        new PointCloudRenderer().render(context(false,budget,emitted),"hold",1,1,0,false);budget.flushAccents();
        assertEquals(200,emitted.size());assertTrue(emitted.stream().allMatch(p->p==Particle.DUST));assertEquals(100,budget.remaining());
    }
    @Test void glowUsesPerViewerGlobalBudgetAfterAllColoredPasses() {
        var emitted=new ArrayList<Particle>();var budget=new RenderBudget();budget.reset(405);
        var renderer=new PointCloudRenderer();var context=context(true,budget,emitted);
        renderer.render(context,"hold",1,1,0,false);renderer.render(context,"hold",1,1,0,false);
        assertEquals(400,emitted.size());assertEquals(5,budget.remaining());budget.flushAccents();
        assertEquals(404,emitted.size());assertEquals(4,emitted.stream().filter(p->p==Particle.GLOW).count());assertEquals(1,budget.remaining());
        budget.flushAccents();assertEquals(404,emitted.size());
        budget.reset(200);emitted.clear();renderer.render(context,"hold",1,1,0,false);budget.flushAccents();
        assertEquals(200,emitted.size());assertEquals(0,budget.remaining());assertFalse(emitted.contains(Particle.GLOW));
    }
    @Test void resettingBudgetDiscardsDeferredLocations() {
        var budget=new RenderBudget();budget.reset(10);budget.deferAccent(1,()->fail("stale accent"));budget.reset(10);budget.flushAccents();assertEquals(10,budget.remaining());
    }
}
