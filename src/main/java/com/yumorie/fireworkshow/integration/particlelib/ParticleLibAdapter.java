package com.yumorie.fireworkshow.integration.particlelib;
import me.sunmc.particlelib.api.ParticleLibAPI;
import me.sunmc.particlelib.api.effect.EffectHandle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.lang.reflect.Field;
import java.util.*;
public final class ParticleLibAdapter {
    private final ParticleLibAPI api;
    public ParticleLibAdapter() {
        var registration=Bukkit.getServicesManager().getRegistration(ParticleLibAPI.class);
        if(registration==null) throw new IllegalStateException("ParticleLib API service unavailable; install matching v0.1-alpha standalone plugin");
        api=registration.getProvider();
    }
    public ParticleLibAdapter(ParticleLibAPI api) { this.api=Objects.requireNonNull(api); }
    public void validate(ConfigurationSection section) {
        String id=section.getString("class","");
        var builder=api.builder(id).orElseThrow(()->new IllegalArgumentException("effect.class: unknown ParticleLib effect "+id));
        Set<String> fields=new HashSet<>(List.of("class","particle","color","size","speed","count","period","iterations","visible_range","visibleRange","type","offset","delay","duration","probability","force_show","forceShow","async","asynchronous","to_color","toColor"));
        Map<String,Class<?>> types=new HashMap<>();
        for(Class<?> c=builder.getClass();c!=null;c=c.getSuperclass()) for(Field f:c.getDeclaredFields()) {
            if(!java.lang.reflect.Modifier.isStatic(f.getModifiers())) { fields.add(f.getName());types.put(f.getName(),f.getType()); }
        }
        for(String key:section.getKeys(false)) {
            if(!fields.contains(key)) throw new IllegalArgumentException("effect."+key+": unknown builder field");
            Object value=section.get(key);
            if(value instanceof Number n && (!Double.isFinite(n.doubleValue()) || Math.abs(n.doubleValue())>10000)) throw new IllegalArgumentException("effect."+key+": unsafe number");
            Class<?> type=types.get(key);
            if(type==boolean.class && !(value instanceof Boolean)) throw new IllegalArgumentException("effect."+key+": expected boolean");
            if(type==int.class || type==long.class || type==float.class || type==double.class) {
                if(!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("effect."+key+": expected finite number");
                if((type==int.class || type==long.class) && ((Number)value).doubleValue()!=Math.rint(((Number)value).doubleValue())) throw new IllegalArgumentException("effect."+key+": expected integer");
            }
        }
        com.yumorie.fireworkshow.config.Values.integer(section,"period",2,1,200);
        com.yumorie.fireworkshow.config.Values.integer(section,"iterations",-1,-1,10000);
        com.yumorie.fireworkshow.config.Values.integer(section,"count",1,1,10);
        if(section.contains("particle")) org.bukkit.Particle.valueOf(section.getString("particle","").toUpperCase(Locale.ROOT));
        if(id.equals("tornado")) {
            com.yumorie.fireworkshow.config.Values.number(section,"distance",0.375,0.1,4);
            com.yumorie.fireworkshow.config.Values.number(section,"tornadoHeight",4,0.1,16);
            com.yumorie.fireworkshow.config.Values.number(section,"maxTornadoRadius",2.5,0,8);
            com.yumorie.fireworkshow.config.Values.integer(section,"circleParticles",8,1,32);
            com.yumorie.fireworkshow.config.Values.integer(section,"cloudParticles",10,0,50);
            com.yumorie.fireworkshow.config.Values.number(section,"cloudSize",2.5,0,4);
        }
    }
    public int emissionBound(ConfigurationSection section) {
        if(section.getString("class","").equals("tornado")) {
            double layers=Math.ceil(section.getDouble("tornadoHeight",4)/section.getDouble("distance",0.375))+1;
            return (int)Math.ceil(layers*section.getDouble("maxTornadoRadius",2.5)*section.getInt("circleParticles",8)+section.getInt("cloudParticles",10)*section.getDouble("cloudSize",2.5));
        }
        return 1500; // External algorithms need their own ParticleLib performance limits.
    }
    public List<EffectHandle> play(ConfigurationSection source,Location origin,List<Player> viewers,double range) {
        List<EffectHandle> handles=new ArrayList<>();
        try {
            for(Player player:viewers) {
                var config=new YamlConfiguration();
                source.getValues(true).forEach(config::set);
                config.set("visibleRange",range);
                config.set("async",false);
                config.set("asynchronous",false);
                if(config.getString("class","").equals("tornado")) {
                    if(!config.contains("tornadoHeight")) config.set("tornadoHeight",4.0);
                    if(!config.contains("maxTornadoRadius")) config.set("maxTornadoRadius",2.5);
                    if(!config.contains("circleParticles")) config.set("circleParticles",8);
                    if(!config.contains("cloudParticles")) config.set("cloudParticles",10);
                }
                var handle=api.play(config,origin.clone(),player);
                handles.add(handle);
                if(handle.isDone()) throw new IllegalStateException("ParticleLib returned terminated handle for "+config.getString("class"));
            }
            return handles;
        } catch(RuntimeException error) {
            for(var handle:handles) try { handle.cancel(); } catch(RuntimeException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }
}
