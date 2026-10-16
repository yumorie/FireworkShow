package com.yumorie.fireworkshow.config;
import org.bukkit.configuration.ConfigurationSection;
import com.yumorie.fireworkshow.image.ImageAssetService.Limits;
import static com.yumorie.fireworkshow.config.Values.*;
public record Settings(boolean unsafeFullFrame,int framePeriod,double viewDistance,int budget,int maxActive,int maxPerPlayer,int maxClips,
                       Limits imageLimits,com.yumorie.fireworkshow.image.ImageAssetService.PreprocessLimits preprocess,int maxPoints,int alpha,float dust,double previewDistance,double previewHeight) {
    public static Settings load(ConfigurationSection c) {
        if(c.contains("experimental.allow-over-budget-full-frames")&&!c.isBoolean("experimental.allow-over-budget-full-frames")) throw new IllegalArgumentException("experimental.allow-over-budget-full-frames: expected boolean");
        return new Settings(c.getBoolean("experimental.allow-over-budget-full-frames",false),integer(c,"display.frame-period-ticks",2,1,20),number(c,"display.view-distance-blocks",48,1,128),
            integer(c,"display.max-particles-per-tick",1500,1,10000),integer(c,"display.max-active-shows",6,1,32),
            integer(c,"display.max-shows-per-player",2,1,16),
            integer(c,"display.max-active-clips",128,1,512),
            new Limits(integer(c,"image.max-file-bytes",16777216,1,16777216),integer(c,"image.max-width",4096,1,8192),
                integer(c,"image.max-height",4096,1,8192),integer(c,"image.max-pixels",4194304,1,16777216)),
            new com.yumorie.fireworkshow.image.ImageAssetService.PreprocessLimits(
                integer(c,"image.max-decoded-bytes",67108864,1048576,268435456),
                integer(c,"image.max-decoded-pixels",67108864,1,268435456),
                integer(c,"image.max-pixel-visits",134217728,1,536870912),
                integer(c,"image.max-grid-cells",262144,1,262144),integer(c,"image.max-sampling-variants",512,1,2048),
                integer(c,"image.max-cached-points",2000000,1,2000000),integer(c,"image.max-sources",128,1,256),
                integer(c,"image.max-preprocess-millis",10000,100,60000)),
            integer(c,"image.max-points",700,1,5000),integer(c,"image.default-alpha-threshold",16,0,255),
            (float)number(c,"image.default-dust-size",0.9,0.01,4),number(c,"preview.distance-blocks",8,1,32),
            number(c,"preview.height-offset-blocks",1,-10,20));
    }
}
