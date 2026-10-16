package com.yumorie.fireworkshow;

import com.yumorie.fireworkshow.config.*;
import com.yumorie.fireworkshow.runtime.ShowContext;
import com.yumorie.fireworkshow.render.RenderBudget;
import com.yumorie.fireworkshow.spatial.*;
import com.yumorie.fireworkshow.step.StepRegistry;
import org.bukkit.Location;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiImageConfigTest {
    @TempDir Path root;
    ShowLoader loader() { return new ShowLoader(StepRegistry.defaults(new ParticleLibTest().adapter())); }
    void setup(String... shows) throws Exception {
        Files.createDirectories(root.resolve("images"));Files.createDirectories(root.resolve("shows"));
        for(String name:List.of("config.yml","images/heart.png","images/text.png","images/mczjulogo.png"))try(var in=TestFixtures.open(name)){Files.copy(in,root.resolve(name));}
        for(String show:shows)try(var in=TestFixtures.open("shows/"+show+".yml")){
            Path file=root.resolve("shows/"+show+".yml");Files.copy(in,file);
            // Keep mutation fixtures independent of documentation lines; bundled-comment parsing has its own integration test.
            Files.writeString(file,String.join("\n",Files.readAllLines(file).stream().filter(line->!line.stripLeading().startsWith("#")).toList())+"\n");
        }
    }
    @Test void namedImagesAndTracksLoadTogether() throws Exception {
        setup("heart","alternate-heart","multi-image","multitrack");
        var snapshot=loader().load(root);assertEquals(4,snapshot.shows().size());
        var heart=snapshot.shows().get("heart");assertEquals(1,heart.tracks().size());
        assertEquals(List.of(0,30,70,110),heart.tracks().getFirst().clips().stream().map(ShowDefinition.ClipDefinition::atTick).toList());
        var sequential=snapshot.shows().get("multi-image");assertEquals(List.of("heart","heart","heart","text","text","text"),sequential.tracks().getFirst().clips().stream().map(ShowDefinition.ClipDefinition::image).toList());
        var multi=snapshot.shows().get("multitrack");assertEquals(4,multi.tracks().size());assertEquals("text",multi.tracks().get(2).clips().getFirst().image());
        assertNotEquals(multi.images().get("heart").cloud(),multi.images().get("text").cloud());
        assertThrows(UnsupportedOperationException.class,()->multi.images().clear());
    }
    @Test void identicalResourceConfigurationSharesCloudButKeepsDustIndependent() throws Exception {
        setup("multi-image");Path file=root.resolve("shows/multi-image.yml");
        String yaml=Files.readString(file).replace("\r\n","\n").replace("file: text.png","file: heart.png").replace("width-blocks: 9.5","width-blocks: 5");Files.writeString(file,yaml);
        var snapshot=loader().load(root);var images=snapshot.shows().get("multi-image").images();
        assertEquals(1,snapshot.cachedClouds());assertSame(images.get("heart").cloud(),images.get("text").cloud());
        assertNotEquals(images.get("heart").dust(),images.get("text").dust());
        Files.writeString(file,yaml.replace("width-blocks: 5\n    max-points: 600\n    dust-size: 0.8","width-blocks: 7\n    max-points: 600\n    dust-size: 0.8"));
        assertEquals(2,loader().load(root).cachedClouds());
    }
    @Test void wrongOrAmbiguousImageReferenceRejectsEntireReload() throws Exception {
        setup("multi-image");var loader=loader();var old=loader.load(root);Path file=root.resolve("shows/multi-image.yml");String valid=Files.readString(file).replace("\r\n","\n");
        for(String invalid:List.of(valid.replace("image: text","image: missing"),valid.replace("        image: heart\n",""),valid.replace("file: text.png","file: absent.png"),valid.replace("dust-size: 0.8","dust-size: .nan"))) {
            Files.writeString(file,invalid);assertThrows(Exception.class,()->loader.load(root));assertEquals(2,old.shows().get("multi-image").images().size());
        }
    }
    @Test void rotationsInheritAndStrictLibraryPolicyFailsAtLoad() throws Exception {
        setup("multitrack");Path file=root.resolve("shows/multitrack.yml");String valid=Files.readString(file).replace("\r\n","\n");
        var show=loader().load(root).shows().get("multitrack");var text=show.tracks().get(2);var clip=text.clips().getFirst();
        var spatial=SpatialContext.at(Vec3.ZERO,0,Rotation3D.ZERO).child(text.transform()).child(clip.transform());
        SpatialTest.vector(new Vec3(-4.5,2.5,-0.5),spatial.apply(Vec3.ZERO));
        Files.writeString(file,valid.replace("position: {x: 0, y: -2, z: 0}","position: {x: 0, y: -2, z: 0}\n      rotation: {x: 90, y: 0, z: 0}").replace("rotation-policy: origin-only","rotation-policy: require-full"));
        var error=assertThrows(Exception.class,()->loader().load(root));assertTrue(error.getMessage().contains("ORIGIN_ONLY"));
    }
    @Test void unsupportedFormatsAreRejected() throws Exception {
        setup();Path file=root.resolve("shows/invalid.yml");
        for(String yaml:List.of(
            "id: invalid\nimage: {file: heart.png}\ntimeline: []\n",
            "id: invalid\nimages: {}\ntracks: []\n",
            "id: invalid\nimages: {}\ntracks:\n  main:\n    transform: {position: [1, 2, 3]}\n    clips: [{at-tick: 0, type: hold, image: heart, duration-ticks: 10}]\n")) {
            Files.writeString(file,yaml);assertThrows(Exception.class,()->loader().load(root));
        }
    }
    @Test void differentImagesHaveSeparateDeterministicAnimationState() throws Exception {
        setup("multi-image");Path file=root.resolve("shows/multi-image.yml");
        Files.writeString(file,Files.readString(file).replace("\r\n","\n").replace("file: text.png","file: heart.png").replace("width-blocks: 9.5","width-blocks: 5"));
        var snapshot=loader().load(root);var show=snapshot.shows().get("multi-image");
        var space=SpatialContext.at(Vec3.ZERO,0,Rotation3D.ZERO);var budget=new RenderBudget();
        var a=new ShowContext(show,snapshot.settings(),new Location(null,0,0,0),null,budget,null,"heart",space,ShowContext.imageSeed(42,"heart"));
        var b=new ShowContext(show,snapshot.settings(),new Location(null,0,0,0),null,budget,null,"text",space,ShowContext.imageSeed(42,"text"));
        assertNotEquals(a.seed,b.seed);
        int heartIndex=a.image.cloud().points().getFirst().index(),textIndex=b.image.cloud().points().getFirst().index();
        assertEquals(heartIndex,textIndex);assertSame(a.image.cloud(),b.image.cloud());
        var before=b.direction(textIndex);a.direction(heartIndex)[0]=999;assertArrayEquals(before,b.direction(textIndex));
        assertFalse(Arrays.equals(a.direction(heartIndex),b.direction(textIndex)));
    }
    @Test void unsafeTrackTimesAndInvalidTransformsAreRejected() throws Exception {
        setup("multitrack");Path file=root.resolve("shows/multitrack.yml");String valid=Files.readString(file).replace("\r\n","\n");
        for(String invalid:List.of(valid.replace("at-tick: 20","at-tick: -1"),valid.replace("at-tick: 130","at-tick: 23999"),valid.replace("z: -8","z: .inf"),valid.replace("x: 4.5, y: 2.5","x: 129, y: 2.5"))) {
            Files.writeString(file,invalid);assertThrows(Exception.class,()->loader().load(root));
        }
    }
    @Test void duplicateNamedImagesOrTracksAreRejectedByYamlLoader() throws Exception {
        setup();Path file=root.resolve("shows/duplicate.yml");
        Files.writeString(file,"""
                id: duplicate
                images:
                  heart: {file: heart.png}
                  heart: {file: text.png}
                tracks:
                  main:
                    clips:
                      - {at-tick: 0, type: hold, image: heart, duration-ticks: 10}
                """);
        assertThrows(Exception.class,()->loader().load(root));
        Files.writeString(file,"""
                id: duplicate
                images: {heart: {file: heart.png}}
                tracks:
                  heart:
                    clips:
                      - {at-tick: 0, type: hold, duration-ticks: 10}
                  heart:
                    clips:
                      - {at-tick: 0, type: hold, duration-ticks: 10}
                """);
        assertThrows(Exception.class,()->loader().load(root));
    }
    @Test void trackAndClipScalesLoadWithoutSplittingResourceCache() throws Exception {
        setup("multi-image");
        String yaml=Files.readString(root.resolve("shows/multi-image.yml")).replace("\r\n","\n")
                .replace("id: multi-image","id: scaled")
                .replace("  main:\n    clips:","  main:\n    transform: {scale: 2}\n    clips:")
                .replace("        type: reveal","        transform: {position: {y: 1}, scale: 0.5}\n        type: reveal");
        Files.writeString(root.resolve("shows/scaled.yml"),yaml);
        var snapshot=loader().load(root);assertEquals(2,snapshot.cachedClouds());
        var original=snapshot.shows().get("multi-image");var scaled=snapshot.shows().get("scaled");
        assertSame(original.images().get("heart").cloud(),scaled.images().get("heart").cloud());
        assertSame(original.images().get("text").cloud(),scaled.images().get("text").cloud());
        assertSame(original.tracks().getFirst().clips().getFirst().sampledImage().cloud(),scaled.tracks().getFirst().clips().getFirst().sampledImage().cloud());
        assertSame(scaled.tracks().getFirst().clips().getFirst().sampledImage().cloud(),scaled.tracks().getFirst().clips().get(1).sampledImage().cloud());
        var track=scaled.tracks().getFirst();var clip=track.clips().getFirst();
        assertEquals(2,track.transform().scale());assertEquals(0.5,clip.transform().scale());
        var space=SpatialContext.at(Vec3.ZERO,0,Rotation3D.ZERO).child(track.transform()).child(clip.transform());
        SpatialTest.vector(new Vec3(0,2,0),space.apply(Vec3.ZERO));assertEquals(1,space.world().scale());
    }
}
