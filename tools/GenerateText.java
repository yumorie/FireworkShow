import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;

/** Local, reproducible 5x7 glyph rasterizer. Run from the repository root with Java 25. */
public class GenerateText {
    private static final Map<Character,String[]> GLYPHS=Map.of(
        'M',new String[]{"10001","11011","10101","10101","10001","10001","10001"},
        'C',new String[]{"01111","10000","10000","10000","10000","10000","01111"},
        'Z',new String[]{"11111","00001","00010","00100","01000","10000","11111"},
        'J',new String[]{"00111","00010","00010","00010","00010","10010","01100"},
        'U',new String[]{"10001","10001","10001","10001","10001","10001","01110"},
        'G',new String[]{"01111","10000","10000","10111","10001","10001","01111"},
        'A',new String[]{"01110","10001","10001","11111","10001","10001","10001"});

    public static void main(String[] args) throws Exception {
        Path output=Path.of("src/main/resources/images/text.png");
        int width=107,height=62,stroke=3,gap=5;
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        String[] lines={"MCZJU","MMGA"};
        for(int line=0;line<lines.length;line++) {
            String text=lines[line];
            int left=(width-(text.length()*5*stroke+(text.length()-1)*gap))/2;
            int top=6+line*(7*stroke+8);
            for(int letter=0;letter<text.length();letter++) {
                String[] glyph=GLYPHS.get(text.charAt(letter));
                for(int row=0;row<7;row++) for(int col=0;col<5;col++) {
                    if(glyph[row].charAt(col)!='1') continue;
                    for(int dy=0;dy<stroke;dy++) for(int dx=0;dx<stroke;dx++) {
                        int x=left+letter*(5*stroke+gap)+col*stroke+dx,y=top+row*stroke+dy;
                        double t=(x-6)/94.0;
                        int r=(int)Math.round(45+(205-45)*t),g=(int)Math.round(222+(185-222)*t);
                        image.setRGB(x,y,0xff0000ff|(r<<16)|(g<<8));
                    }
                }
            }
        }
        Files.createDirectories(output.getParent());
        if(!ImageIO.write(image,"png",output.toFile())) throw new IllegalStateException("PNG writer unavailable");
        System.out.println("Wrote "+output+" (107x62 RGBA, MCZJU / MMGA)");
    }
}
