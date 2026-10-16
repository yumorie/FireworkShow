import com.yumorie.fireworkshow.image.*;
import java.nio.file.*;

/** Local-only diagnostic: java --class-path target/classes tools/InspectRawPng.java file.png ... */
class InspectRawPng {
    public static void main(String[] args) throws Exception {
        for(String name:args) {
            try(var assets=new ImageAssetService()) {
                var path=Path.of(name);
                var limits=new ImageAssetService.Limits(16777216,4096,4096,4194304);
                var size=assets.metadata(path,limits);
                var all=assets.loadRaw(path,limits,new SamplingRequest(5,1,.12,0,1,1));
                var eligible=assets.loadRaw(path,limits,new SamplingRequest(5,1,.12,16,1,1));
                System.out.println(name+" source="+size.width()+"x"+size.height()+" nonTransparent="+all.points().size()+" alpha16="+eligible.points().size()+" cloudPoints="+eligible.points().size());
                for(int viewers:new int[]{1,2,4}) {
                    long cost=(long)eligible.points().size()*viewers;
                    System.out.println(" viewers="+viewers+" DUST/frame="+cost+" DUST/second@10frames="+(cost*10)+" budget1500Sufficient="+(cost<=1500));
                }
                System.out.println(assets.stats());
            }
        }
    }
}
