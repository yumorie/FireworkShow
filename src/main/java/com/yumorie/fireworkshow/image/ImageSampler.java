package com.yumorie.fireworkshow.image;

import java.awt.image.BufferedImage;
import java.util.*;
import com.yumorie.fireworkshow.image.ImagePointCloud.Point;

/** Area-weighted premultiplied-alpha sampling; no list of original pixel particles is created. */
public final class ImageSampler {
    public record Grid(int columns,int rows) { public long cells() { return (long)columns*rows; } }
    public Grid grid(int width,int height,SamplingRequest request,int maxCells) {
        double columns=Math.ceil(request.effectiveWidth()/request.spacing());
        double rows=Math.ceil(request.effectiveWidth()*height/width/request.spacing());
        if(columns>Integer.MAX_VALUE||rows>Integer.MAX_VALUE||columns*rows>maxCells) throw new IllegalArgumentException("Sampling grid exceeds max-grid-cells: "+columns+"x"+rows);
        return new Grid(Math.max(1,(int)columns),Math.max(1,(int)rows));
    }
    public ImagePointCloud sample(BufferedImage source,SamplingRequest request,Grid grid,Runnable checkpoint) {
        int w=source.getWidth(),h=source.getHeight();var candidates=new ArrayList<Point>();
        for(int gy=0;gy<grid.rows();gy++) {
            checkpoint.run();double y0=(double)gy*h/grid.rows(),y1=(double)(gy+1)*h/grid.rows();
            for(int gx=0;gx<grid.columns();gx++) {
                double x0=(double)gx*w/grid.columns(),x1=(double)(gx+1)*w/grid.columns();
                double a=0,r=0,g=0,b=0,inkArea=0,bestDistance=Double.POSITIVE_INFINITY;
                double centerX=(x0+x1)/2,centerY=(y0+y1)/2,chosenX=centerX,chosenY=centerY;
                for(int y=(int)Math.floor(y0);y<Math.min(h,(int)Math.ceil(y1));y++) for(int x=(int)Math.floor(x0);x<Math.min(w,(int)Math.ceil(x1));x++) {
                    int pixel=source.getRGB(x,y),alpha=pixel>>>24;if(alpha==0) continue;
                    double area=(Math.min(x+1,x1)-Math.max(x,x0))*(Math.min(y+1,y1)-Math.max(y,y0));if(area<=0) continue;
                    double weight=area*alpha;inkArea+=area;a+=weight;r+=(pixel>>16&255)*weight;g+=(pixel>>8&255)*weight;b+=(pixel&255)*weight;
                    double distance=(x+0.5-centerX)*(x+0.5-centerX)+(y+0.5-centerY)*(y+0.5-centerY);
                    if(distance<bestDistance) { bestDistance=distance;chosenX=x+0.5;chosenY=y+0.5; }
                }
                if(a==0) continue;
                // Average opacity of contributing ink, not transparent padding: preserve thin logo strokes.
                int alpha=(int)Math.round(a/inkArea);if(alpha<request.alpha()) continue;
                if((source.getRGB(Math.min(w-1,(int)centerX),Math.min(h-1,(int)centerY))>>>24)!=0) { chosenX=centerX;chosenY=centerY; }
                int rgb=((int)Math.round(r/a)<<16)|((int)Math.round(g/a)<<8)|(int)Math.round(b/a);
                rgb=ColorEnhancement.brighten(rgb,request.brightness());
                candidates.add(new Point((chosenX/w-0.5)*request.width(),(0.5-chosenY/h)*request.width()*h/w,rgb,alpha,gy*grid.columns()+gx));
            }
        }
        var selected=new ArrayList<Point>();int count=Math.min(request.maxPoints(),candidates.size());
        for(int i=0;i<count;i++) selected.add(candidates.get((int)((2L*i+1)*candidates.size()/(2L*count))));
        selected.sort(Comparator.comparingDouble((Point p)->p.x()*p.x()+p.y()*p.y()).thenComparingInt(Point::index));
        return new ImagePointCloud(selected);
    }
}
