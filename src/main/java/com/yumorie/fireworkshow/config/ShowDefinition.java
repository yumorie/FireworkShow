package com.yumorie.fireworkshow.config;
import java.util.*;
import com.yumorie.fireworkshow.spatial.Transform3D;
/** YAML is retained as immutable text; each session parses its own step configuration. */
public record ShowDefinition(String id,String displayName,Map<String,ImageDefinition> images,String orientation,float yaw,List<TrackDefinition> tracks) {
    public ShowDefinition { images=Map.copyOf(images);tracks=List.copyOf(tracks); }
    public record StepDefinition(String type,int duration,String yaml) {}
    public record ClipDefinition(int atTick,StepDefinition step,String image,Transform3D transform,String rotationPolicy) {}
    public record TrackDefinition(String id,Transform3D transform,List<ClipDefinition> clips) {
        public TrackDefinition { clips=List.copyOf(clips); }
    }
}
