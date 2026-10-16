package com.yumorie.fireworkshow.image;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import com.yumorie.fireworkshow.image.ImagePointCloud.Point;
public final class ImageAssetService {
    public record Limits(long bytes, int width, int height, long pixels) {}
    public static Path resolve(Path root, String name) throws IOException {
        if (!name.matches("[A-Za-z0-9_-]+\\.png")) throw new IOException("image.file: expected local PNG filename");
        Path realRoot = root.toRealPath(), file = realRoot.resolve(name).toRealPath();
        if (!file.startsWith(realRoot)) throw new IOException("image.file: symlink escapes images/");
        return file;
    }
    public ImagePointCloud load(Path file, Limits limits, double width, int threshold, int max) throws IOException {
        if (Files.size(file) > limits.bytes) throw new IOException("PNG exceeds max-file-bytes");
        try (var input = ImageIO.createImageInputStream(file.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Invalid PNG");
            var reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase("png")) throw new IOException("Not PNG");
                reader.setInput(input);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w > limits.width || h > limits.height || (long) w*h > limits.pixels) throw new IOException("PNG dimensions exceed limits");
                var image = reader.read(0);
                var all = new ArrayList<Point>();
                for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
                    int argb=image.getRGB(x,y), alpha=argb>>>24;
                    if (alpha == 0 || alpha < threshold) continue;
                    all.add(new Point(((x+0.5)/w-0.5)*width, (0.5-(y+0.5)/h)*width*h/w, argb&0xffffff, alpha, y*w+x));
                }
                var sampled = new ArrayList<Point>();
                int count=Math.min(max,all.size());
                for(int i=0;i<count;i++) sampled.add(all.get((int)((long)i*all.size()/count)));
                sampled.sort(Comparator.comparingDouble((Point p)->p.x()*p.x()+p.y()*p.y()).thenComparingInt(Point::index));
                return new ImagePointCloud(sampled);
            } finally { reader.dispose(); }
        }
    }
}
