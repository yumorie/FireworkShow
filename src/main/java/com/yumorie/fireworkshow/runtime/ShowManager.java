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
import com.yumorie.fireworkshow.spatial.*;
public final class ShowManager {
    private record Session(ShowContext context,List<ShowContext> clips,TimelineEngine engine,boolean preview) {}
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
    public void validateRotation(ShowDefinition definition,Rotation3D rotation) {
        var root=SpatialContext.at(Vec3.ZERO,0,rotation);
        for(var track:definition.tracks()) for(var clip:track.clips()) {
            var config=new YamlConfiguration();
            try { config.loadFromString(clip.step().yaml()); } catch(Exception error) { throw new IllegalStateException("Invalid validated step",error); }
            steps.factory(clip.step().type()).validateSpatial(config,root.child(track.transform()).child(clip.transform()));
        }
    }
    public ShowHandle play(String id,Location location,Player focus,boolean preview,Rotation3D rotation) {
        if(!Bukkit.isPrimaryThread()) throw new IllegalStateException("Shows require the server main thread");
        var def=snapshot.shows().get(id);
        if(def==null) throw new IllegalArgumentException("Unknown show: "+id);
        var settings=snapshot.settings();
        UUID owner=focus==null?null:focus.getUniqueId();
        if(sessions.size()>=settings.maxActive() || owner!=null && sessions.stream().filter(s->owner.equals(s.context.owner)).count()>=settings.maxPerPlayer()) throw new IllegalArgumentException("Active show limit reached");
        int planned=def.tracks().stream().mapToInt(t->t.clips().size()).sum();
        if(planned+sessions.stream().mapToInt(s->s.clips.size()).sum()>settings.maxClips()) throw new IllegalArgumentException("Global clip limit reached (includes scheduled clips)");
        if(location.getWorld()==null || !Double.isFinite(location.getX()) || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ())) throw new IllegalArgumentException("Invalid show location");
        var origin=location.clone();
        origin.setYaw(def.yaw()); origin.setPitch(0);
        if(focus!=null && def.orientation().equals("face-trigger")) {
            var delta=location.toVector().subtract(focus.getLocation().toVector());
            origin.setYaw((float)Math.toDegrees(Math.atan2(-delta.getX(),delta.getZ())));
        }
        var spatial=SpatialContext.at(new Vec3(origin.getX(),origin.getY(),origin.getZ()),origin.getYaw(),rotation);
        var context=new ShowContext(def,settings,origin,owner,budget,adapter,null,spatial,UUID.randomUUID().getMostSignificantBits());
        var contexts=new ArrayList<ShowContext>();
        var clips=new ArrayList<TimelineEngine.ScheduledClip>();
        for(var track:def.tracks()) for(int index=0;index<track.clips().size();index++) {
            var clip=track.clips().get(index);var d=clip.step();
            var c=new YamlConfiguration();
            try { c.loadFromString(d.yaml()); } catch(Exception e) { throw new IllegalStateException("Invalid validated step",e); }
            var child=spatial.child(track.transform()).child(clip.transform());
            var factory=steps.factory(d.type());factory.validateSpatial(c,child);
            if(d.type().equals("particlelib") && child.requestsRotation()) plugin.getLogger().warning("Show "+id+" clip "+track.id()+"/"+index+": ParticleLib ORIGIN_ONLY; position transformed, internal shape rotation is not supported");
            long seed=ShowContext.imageSeed(context.seed,clip.image());
            var clipContext=new ShowContext(def,settings,origin,owner,budget,adapter,clip.image(),child,seed);
            contexts.add(clipContext);
            clips.add(new TimelineEngine.ScheduledClip(track.id()+"/"+index,clip.atTick(),d.duration(),factory.create(c,clipContext)));
        }
        var engine=TimelineEngine.scheduled(clips);
        sessions.add(new Session(context,List.copyOf(contexts),engine,preview));
        return new ShowHandle(engine,()-> { if(!Bukkit.isPrimaryThread()) throw new IllegalStateException("Show handles require main thread"); });
    }
    private void tick() {
        budget.reset(snapshot.settings().budget());
        for(var s:sessions) for(var clip:s.clips) budget.reserve(clip.reservedParticles);
        for(var it=sessions.iterator();it.hasNext();) {
            var s=it.next();
            try {
                if(Bukkit.getWorld(s.context.origin().getWorld().getUID())==null || s.context.owner!=null && Bukkit.getPlayer(s.context.owner)==null) s.engine.stop();
                else { s.engine.tick();s.context.tick++;for(var clip:s.clips) clip.tick++; }
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
