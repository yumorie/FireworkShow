package com.yumorie.fireworkshow;
import com.yumorie.fireworkshow.config.*;
import com.yumorie.fireworkshow.integration.particlelib.ParticleLibAdapter;
import com.yumorie.fireworkshow.step.*;
import com.yumorie.fireworkshow.runtime.ShowContext;
import com.yumorie.fireworkshow.render.RenderBudget;
import me.sunmc.particlelib.api.ParticleLibAPI;
import me.sunmc.particlelib.api.effect.EffectHandle;
import me.sunmc.particlelib.effects.motion.TornadoEffect;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ParticleLibTest {
    @TempDir Path root;
    ParticleLibAdapter adapter() {
        var api=(ParticleLibAPI)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ParticleLibAPI.class},(p,m,a)->{
            if(m.getName().equals("builder")) return a[0].equals("tornado")?Optional.of(TornadoEffect.builder()):Optional.empty();
            throw new UnsupportedOperationException(m.getName());
        });return new ParticleLibAdapter(api);
    }
    @Test void actualTornadoBuilderAcceptsBundledShowAndNestedEffect() throws Exception {
        Files.createDirectories(root.resolve("images"));Files.createDirectories(root.resolve("shows"));
        for(String name:List.of("config.yml","images/heart.png","shows/heart.yml","shows/alternate-heart.yml"))try(var in=getClass().getResourceAsStream("/"+name)){Files.copy(in,root.resolve(name));}
        var registry=StepRegistry.defaults(adapter());
        var loaded=new ShowLoader(registry).load(root);
        assertEquals(2,loaded.shows().size());
        var step=new YamlConfiguration();step.loadFromString(loaded.shows().get("heart").steps().getFirst().yaml());
        assertEquals("tornado",step.getConfigurationSection("effect").getString("class"));
        step.set("effect.height",9);
        assertThrows(IllegalArgumentException.class,()->registry.factory("particlelib").validate(step));
    }
    @Test void adapterCancelsPartialPlaybackWhenLibraryReturnsTerminatedHandle() {
        int[] cancelled={0},played={0};
        EffectHandle active=new EffectHandle(){public void cancel(){cancelled[0]++;}public void pause(){}public void resume(){}public boolean isActive(){return true;}public boolean isPaused(){return false;}public boolean isDone(){return false;}};
        var api=(ParticleLibAPI)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ParticleLibAPI.class},(p,m,a)->++played[0]==1?active:EffectHandle.terminated());
        var adapter=new ParticleLibAdapter(api);var config=new YamlConfiguration();config.set("class","tornado");
        // The test API ignores target players; null entries avoid simulating a Paper server.
        assertThrows(IllegalStateException.class,()->adapter.play(config,new org.bukkit.Location(null,0,0,0),Arrays.asList(null,null),48));
        assertEquals(1,cancelled[0]);
    }
    @Test void renderBudgetRejectsOverruns() {
        var b=new RenderBudget();b.reset(10);assertTrue(b.take(6));assertFalse(b.take(5));assertEquals(4,b.remaining());
    }
}
