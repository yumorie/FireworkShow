package com.yumorie.fireworkshow.runtime;
import com.yumorie.fireworkshow.config.*;
import com.yumorie.fireworkshow.render.RenderBudget;
import com.yumorie.fireworkshow.integration.particlelib.ParticleLibAdapter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.*;
import com.yumorie.fireworkshow.spatial.*;
public final class ShowContext {
    public final ShowDefinition definition;
    public final Settings settings;
    private final Location origin;
    public final UUID owner;
    public final long seed;
    public final RenderBudget budget;
    public final ParticleLibAdapter adapter;
    public final String imageName;
    public final ImageDefinition image;
    public final SpatialContext spatial;
    private final Map<Integer,double[]> directions=new HashMap<>();
    public final Set<Integer> glowPoints;
    public int tick;
    public int reservedParticles;
    public static long imageSeed(long sessionSeed,String imageName) {
        long hash=0xcbf29ce484222325L;
        if(imageName!=null) for(int i=0;i<imageName.length();i++) { hash^=imageName.charAt(i);hash*=0x100000001b3L; }
        return sessionSeed^hash;
    }
    public ShowContext(ShowDefinition definition,Settings settings,Location origin,UUID owner,RenderBudget budget,ParticleLibAdapter adapter,String imageName,SpatialContext spatial,long seed) {
        this(definition,settings,origin,owner,budget,adapter,imageName,spatial,seed,imageName==null?null:definition.images().get(imageName));
    }
    public ShowContext(ShowDefinition definition,Settings settings,Location origin,UUID owner,RenderBudget budget,ParticleLibAdapter adapter,String imageName,SpatialContext spatial,long seed,ImageDefinition sampledImage) {
        this.definition=definition; this.settings=settings; this.origin=origin.clone(); this.owner=owner;this.budget=budget;this.adapter=adapter;
        this.imageName=imageName;this.image=sampledImage;this.spatial=spatial;
        this.seed=seed;
        if(image!=null) for(var point:image.cloud().points()) directions.put(point.index(),com.yumorie.fireworkshow.step.Animation.direction(seed,point.index()));
        glowPoints=image==null?Set.of():image.glow().select(image.cloud());
    }
    public double[] direction(int index) { return directions.get(index).clone(); }
    public Location origin() { var p=spatial.apply(Vec3.ZERO);return new Location(origin.getWorld(),p.x(),p.y(),p.z(),origin.getYaw(),0); }
    public Location imageLocation(double x,double y,double z) { var p=spatial.imagePoint(x,y,z);return new Location(origin.getWorld(),p.x(),p.y(),p.z()); }
    public List<Player> viewers() {
        double squared=settings.viewDistance()*settings.viewDistance();
        var center=origin();
        return center.getWorld().getPlayers().stream().filter(p->p.isOnline() && p.getLocation().distanceSquared(center)<=squared).toList();
    }
}
