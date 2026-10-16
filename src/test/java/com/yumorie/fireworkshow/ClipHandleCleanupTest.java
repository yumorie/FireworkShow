package com.yumorie.fireworkshow;

import com.yumorie.fireworkshow.config.*;
import com.yumorie.fireworkshow.integration.particlelib.ParticleLibAdapter;
import com.yumorie.fireworkshow.render.RenderBudget;
import com.yumorie.fireworkshow.runtime.*;
import com.yumorie.fireworkshow.spatial.*;
import com.yumorie.fireworkshow.step.StepRegistry;
import me.sunmc.particlelib.api.ParticleLibAPI;
import me.sunmc.particlelib.api.effect.EffectHandle;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClipHandleCleanupTest {
    static final class Handle implements EffectHandle {
        int cancellations;boolean done;
        public void cancel(){ if(!done){done=true;cancellations++;} }
        public void pause(){}public void resume(){}public boolean isActive(){return !done;}public boolean isPaused(){return false;}public boolean isDone(){return done;}
    }
    @Test void actualLibraryStepsOwnSeparateHandlesAndReservationsAcrossOverlappingTracks() {
        List<Player> players=new ArrayList<>();
        World world=(World)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{World.class},(p,m,a)->switch(m.getName()){
            case "getPlayers" -> players;case "equals" -> p==a[0];case "hashCode" -> 1;case "getName" -> "test";default -> throw new UnsupportedOperationException(m.getName());
        });
        Player viewer=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()){
            case "isOnline" -> true;case "getLocation" -> new Location(world,0,0,0);case "equals" -> p==a[0];case "hashCode" -> 2;default -> throw new UnsupportedOperationException(m.getName());
        });players.add(viewer);
        List<Handle> owned=new ArrayList<>();List<Location> origins=new ArrayList<>();
        var api=(ParticleLibAPI)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ParticleLibAPI.class},(p,m,a)-> {
            if(!m.getName().equals("play")) throw new UnsupportedOperationException(m.getName());
            origins.add(((Location)a[1]).clone());var h=new Handle();owned.add(h);return h;
        });
        var adapter=new ParticleLibAdapter(api);var registry=StepRegistry.defaults(adapter);
        var settings=Settings.load(new YamlConfiguration());var definition=new ShowDefinition("test","Test",Map.of(),"fixed",0,List.of());
        var budget=new RenderBudget();budget.reset(1500);var space=SpatialContext.at(Vec3.ZERO,0,Rotation3D.ZERO);
        var a=new ShowContext(definition,settings,new Location(world,0,0,0),null,budget,adapter,null,space,1);
        var b=new ShowContext(definition,settings,new Location(world,0,0,0),null,budget,adapter,null,space.child(Transform3D.of(new Vec3(2,0,0),Rotation3D.ZERO)),2);
        var config=new YamlConfiguration();config.set("effect.class","tornado");
        var engine=TimelineEngine.scheduled(List.of(
                new TimelineEngine.ScheduledClip("a/0",0,20,registry.factory("particlelib").create(config,a)),
                new TimelineEngine.ScheduledClip("b/0",0,30,registry.factory("particlelib").create(config,b))));
        engine.tick();assertEquals(2,owned.size());assertEquals(2,engine.activeClips());assertTrue(a.reservedParticles>0);assertTrue(b.reservedParticles>0);
        assertEquals(0,origins.getFirst().getX());assertEquals(-2,origins.get(1).getX(),1e-9);
        engine.stopClip("a/0");assertEquals(1,owned.getFirst().cancellations);assertFalse(owned.get(1).done);assertEquals(0,a.reservedParticles);assertTrue(b.reservedParticles>0);
        engine.stop();engine.stop();assertEquals(1,owned.get(1).cancellations);assertEquals(0,b.reservedParticles);
        // Starting a second track cannot obtain a fresh budget or leak the already-started first effect.
        budget.reset(300);
        var limited=TimelineEngine.scheduled(List.of(
                new TimelineEngine.ScheduledClip("a/0",0,20,registry.factory("particlelib").create(config,a)),
                new TimelineEngine.ScheduledClip("b/0",0,30,registry.factory("particlelib").create(config,b))));
        assertThrows(IllegalStateException.class,limited::tick);assertTrue(limited.done());
        assertEquals(3,owned.size());assertEquals(1,owned.get(2).cancellations);assertEquals(0,a.reservedParticles);assertEquals(0,b.reservedParticles);
    }
}
