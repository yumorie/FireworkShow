package com.yumorie.fireworkshow.image;
import java.util.List;
public record ImagePointCloud(List<Point> points) {
    public ImagePointCloud { points = List.copyOf(points); }
    public record Point(double x, double y, int rgb, int alpha, int index) {}
}
