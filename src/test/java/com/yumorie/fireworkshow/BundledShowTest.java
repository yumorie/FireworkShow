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
        var image=ImageIO.read(logo.toFile());assertEquals(1254,image.getWidth());assertEquals(1254,image.getHeight());assertTrue(image.getColorModel().hasAlpha());
        Files.writeString(root.resolve("shows/multitrack.yml"),"user configuration");BundledResources.install(root);
        assertEquals("user configuration",Files.readString(root.resolve("shows/multitrack.yml")));
    }
    @Test void allCommentedYamlLoadsAndLogoStagesShareFinalSizeVariant() throws Exception {
        BundledResources.install(root);var snapshot=loader().load(root);assertEquals(4,snapshot.shows().size());
        for(String name:BundledResources.FILES)if(name.endsWith(".yml")) {
            var lines=Files.readAllLines(root.resolve(name));assertTrue(Files.size(root.resolve(name))<65536);
            for(int i=0;i<lines.size();i++) {
                String line=lines.get(i).stripLeading();assertFalse(line.contains("\ufffd"));
                if(!line.isEmpty()&&!line.startsWith("#")) {
                    assertTrue(i>0&&lines.get(i-1).stripLeading().startsWith("#"),name+":"+(i+1));
                    assertTrue(lines.get(i-1).matches(".*[\u4e00-\u9fff].*"));
                }
            }
        }
        var show=snapshot.shows().get("multitrack");assertEquals(Set.of("heart","text","logo"),show.images().keySet());assertEquals(4,show.tracks().size());
        var track=show.tracks().getLast();assertEquals("logo",track.id());assertEquals(.75,track.transform().scale());
        assertEquals(7.6,track.transform().apply(com.yumorie.fireworkshow.spatial.Vec3.ZERO).y());
        var clips=track.clips();assertEquals(List.of(40,80,140),clips.stream().map(ShowDefinition.ClipDefinition::atTick).toList());
        assertEquals(List.of(40,60,40),clips.stream().map(c->c.step().duration()).toList());
        var image=clips.getFirst().sampledImage();assertEquals(3.75,image.sampling().effectiveWidth());assertTrue(image.cloud().points().size()>0);assertTrue(image.cloud().points().size()<=350);
        for(var clip:clips)assertSame(image.cloud(),clip.sampledImage().cloud());
        assertNotSame(show.images().get("logo").cloud(),image.cloud());
        assertTrue(show.tracks().stream().filter(t->!t.id().equals("logo")).flatMap(t->t.clips().stream()).anyMatch(c->c.atTick()<80&&c.atTick()+c.step().duration()>40));
        System.out.println("BUNDLED_SAMPLING logoWidth="+image.sampling().effectiveWidth()+" points="+image.cloud().points().size()+" glow="+image.glow().select(image.cloud()).size()+" stats="+snapshot.preprocessing());
    }
    @Test void invalidNewImageOrSamplingConfigurationRejectsReload() throws Exception {
        BundledResources.install(root);var loader=loader();var old=loader.load(root);var file=root.resolve("shows/multitrack.yml");String valid=Files.readString(file);
        for(String invalid:List.of(valid.replace("brightness: 1.4","brightness: .nan"),valid.replace("density: 0.03","density: 0.3"),valid.replace("particle-spacing: 0.12","particle-spacing: 0"))) {
            Files.writeString(file,invalid);assertThrows(Exception.class,()->loader.load(root));assertEquals(4,old.shows().size());
        }
        Files.writeString(file,valid);Files.writeString(root.resolve("images/mczjulogo.png"),"not PNG");assertThrows(Exception.class,()->loader.load(root));
        assertFalse(old.shows().get("multitrack").images().get("logo").cloud().points().isEmpty());
    }
    @Test void clipVariantsUseParentScaleAndIgnoreRotationForSampling() throws Exception {
        BundledResources.install(root);
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
        assertEquals(1600,clips.getFirst().sampledImage().cloud().points().size());
        assertSame(clips.getFirst().sampledImage().cloud(),clips.get(1).sampledImage().cloud());
        assertEquals(400,clips.getLast().sampledImage().cloud().points().size());
        assertEquals(2,snapshot.cachedClouds());assertEquals(1,snapshot.preprocessing().decodes());
    }
}
