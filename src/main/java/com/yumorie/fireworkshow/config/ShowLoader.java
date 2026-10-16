package com.yumorie.fireworkshow.config;

import com.yumorie.fireworkshow.image.*;
import com.yumorie.fireworkshow.spatial.*;
import com.yumorie.fireworkshow.step.StepRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static com.yumorie.fireworkshow.config.Values.*;

public final class ShowLoader {
    public record Snapshot(Settings settings,Map<String,ShowDefinition> shows,int cachedClouds) {
        public Snapshot { shows=Map.copyOf(shows); }
    }
    private final StepRegistry steps;
    public ShowLoader(StepRegistry steps) { this.steps=steps; }
    public Snapshot load(Path root) throws Exception {
        if(Files.size(root.resolve("config.yml"))>65536) throw new IllegalArgumentException("config.yml exceeds 64 KiB");
        var config=loadYaml(root.resolve("config.yml"));
        var settings=Settings.load(config);
        Map<String,ShowDefinition> shows=new LinkedHashMap<>();
        Map<String,ImagePointCloud> cache=new HashMap<>();
        List<String> errors=new ArrayList<>();
        try(var files=Files.list(root.resolve("shows"))) {
            for(var file:files.filter(p->p.getFileName().toString().endsWith(".yml")).sorted().limit(129).toList()) {
                if(shows.size()>=128) { errors.add("shows/: maximum 128 definitions");break; }
                try {
                    if(!file.toRealPath().startsWith(root.resolve("shows").toRealPath())) throw new IllegalArgumentException("show symlink escapes shows/");
                    if(Files.size(file)>65536) throw new IllegalArgumentException("YAML exceeds 64 KiB");
                    var yaml=loadYaml(file);
                    String id=yaml.getString("id","");
                    if(!id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("id: invalid identifier");
                    if(shows.containsKey(id)) throw new IllegalArgumentException("id: duplicate "+id);
                    shows.put(id,definition(root,yaml,settings,cache,id));
                } catch(Exception error) { errors.add(file.getFileName()+": "+error.getMessage()); }
            }
        }
        if(!errors.isEmpty()) throw new IllegalArgumentException("Reload rejected ("+errors.size()+" errors); old snapshot retained:\n"+String.join("\n",errors));
        return new Snapshot(settings,shows,cache.size());
    }
    private ShowDefinition definition(Path root,YamlConfiguration yaml,Settings settings,Map<String,ImagePointCloud> cache,String id) throws Exception {
        Map<String,ImageDefinition> images=new LinkedHashMap<>();
        for(String key:yaml.getKeys(false)) if(!Set.of("id","display-name","images","orientation","yaw","tracks").contains(key))
            throw new IllegalArgumentException(key+": unknown show field");
        var table=requiredSection(yaml,"images");
        if(table.getKeys(false).size()>16) throw new IllegalArgumentException("images: maximum 16 named resources");
        for(String name:table.getKeys(false)) {
            if(!name.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("images: invalid name "+name);
            try { images.put(name,image(root,requiredSection(table,name),settings,cache)); }
            catch(Exception error) { throw new IllegalArgumentException("images."+name+": "+error.getMessage(),error); }
        }
        String orientation=yaml.getString("orientation","face-trigger");
        if(!Set.of("face-trigger","fixed").contains(orientation)) throw new IllegalArgumentException("orientation: expected face-trigger or fixed");
        float yaw=(float)number(yaml,"yaw",0,-360,360);
        List<ShowDefinition.TrackDefinition> tracks=new ArrayList<>();
        var trackTable=requiredSection(yaml,"tracks");
        if(trackTable.getKeys(false).isEmpty() || trackTable.getKeys(false).size()>16) throw new IllegalArgumentException("tracks: requires 1..16 named tracks");
        int total=0;
        for(String name:trackTable.getKeys(false)) {
            if(!name.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("tracks: invalid name "+name);
            var track=requiredSection(trackTable,name);
            for(String key:track.getKeys(false)) if(!Set.of("transform","clips").contains(key)) throw new IllegalArgumentException("tracks."+name+"."+key+": unknown field");
            var clips=clips(maps(track,"clips",1,64),images,"tracks."+name+".clips");
            total+=clips.size();if(total>128) throw new IllegalArgumentException("tracks: maximum 128 clips per show");
            tracks.add(new ShowDefinition.TrackDefinition(name,SpatialConfig.read(track),clips));
        }
        for(var track:tracks) for(var clip:track.clips()) {
            var config=new YamlConfiguration();config.loadFromString(clip.step().yaml());
            try { steps.factory(clip.step().type()).validateSpatial(config,SpatialContext.at(Vec3.ZERO,0,Rotation3D.ZERO).child(track.transform()).child(clip.transform())); }
            catch(Exception error) { throw new IllegalArgumentException("track "+track.id()+" ParticleLib transform: "+error.getMessage(),error); }
        }
        return new ShowDefinition(id,yaml.getString("display-name",id),images,orientation,yaw,tracks);
    }
    private List<ShowDefinition.ClipDefinition> clips(List<Map<?,?>> list,Map<String,ImageDefinition> images,String path) {
        List<ShowDefinition.ClipDefinition> result=new ArrayList<>();
        for(int index=0;index<list.size();index++) {
            var step=section(list.get(index));String type=step.getString("type","");
            try {
                int duration=integer(step,"duration-ticks",0,1,12000);
                int at=integer(step,"at-tick",-1,0,23999);
                if((long)at+duration>24000) throw new IllegalArgumentException("clip end exceeds 24000 ticks");
                var factory=steps.factory(type);factory.validate(step);
                String image=step.getString("image");
                if(step.contains("image") && !step.isString("image")) throw new IllegalArgumentException("image: expected named reference");
                if(image!=null && !images.containsKey(image)) throw new IllegalArgumentException("image: unknown resource "+image);
                if(factory.usesImage() && image==null) throw new IllegalArgumentException("image: explicit named resource required");
                String policy=step.getString("rotation-policy","origin-only");
                if(!Set.of("origin-only","require-full").contains(policy)) throw new IllegalArgumentException("rotation-policy: expected origin-only or require-full");
                result.add(new ShowDefinition.ClipDefinition(at,new ShowDefinition.StepDefinition(type,duration,step.saveToString()),image,SpatialConfig.read(step),policy));
            } catch(Exception error) { throw new IllegalArgumentException(path+"["+index+"]: "+error.getMessage(),error); }
        }
        return List.copyOf(result);
    }
    private ImageDefinition image(Path root,ConfigurationSection image,Settings settings,Map<String,ImagePointCloud> cache) throws Exception {
        Path path=ImageAssetService.resolve(root.resolve("images"),image.getString("file",""));
        double width=number(image,"width-blocks",5,0.1,32);
        int alpha=integer(image,"alpha-threshold",settings.alpha(),0,255);
        int max=integer(image,"max-points",settings.maxPoints(),1,settings.maxPoints());
        float dust=(float)number(image,"dust-size",settings.dust(),0.01,4);
        String key=path+":"+width+":"+alpha+":"+max;
        var cloud=cache.get(key);
        if(cloud==null) { cloud=new ImageAssetService().load(path,settings.imageLimits(),width,alpha,max);cache.put(key,cloud); }
        return new ImageDefinition(cloud,dust);
    }
    private static ConfigurationSection requiredSection(ConfigurationSection parent,String key) {
        var section=parent.getConfigurationSection(key);
        if(section==null) throw new IllegalArgumentException(key+": expected mapping");return section;
    }
    private static YamlConfiguration loadYaml(Path path) throws Exception {
        String text=Files.readString(path);
        // Reuse Paper's existing SnakeYAML dependency, but reject duplicate names before Bukkit silently overwrites them.
        var options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(32);options.setCodePointLimit(65536);
        new Yaml(new SafeConstructor(options)).load(text);
        var result=new YamlConfiguration();result.loadFromString(text);return result;
    }
    private static List<Map<?,?>> maps(ConfigurationSection parent,String key,int min,int max) {
        var raw=parent.getList(key);
        if(raw==null || raw.size()<min || raw.size()>max) throw new IllegalArgumentException(key+": requires "+min+".."+max+" mappings");
        List<Map<?,?>> result=new ArrayList<>();
        for(Object item:raw) { if(!(item instanceof Map<?,?> map)) throw new IllegalArgumentException(key+": each entry must be a mapping");result.add(map); }
        return result;
    }
    private static YamlConfiguration section(Map<?,?> map) { var result=new YamlConfiguration();copyMap(result,map);return result; }
    private static void copyMap(ConfigurationSection section,Map<?,?> map) {
        map.forEach((k,v)-> { if(v instanceof Map<?,?> nested) copyMap(section.createSection(k.toString()),nested);else section.set(k.toString(),v); });
    }
}
