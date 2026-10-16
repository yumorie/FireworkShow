package com.yumorie.fireworkshow.config;
import com.yumorie.fireworkshow.image.*;
import com.yumorie.fireworkshow.step.StepRegistry;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import static com.yumorie.fireworkshow.config.Values.*;
public final class ShowLoader {
    public record Snapshot(Settings settings,Map<String,ShowDefinition> shows,int cachedClouds) {
        public Snapshot { shows=Map.copyOf(shows); }
    }
    private final StepRegistry steps;
    public ShowLoader(StepRegistry steps) { this.steps=steps; }
    public Snapshot load(Path root) throws Exception {
        if(Files.size(root.resolve("config.yml"))>65536) throw new IllegalArgumentException("config.yml exceeds 64 KiB");
        var config=new YamlConfiguration(); config.load(root.resolve("config.yml").toFile());
        var settings=Settings.load(config);
        Map<String,ShowDefinition> shows=new LinkedHashMap<>();
        Map<String,ImagePointCloud> cache=new HashMap<>();
        List<String> errors=new ArrayList<>();
        try(var files=Files.list(root.resolve("shows"))) {
            for(var file:files.filter(p->p.getFileName().toString().endsWith(".yml")).sorted().limit(129).toList()) {
                if(shows.size()>=128) { errors.add("shows/: maximum 128 definitions"); break; }
                try {
                    if(!file.toRealPath().startsWith(root.resolve("shows").toRealPath())) throw new IllegalArgumentException("show symlink escapes shows/");
                    if(Files.size(file)>65536) throw new IllegalArgumentException("YAML exceeds 64 KiB");
                    var yaml=new YamlConfiguration();yaml.load(file.toFile());
                    String id=yaml.getString("id","");
                    if(!id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("id: invalid identifier");
                    if(shows.containsKey(id)) throw new IllegalArgumentException("id: duplicate "+id);
                    var image=yaml.getConfigurationSection("image");
                    if(image==null) throw new IllegalArgumentException("image: missing section");
                    Path path=ImageAssetService.resolve(root.resolve("images"),image.getString("file",""));
                    double width=number(image,"width-blocks",5,0.1,32);
                    int alpha=integer(image,"alpha-threshold",settings.alpha(),0,255);
                    int max=integer(image,"max-points",settings.maxPoints(),1,settings.maxPoints());
                    float dust=(float)number(image,"dust-size",settings.dust(),0.01,4);
                    String orientation=image.getString("orientation","face-trigger");
                    if(!Set.of("face-trigger","fixed").contains(orientation)) throw new IllegalArgumentException("image.orientation: expected face-trigger or fixed");
                    float yaw=(float)number(image,"yaw",0,-360,360);
                    String key=path+":"+width+":"+alpha+":"+max;
                    var cloud=cache.get(key);
                    if(cloud==null) { cloud=new ImageAssetService().load(path,settings.imageLimits(),width,alpha,max);cache.put(key,cloud); }
                    var timeline=yaml.getMapList("timeline");
                    if(yaml.getList("timeline")==null || yaml.getList("timeline").size()!=timeline.size()) throw new IllegalArgumentException("timeline: each entry must be a map");
                    if(timeline.isEmpty() || timeline.size()>32) throw new IllegalArgumentException("timeline: requires 1..32 steps");
                    var definitions=new ArrayList<ShowDefinition.StepDefinition>();
                    int total=0,index=0;
                    for(var map:timeline) {
                        var step=new YamlConfiguration();copyMap(step,map);
                        String type=step.getString("type","");
                        try {
                            int duration=integer(step,"duration-ticks",0,1,12000);total+=duration;
                            steps.factory(type).validate(step);
                            definitions.add(new ShowDefinition.StepDefinition(type,duration,step.saveToString()));
                        } catch(Exception e) { throw new IllegalArgumentException("timeline["+index+"]: "+e.getMessage(),e); }
                        index++;
                    }
                    if(total>24000) throw new IllegalArgumentException("timeline: exceeds 24000 ticks");
                    shows.put(id,new ShowDefinition(id,yaml.getString("display-name",id),cloud,dust,orientation,yaw,definitions));
                } catch(Exception e) { errors.add(file.getFileName()+": "+e.getMessage()); }
            }
        }
        if(!errors.isEmpty()) throw new IllegalArgumentException("Reload rejected ("+errors.size()+" errors); old snapshot retained:\n"+String.join("\n",errors));
        return new Snapshot(settings,shows,cache.size());
    }
    private static void copyMap(org.bukkit.configuration.ConfigurationSection section,Map<?,?> map) {
        map.forEach((k,v)-> { if(v instanceof Map<?,?> nested) copyMap(section.createSection(k.toString()),nested);else section.set(k.toString(),v); });
    }
}
