package com.yumorie.fireworkshow;
import com.yumorie.fireworkshow.config.*;
import com.yumorie.fireworkshow.step.*;
import com.yumorie.fireworkshow.runtime.ShowContext;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ConfigTest {
    @TempDir Path root;
    StepRegistry registry() { var r=new StepRegistry();for(String type:new String[]{"reveal","hold","scatter","custom"})r.register(type,new StepFactory(){public void validate(ConfigurationSection c){}public ShowStep create(ConfigurationSection c,ShowContext context){return p->{};}});return r; }
    void setup() throws Exception {
        Files.createDirectories(root.resolve("images"));Files.createDirectories(root.resolve("shows"));
        for(String resource:new String[]{"config.yml","images/heart.png","shows/alternate-heart.yml"})try(var in=getClass().getResourceAsStream("/"+resource)){Files.copy(in,root.resolve(resource));}
    }
    @Test void snapshotAndCloudCacheSurviveFailedReload() throws Exception {
        setup();var loader=new ShowLoader(registry());var old=loader.load(root);assertEquals(1,old.shows().size());
        var text=Files.readString(root.resolve("shows/alternate-heart.yml"));
        Files.writeString(root.resolve("shows/copy.yml"),text.replace("id: alternate-heart","id: second"));
        var next=loader.load(root);assertEquals(2,next.shows().size());assertEquals(1,next.cachedClouds());assertSame(next.shows().get("second").cloud(),next.shows().get("alternate-heart").cloud());
        Files.writeString(root.resolve("shows/copy.yml"),text);assertThrows(Exception.class,()->loader.load(root));assertEquals(1,old.shows().size());
    }
    @Test void invalidFieldsAndUnknownTypesAreRejected() throws Exception {
        setup();var loader=new ShowLoader(registry());Path p=root.resolve("shows/alternate-heart.yml");String valid=Files.readString(p);
        for(String invalid:new String[]{valid.replace("type: reveal","type: unknown"),valid.replace("duration-ticks: 80","duration-ticks: 0"),valid.replace("width-blocks: 5.0","width-blocks: .nan"),valid.replace("file: heart.png","file: ../heart.png"),"id: missing\n"}) {
            Files.writeString(p,invalid);assertThrows(Exception.class,()->loader.load(root));
        }
    }
    @Test void customRegistryTypeNeedsNoDispatchChanges() { var r=registry();assertNotNull(r.factory("custom").create(null,null));assertThrows(Exception.class,()->r.factory("unknown")); }
}
