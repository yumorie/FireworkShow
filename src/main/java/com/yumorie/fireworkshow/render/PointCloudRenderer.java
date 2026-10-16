package com.yumorie.fireworkshow.render;
import com.yumorie.fireworkshow.runtime.ShowContext;
import com.yumorie.fireworkshow.step.Animation;
import org.bukkit.Color;
import org.bukkit.Particle;
public final class PointCloudRenderer {
    public void render(ShowContext c,String mode,double progress,double initialScale,double radius,boolean fade) {
        if(c.tick%c.settings.framePeriod()!=0 && progress!=1) return;
        if(mode.equals("scatter") && progress>=1) return;
        var viewers=c.viewers();
        if(viewers.isEmpty()) return;
        var points=c.image.cloud().points();
        double scale=mode.equals("reveal")?Animation.revealScale(initialScale,progress):1;
        int count=mode.equals("reveal")?(int)Math.ceil(points.size()*progress):points.size();
        // Authorize this entire animation frame BEFORE sending any point; never truncate a prefix.
        int visible=count;
        if(mode.equals("scatter") && fade) {
            visible=0;for(int i=0;i<count;i++)if(Animation.visible(c.seed,points.get(i).index(),progress))visible++;
        }
        long cost=(long)visible*viewers.size();
        c.reportFrame((long)points.size()*viewers.size());
        if(!c.budget.authorizeFrame(cost,c.settings.unsafeFullFrame())) throw new IllegalStateException("RAW PIXEL frame rejected: image="+c.imageName+" required="+cost+" remaining="+c.budget.remaining()+"; safe capacity exceeded; ZERO pixels sent for this frame. High-risk override: experimental.allow-over-budget-full-frames");
        // Optional accents remain within the normal leftover budget.
        var accents=new java.util.ArrayList<org.bukkit.Location>();
        for(int i=0;i<count;i++) {
            var p=points.get(i);
            if(mode.equals("scatter") && fade && !Animation.visible(c.seed,p.index(),progress)) continue;
            double x=p.x()*scale,y=p.y()*scale,z=0;
            if(mode.equals("scatter")) { var d=c.direction(p.index());x+=d[0]*radius*progress;y+=d[1]*radius*progress;z=d[2]*radius*progress; }
            var location=c.imageLocation(x,y,z);
            var dust=new Particle.DustOptions(Color.fromRGB(p.rgb()),c.image.dust());
            for(var player:viewers) player.spawnParticle(Particle.DUST,location,1,0,0,0,0,dust,true);
            if(c.glowPoints.contains(p.index())) accents.add(location);
        }
        for(var location:accents) {
            c.budget.deferAccent(viewers.size(),()-> {
                for(var player:viewers) player.spawnParticle(Particle.GLOW,location,1,0,0,0,0,null,true);
            });
        }
    }
}
