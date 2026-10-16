package com.yumorie.fireworkshow.step;
import java.util.SplittableRandom;
public final class Animation {
    private Animation() {}
    public static double revealScale(double initial,double progress) { return progress>=1?1:initial+(1-initial)*progress; }
    public static double ease(String name,double p) {
        return switch(name) { case "linear" -> p; case "ease-in-cubic" -> p*p*p; case "ease-out-cubic" -> 1-Math.pow(1-p,3); default -> throw new IllegalArgumentException("Unknown easing: "+name); };
    }
    public static double[] direction(long seed,int index) {
        var r=new SplittableRandom(seed+index*0x9e3779b97f4a7c15L);
        double a=r.nextDouble()*Math.PI*2,z=r.nextDouble(-0.25,0.25),s=Math.sqrt(1-z*z);
        return new double[]{Math.cos(a)*s,Math.sin(a)*s,z};
    }
    public static boolean visible(long seed,int index,double progress) {
        return progress<1 && new SplittableRandom(seed^index).nextDouble()>=progress;
    }
}
