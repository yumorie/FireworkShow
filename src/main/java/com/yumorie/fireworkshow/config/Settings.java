package com.yumorie.fireworkshow.config;
import org.bukkit.configuration.ConfigurationSection;
import com.yumorie.fireworkshow.image.ImageAssetService.Limits;
import static com.yumorie.fireworkshow.config.Values.*;
public record Settings(int framePeriod,double viewDistance,int budget,int maxActive,int maxPerPlayer,
                       Limits imageLimits,int maxPoints,int alpha,float dust,double previewDistance,double previewHeight) {
    public static Settings load(ConfigurationSection c) {
        return new Settings(integer(c,"display.frame-period-ticks",2,1,20),number(c,"display.view-distance-blocks",48,1,128),
            integer(c,"display.max-particles-per-tick",1500,1,10000),integer(c,"display.max-active-shows",6,1,32),
            integer(c,"display.max-shows-per-player",2,1,16),
            new Limits(integer(c,"image.max-file-bytes",2097152,1,16777216),integer(c,"image.max-width",512,1,2048),
                integer(c,"image.max-height",512,1,2048),integer(c,"image.max-pixels",262144,1,4194304)),
            integer(c,"image.max-points",700,1,5000),integer(c,"image.default-alpha-threshold",16,0,255),
            (float)number(c,"image.default-dust-size",0.9,0.01,4),number(c,"preview.distance-blocks",8,1,32),
            number(c,"preview.height-offset-blocks",1,-10,20));
    }
}
