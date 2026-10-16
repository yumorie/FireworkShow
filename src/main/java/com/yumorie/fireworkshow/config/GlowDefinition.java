package com.yumorie.fireworkshow.config;

import com.yumorie.fireworkshow.image.ImagePointCloud;
import java.util.*;

/** Stable sparse accents, never a replacement for the colored dust cloud. */
public record GlowDefinition(boolean enabled,double density) {
    public static final GlowDefinition OFF=new GlowDefinition(false,0.03);
    public GlowDefinition {
        if(!Double.isFinite(density)||density<0||density>0.1) throw new IllegalArgumentException("glow.density: expected 0..0.1");
    }
    public Set<Integer> select(ImagePointCloud cloud) {
        if(!enabled||density==0) return Set.of();
        var points=cloud.points();int count=(int)Math.floor(points.size()*density);var result=new HashSet<Integer>();
        for(int i=0;i<count;i++) result.add(points.get((int)((2L*i+1)*points.size()/(2L*count))).index());
        return Set.copyOf(result);
    }
}
