package com.yumorie.fireworkshow.runtime;
import com.yumorie.fireworkshow.config.*;
import com.yumorie.fireworkshow.render.RenderBudget;
import com.yumorie.fireworkshow.integration.particlelib.ParticleLibAdapter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.*;
public final class ShowContext {
    public final ShowDefinition definition;
    public final Settings settings;
    private final Location origin;
    public final UUID owner;
    public final long seed;
    public final RenderBudget budget;
    public final ParticleLibAdapter adapter;
    private final Map<Integer,double[]> directions=new HashMap<>();
    public int tick;
    public int reservedParticles;
    public ShowContext(ShowDefinition definition,Settings settings,Location origin,UUID owner,RenderBudget budget,ParticleLibAdapter adapter) {
        this.definition=definition; this.settings=settings; this.origin=origin.clone(); this.owner=owner;this.budget=budget;this.adapter=adapter;
        seed=UUID.randomUUID().getMostSignificantBits();
        for(var point:definition.cloud().points()) directions.put(point.index(),com.yumorie.fireworkshow.step.Animation.direction(seed,point.index()));
    }
    public double[] direction(int index) { return directions.get(index); }
    public Location origin() { return origin.clone(); }
    public List<Player> viewers() {
        double squared=settings.viewDistance()*settings.viewDistance();
        return origin.getWorld().getPlayers().stream().filter(p->p.isOnline() && p.getLocation().distanceSquared(origin)<=squared).toList();
    }
}
