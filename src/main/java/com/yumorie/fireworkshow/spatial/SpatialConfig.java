package com.yumorie.fireworkshow.spatial;

import org.bukkit.configuration.ConfigurationSection;
import java.util.Set;

public final class SpatialConfig {
    private SpatialConfig() {}
    public static Transform3D read(ConfigurationSection config) {
        if(!config.contains("transform")) return Transform3D.IDENTITY;
        var transform=config.getConfigurationSection("transform");
        if(transform==null) throw new IllegalArgumentException("transform: expected mapping");
        for(String key:transform.getKeys(false)) if(!Set.of("position","rotation").contains(key)) throw new IllegalArgumentException("transform."+key+": unknown field");
        double[] p=vector(transform,"position"), r=vector(transform,"rotation");
        for(double n:p) if(Math.abs(n)>128) throw new IllegalArgumentException("transform.position: maximum absolute offset 128");
        return Transform3D.of(new Vec3(p[0],p[1],p[2]),new Rotation3D(r[0],r[1],r[2]));
    }
    private static double[] vector(ConfigurationSection section,String key) {
        if(!section.contains(key)) return new double[3];
        Object raw=section.get(key);
        if(raw instanceof ConfigurationSection mapping) {
            for(String axis:mapping.getKeys(false)) if(!Set.of("x","y","z").contains(axis)) throw new IllegalArgumentException("transform."+key+": unknown axis "+axis);
            return new double[]{component(mapping,"x"),component(mapping,"y"),component(mapping,"z")};
        }
        throw new IllegalArgumentException("transform."+key+": expected {x, y, z} mapping");
    }
    private static double component(ConfigurationSection mapping,String axis) {
        Object value=mapping.get(axis,0);
        if(!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("transform."+axis+": expected finite number");
        return n.doubleValue();
    }
}
