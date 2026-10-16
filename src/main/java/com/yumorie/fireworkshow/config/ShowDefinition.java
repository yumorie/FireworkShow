package com.yumorie.fireworkshow.config;
import com.yumorie.fireworkshow.image.ImagePointCloud;
import java.util.List;
/** YAML is retained as immutable text; each session parses its own step configuration. */
public record ShowDefinition(String id,String displayName,ImagePointCloud cloud,float dust,String orientation,float yaw,List<StepDefinition> steps) {
    public ShowDefinition { steps=List.copyOf(steps); }
    public record StepDefinition(String type,int duration,String yaml) {}
}
