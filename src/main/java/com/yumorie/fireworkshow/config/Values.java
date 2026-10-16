package com.yumorie.fireworkshow.config;
import org.bukkit.configuration.ConfigurationSection;
public final class Values {
    private Values() {}
    public static double number(ConfigurationSection c,String key,double fallback,double min,double max) {
        Object raw=c.get(key,fallback);
        if(!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()<min || n.doubleValue()>max)
            throw new IllegalArgumentException(key+": expected finite number in ["+min+", "+max+"]");
        return n.doubleValue();
    }
    public static int integer(ConfigurationSection c,String key,int fallback,int min,int max) {
        double n=number(c,key,fallback,min,max);
        if(n!=Math.rint(n)) throw new IllegalArgumentException(key+": expected integer");
        return (int)n;
    }
}
