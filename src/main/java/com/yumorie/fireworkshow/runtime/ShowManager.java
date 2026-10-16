package com.yumorie.fireworkshow.runtime;
import com.yumorie.fireworkshow.config.*;
import com.yumorie.fireworkshow.integration.particlelib.ParticleLibAdapter;
import com.yumorie.fireworkshow.render.RenderBudget;
import com.yumorie.fireworkshow.step.StepRegistry;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
public final class ShowManager {
    private record Session(ShowContext context,TimelineEngine engine,boolean preview) {}
    private final JavaPlugin plugin;
    private final StepRegistry steps;
    private final ParticleLibAdapter adapter;
    private final RenderBudget budget=new RenderBudget();
    private final List<Session> sessions=new ArrayList<>();
    private ShowLoader.Snapshot snapshot;
    private final BukkitTask task;
    public ShowManager(JavaPlugin plugin,StepRegistry steps,ParticleLibAdapter adapter,ShowLoader.Snapshot snapshot) {
        this.plugin=plugin;this.steps=steps;this.adapter=adapter;this.snapshot=snapshot;
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    public ShowLoader.Snapshot snapshot() { return snapshot; }
    public void replace(ShowLoader.Snapshot next) { snapshot=next; }
    public int active() { return sessions.size(); }
    public void play(String id,Location location,Player focus,boolean preview) {
        if(!Bukkit.isPrimaryThread()) throw new IllegalStateException("Shows require the server main thread");
        var def=snapshot.shows().get(id);
        if(def==null) throw new IllegalArgumentException("Unknown show: "+id);
        var settings=snapshot.settings();
        UUID owner=focus==null?null:focus.getUniqueId();
        if(sessions.size()>=settings.maxActive() || owner!=null && sessions.stream().filter(s->owner.equals(s.context.owner)).count()>=settings.maxPerPlayer()) throw new IllegalArgumentException("Active show limit reached");
        if(location.getWorld()==null || !Double.isFinite(location.getX()) || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ())) throw new IllegalArgumentException("Invalid show location");
        var origin=location.clone();
        origin.setYaw(def.yaw()); origin.setPitch(0);
        if(focus!=null && def.orientation().equals("face-trigger")) {
            var delta=location.toVector().subtract(focus.getLocation().toVector());
            origin.setYaw((float)Math.toDegrees(Math.atan2(-delta.getX(),delta.getZ())));
        }
        var context=new ShowContext(def,settings,origin,owner,budget,adapter);
        var stages=new ArrayList<TimelineEngine.Stage>();
        for(var d:def.steps()) {
            var c=new YamlConfiguration();
            try { c.loadFromString(d.yaml()); } catch(Exception e) { throw new IllegalStateException("Invalid validated step",e); }
            stages.add(new TimelineEngine.Stage(d.duration(),steps.factory(d.type()).create(c,context)));
        }
        sessions.add(new Session(context,new TimelineEngine(stages),preview));
    }
    private void tick() {
        budget.reset(snapshot.settings().budget());
        for(var s:sessions) budget.reserve(s.context.reservedParticles);
        for(var it=sessions.iterator();it.hasNext();) {
            var s=it.next();
            try {
                if(Bukkit.getWorld(s.context.origin().getWorld().getUID())==null || s.context.owner!=null && Bukkit.getPlayer(s.context.owner)==null) s.engine.stop();
                else { s.engine.tick();s.context.tick++; }
            } catch(RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,"Show "+s.context.definition.id()+" stopped after playback error",e);
                try { s.engine.stop(); } catch(RuntimeException cleanup) { plugin.getLogger().log(java.util.logging.Level.SEVERE,"Effect cleanup failed",cleanup); }
            }
            if(s.engine.done()) it.remove();
        }
    }
    public void stop(UUID owner) {
        for(var it=sessions.iterator();it.hasNext();) { var s=it.next();if(owner==null || s.preview && owner.equals(s.context.owner)) {
            try { s.engine.stop(); } catch(RuntimeException e) { plugin.getLogger().log(java.util.logging.Level.SEVERE,"Failed to stop show "+s.context.definition.id(),e); } finally { it.remove(); }
        } }
    }
    public void close() { try { stop(null); } finally { task.cancel(); } }
}
