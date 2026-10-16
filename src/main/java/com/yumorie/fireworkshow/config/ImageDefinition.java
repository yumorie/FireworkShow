package com.yumorie.fireworkshow.config;
import com.yumorie.fireworkshow.image.ImagePointCloud;
import com.yumorie.fireworkshow.image.*;
import java.nio.file.Path;
public record ImageDefinition(ImagePointCloud cloud,float dust,Path source,SamplingRequest sampling,GlowDefinition glow) {
    public ImageDefinition atScale(ImageAssetService assets,ImageAssetService.Limits limits,double scale) throws java.io.IOException {
        var request=sampling.atScale(scale);
        return new ImageDefinition(assets.sample(source,limits,request),dust,source,request,glow);
    }
}
