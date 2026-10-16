package com.yumorie.fireworkshow.image;

import java.awt.image.BufferedImage;
import java.util.*;

/** Experimental one-source-pixel/one-point conversion. No sampling or point-count truncation. */
public final class RawPixelCloudBuilder {
    public ImagePointCloud build(BufferedImage image,double widthBlocks,int threshold,double brightness,int available,Runnable checkpoint) {
        int w=image.getWidth(),h=image.getHeight(),count=0;
        // Count before allocating point objects. A resource limit rejects the WHOLE asset.
        for(int y=0;y<h;y++) {
            checkpoint.run();
            for(int x=0;x<w;x++) { int alpha=image.getRGB(x,y)>>>24;if(alpha!=0&&alpha>=threshold)count++; }
        }
        if(count>available) throw new IllegalArgumentException("RAW PIXEL image needs "+count+" points; max-cached-points remaining="+available+". Whole image rejected; no truncation.");
        var points=new ArrayList<ImagePointCloud.Point>(count);
        for(int y=0;y<h;y++) {
            checkpoint.run();
            for(int x=0;x<w;x++) {
                int argb=image.getRGB(x,y),alpha=argb>>>24;
                if(alpha==0||alpha<threshold)continue;
                double localX=((x+.5)/w-.5)*widthBlocks;
                double localY=(.5-(y+.5)/h)*widthBlocks*h/w;
                points.add(new ImagePointCloud.Point(localX,localY,ColorEnhancement.brighten(argb&0xffffff,brightness),alpha,y*w+x));
            }
        }
        points.sort(Comparator.comparingDouble((ImagePointCloud.Point p)->p.x()*p.x()+p.y()*p.y()).thenComparingInt(ImagePointCloud.Point::index));
        checkpoint.run();return new ImagePointCloud(points);
    }
}
