package com.yumorie.fireworkshow;

import javax.imageio.ImageIO;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextAssetTest {
    @Test void rgbaPngContainsExactlyCenteredMczjuAndMmgaInCoarsePixelGlyphs() throws Exception {
        try(var input=getClass().getResourceAsStream("/images/text.png")) {
            assertNotNull(input);byte[] png=input.readAllBytes();
            assertEquals(8,png[24]);assertEquals(6,png[25]); // PNG IHDR: 8-bit RGBA, not a filled RGB rectangle.
            var image=ImageIO.read(new java.io.ByteArrayInputStream(png));
            assertEquals(107,image.getWidth());assertEquals(62,image.getHeight());assertTrue(image.getColorModel().hasAlpha());
            Map<Character,String> glyphs=Map.of(
                'M',"10001/11011/10101/10101/10001/10001/10001",
                'C',"01111/10000/10000/10000/10000/10000/01111",
                'Z',"11111/00001/00010/00100/01000/10000/11111",
                'J',"00111/00010/00010/00010/00010/10010/01100",
                'U',"10001/10001/10001/10001/10001/10001/01110",
                'G',"01111/10000/10000/10111/10001/10001/01111",
                'A',"01110/10001/10001/11111/10001/10001/10001");
            String[] lines={"MCZJU","MMGA"};boolean[][] expected=new boolean[62][107];
            for(int row=0;row<2;row++) {
                int left=(107-(lines[row].length()*15+(lines[row].length()-1)*5))/2,top=6+row*29;
                for(int letter=0;letter<lines[row].length();letter++) {
                    var mask=glyphs.get(lines[row].charAt(letter)).split("/");
                    for(int y=0;y<21;y++) for(int x=0;x<15;x++) expected[top+y][left+letter*20+x]=mask[y/3].charAt(x/3)=='1';
                }
            }
            for(int y=0;y<62;y++) for(int x=0;x<107;x++) {
                int argb=image.getRGB(x,y);
                assertEquals(expected[y][x]?255:0,argb>>>24,"Pixel "+x+","+y);
                if(expected[y][x]) {
                    assertEquals(255,argb&255);assertTrue((argb>>8&255)>=185);assertNotEquals(0,argb&0xffffff);
                }
            }
            // Symmetric margins: first line spans x=6..100, second spans x=16..90; vertical margins are both 6px.
            assertTrue(expected[9][6]);assertTrue(expected[9][100]);assertTrue(expected[38][16]);assertTrue(expected[38][90]);
            assertTrue((image.getRGB(6,9)>>16&255)<(image.getRGB(100,9)>>16&255));
        }
    }
}
