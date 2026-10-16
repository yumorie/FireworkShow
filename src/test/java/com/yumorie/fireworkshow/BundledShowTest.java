package com.yumorie.fireworkshow;

import com.yumorie.fireworkshow.config.*;
import com.yumorie.fireworkshow.image.*;
import com.yumorie.fireworkshow.step.StepRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BundledShowTest {
    @TempDir Path root;
    ShowLoader loader() { return new ShowLoader(StepRegistry.defaults(new ParticleLibTest().adapter())); }
    @Test void installsLogoExactlyAndNeverOverwritesUserFiles() throws Exception {
        BundledResources.install(root);Path logo=root.resolve("images/mczjulogo.png");
        try(var input=getClass().getResourceAsStream("/images/mczjulogo.png")) { assertArrayEquals(input.readAllBytes(),Files.readAllBytes(logo)); }
        var image=ImageIO.read(logo.toFile());assertTrue(image.getWidth()>0);assertTrue(image.getHeight()>0);assertTrue(image.getColorModel().hasAlpha());
        Files.writeString(root.resolve("shows/multitrack.yml"),"user configuration");BundledResources.install(root);
        assertEquals("user configuration",Files.readString(root.resolve("shows/multitrack.yml")));
    }
    @Test void currentBundledConfigurationLoadsWithoutPinningArtisticSettings() throws Exception {
        BundledResources.install(root);
        var snapshot=loader().load(root);
        try(var files=Files.list(root.resolve("shows"))) {
            for(var file:files.filter(p->p.toString().endsWith(".yml")).toList()) {
                var yaml=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());
                var show=snapshot.shows().get(yaml.getString("id"));assertNotNull(show);
                assertFalse(Files.readString(file).contains("\ufffd"));
                for(var track:show.tracks())for(var clip:track.clips())if(clip.image()!=null) {
                    var resource=show.images().get(clip.image());assertNotNull(resource);
                    assertSame(resource.cloud(),clip.sampledImage().cloud());
                    double expected=resource.sampling().width()*track.transform().scale()*clip.transform().scale();
                    assertEquals(expected,clip.sampledImage().sampling().effectiveWidth(),1e-9);
                }
            }
        }
    }
    @Test void fixtureLayoutAndStagesShareRawCloud() throws Exception {
        TestFixtures.install(root);var snapshot=loader().load(root);assertEquals(4,snapshot.shows().size());
        var show=snapshot.shows().get("multitrack");assertEquals(Set.of("heart","text","logo"),show.images().keySet());assertEquals(4,show.tracks().size());
        var track=show.tracks().getLast();assertEquals("logo",track.id());assertEquals(.75,track.transform().scale());
        assertEquals(7.6,track.transform().apply(com.yumorie.fireworkshow.spatial.Vec3.ZERO).y());
        var clips=track.clips();assertEquals(List.of(40,80,140),clips.stream().map(ShowDefinition.ClipDefinition::atTick).toList());
        assertEquals(List.of(40,60,40),clips.stream().map(c->c.step().duration()).toList());
        var yaml=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(root.resolve("shows/multitrack.yml").toFile());
        double expectedWidth=yaml.getDouble("images.logo.width-blocks",5)*track.transform().scale()*clips.getFirst().transform().scale();
        var image=clips.getFirst().sampledImage();assertEquals(expectedWidth,image.sampling().effectiveWidth(),1e-9);assertTrue(image.cloud().points().size()>0);assertTrue(image.cloud().points().size()>350);
        for(var clip:clips)assertSame(image.cloud(),clip.sampledImage().cloud());
        assertSame(show.images().get("logo").cloud(),image.cloud());
        assertTrue(show.tracks().stream().filter(t->!t.id().equals("logo")).flatMap(t->t.clips().stream()).anyMatch(c->c.atTick()<80&&c.atTick()+c.step().duration()>40));
        System.out.println("BUNDLED_RAW_PIXELS logoWidth="+image.sampling().effectiveWidth()+" points="+image.cloud().points().size()+" glow="+image.glow().select(image.cloud()).size()+" stats="+snapshot.preprocessing());
    }
    @Test void invalidNewImageOrSamplingConfigurationRejectsReload() throws Exception {
        TestFixtures.install(root);var loader=loader();var old=loader.load(root);var file=root.resolve("shows/multitrack.yml");String valid=Files.readString(file);
        for(String invalid:List.of(valid.replace("brightness: 1.0","brightness: .nan"),valid.replace("density: 0.03","density: 0.3"),valid.replace("alpha-threshold: 16","alpha-threshold: -1"))) {
            Files.writeString(file,invalid);assertThrows(Exception.class,()->loader.load(root));assertEquals(4,old.shows().size());
        }
        Files.writeString(file,valid);Files.writeString(root.resolve("images/mczjulogo.png"),"not PNG");assertThrows(Exception.class,()->loader.load(root));
        assertFalse(old.shows().get("multitrack").images().get("logo").cloud().points().isEmpty());
    }
    @Test void clipScalesAndRotationsDoNotSplitRawCloud() throws Exception {
        TestFixtures.install(root);
        try(var files=Files.list(root.resolve("shows"))) { for(var file:files.toList())Files.delete(file); }
        Files.writeString(root.resolve("config.yml"),"image: {max-points: 5000}\ndisplay: {max-particles-per-tick: 10000}\n");
        var source=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<32;y++)for(int x=0;x<32;x++)source.setRGB(x,y,0xff123456);
        ImageIO.write(source,"PNG",root.resolve("images/solid.png").toFile());
        Files.writeString(root.resolve("shows/sized.yml"),"""
                id: sized
                images:
                  solid: {file: solid.png, width-blocks: 8, particle-spacing: 0.2, max-points: 5000}
                tracks:
                  main:
                    transform: {scale: 2, rotation: {z: 60}}
                    clips:
                      - {at-tick: 0, type: reveal, image: solid, duration-ticks: 10, transform: {scale: 0.5}}
                      - {at-tick: 10, type: hold, image: solid, duration-ticks: 10, transform: {scale: 0.5, rotation: {x: 90}}}
                      - {at-tick: 20, type: scatter, image: solid, duration-ticks: 10, transform: {scale: 0.25}}
                """);
        var snapshot=loader().load(root);var clips=snapshot.shows().get("sized").tracks().getFirst().clips();
        assertEquals(1024,clips.getFirst().sampledImage().cloud().points().size());
        assertSame(clips.getFirst().sampledImage().cloud(),clips.get(1).sampledImage().cloud());
        assertEquals(1024,clips.getLast().sampledImage().cloud().points().size());
        assertEquals(1,snapshot.cachedClouds());assertEquals(1,snapshot.preprocessing().decodes());
    }
}
