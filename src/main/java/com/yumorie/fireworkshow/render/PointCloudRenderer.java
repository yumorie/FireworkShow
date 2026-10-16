package com.yumorie.fireworkshow.render;
import com.yumorie.fireworkshow.runtime.ShowContext;
import com.yumorie.fireworkshow.step.Animation;
import org.bukkit.Color;
import org.bukkit.Particle;
public final class PointCloudRenderer {
    /** Minecraft camera right is direction cross up: yaw=0 yields -X. */
    public static double[] offset(double x,double y,double z,float degrees) {
        double yaw=Math.toRadians(degrees),cos=Math.cos(yaw),sin=Math.sin(yaw);
        return new double[]{-cos*x-sin*z,y,-sin*x+cos*z};
    }
    public void render(ShowContext c,String mode,double progress,double initialScale,double radius,boolean fade) {
        if(c.tick%c.settings.framePeriod()!=0 && progress!=1) return;
        if(mode.equals("scatter") && progress>=1) return;
        var viewers=c.viewers();
        if(viewers.isEmpty()) return;
        var points=c.definition.cloud().points();
        var origin=c.origin();
        double scale=mode.equals("reveal")?Animation.revealScale(initialScale,progress):1;
        int count=mode.equals("reveal")?(int)Math.ceil(points.size()*progress):points.size();
        for(int i=0;i<count;i++) {
            var p=points.get(i);
            if(!Animation.visible(0,p.index(),1-p.alpha()/255.0)) continue;
            if(mode.equals("scatter") && fade && !Animation.visible(c.seed,p.index(),progress)) continue;
            double x=p.x()*scale,y=p.y()*scale,z=0;
            if(mode.equals("scatter")) { var d=c.direction(p.index());x+=d[0]*radius*progress;y+=d[1]*radius*progress;z=d[2]*radius*progress; }
            if(!c.budget.take(viewers.size())) break;
            var offset=offset(x,y,z,origin.getYaw());
            var location=origin.clone().add(offset[0],offset[1],offset[2]);
            var dust=new Particle.DustOptions(Color.fromRGB(p.rgb()),c.definition.dust());
            for(var player:viewers) player.spawnParticle(Particle.DUST,location,1,0,0,0,0,dust,true);
        }
    }
}
