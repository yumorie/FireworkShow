package com.yumorie.fireworkshow.step;
import com.yumorie.fireworkshow.runtime.ShowContext;
import com.yumorie.fireworkshow.render.PointCloudRenderer;
import com.yumorie.fireworkshow.integration.particlelib.ParticleLibAdapter;
import me.sunmc.particlelib.api.effect.EffectHandle;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;
import static com.yumorie.fireworkshow.config.Values.*;
public final class StepRegistry {
    private final Map<String,StepFactory> factories=new HashMap<>();
    public void register(String id,StepFactory factory) {
        if(factories.putIfAbsent(id,factory)!=null) throw new IllegalArgumentException("Duplicate step type: "+id);
    }
    public StepFactory factory(String id) {
        var factory=factories.get(id);
        if(factory==null) throw new IllegalArgumentException("Unknown timeline type: "+id);
        return factory;
    }
    public static StepRegistry defaults(ParticleLibAdapter adapter) {
        var registry=new StepRegistry();
        var renderer=new PointCloudRenderer();
        for(String mode:List.of("reveal","hold","scatter")) registry.register(mode,new StepFactory() {
            public boolean usesImage() { return true; }
            public void validate(ConfigurationSection c) {
                for(String key:c.getKeys(false)) if(!Set.of("type","duration-ticks","at-tick","transform","image","easing","initial-scale","radius-blocks","mode","fade-out").contains(key)) throw new IllegalArgumentException(key+": unknown step field");
                Animation.ease(c.getString("easing","linear"),0);
                number(c,"initial-scale",0.15,0,1); number(c,"radius-blocks",4,0,32);
                if(!c.getString("mode","center-out").equals("center-out")) throw new IllegalArgumentException("mode: only center-out supported");
                if(c.contains("fade-out") && !c.isBoolean("fade-out")) throw new IllegalArgumentException("fade-out: expected boolean");
            }
            public ShowStep create(ConfigurationSection c,ShowContext context) {
                return progress->renderer.render(context,mode,Animation.ease(c.getString("easing","linear"),progress),c.getDouble("initial-scale",0.15),c.getDouble("radius-blocks",4),c.getBoolean("fade-out",true));
            }
        });
        registry.register("particlelib",new StepFactory() {
            public void validate(ConfigurationSection c) {
                for(String key:c.getKeys(false)) if(!Set.of("type","duration-ticks","at-tick","transform","rotation-policy","effect").contains(key)) throw new IllegalArgumentException(key+": unknown step field");
                var effect=c.getConfigurationSection("effect");
                if(effect==null) throw new IllegalArgumentException("effect: missing section");
                adapter.validate(effect);
            }
            public void validateSpatial(ConfigurationSection c,com.yumorie.fireworkshow.spatial.SpatialContext spatial) {
                adapter.validateSpatial(spatial,c.getString("rotation-policy","origin-only"));
            }
            public ShowStep create(ConfigurationSection c,ShowContext context) {
                return new ShowStep() {
                    private List<EffectHandle> handles=List.of();
                    private List<org.bukkit.entity.Player> viewers=List.of();
                    public void start() {
                        viewers=context.viewers();
                        int cost=Math.multiplyExact(adapter.emissionBound(c.getConfigurationSection("effect")),viewers.size());
                        if(!context.budget.take(cost)) throw new IllegalStateException("ParticleLib particle budget exhausted; reduce viewers, density or concurrent shows");
                        context.reservedParticles=cost;
                        handles=adapter.play(c.getConfigurationSection("effect"),context.origin(),viewers,context.settings.viewDistance());
                    }
                    public void tick(double progress) {
                        var current=context.viewers();
                        for(int i=0;i<handles.size();i++) if(!current.contains(viewers.get(i))) handles.get(i).cancel();
                    }
                    public void stop() {
                        var old=handles;handles=List.of();context.reservedParticles=0;
                        RuntimeException failure=null;
                        for(var handle:old) try { handle.cancel(); } catch(RuntimeException e) { if(failure==null) failure=e;else failure.addSuppressed(e); }
                        if(failure!=null) throw failure;
                    }
                };
            }
        });
        return registry;
    }
}
