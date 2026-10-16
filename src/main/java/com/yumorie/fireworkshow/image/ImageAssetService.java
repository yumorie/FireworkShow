package com.yumorie.fireworkshow.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** One bounded preprocessing scope per reload. At most one decoded source is retained. */
public final class ImageAssetService implements AutoCloseable {
    public record Limits(long bytes,int width,int height,long pixels) {}
    public record PreprocessLimits(long decodedBytes,long decodedPixels,long pixelVisits,int gridCells,int variants,int points,int sources,int millis) {
        public static PreprocessLimits defaults() { return new PreprocessLimits(67108864,67108864,134217728,262144,512,200000,128,10000); }
    }
    public record Stats(int variants,int points,int sources,int decodes,long decodedPixels,long pixelVisits,long millis,long peakEstimatedDecodeBytes) {}
    private record SourceKey(Path path,Limits limits) {}
    private record Key(SourceKey source,SamplingRequest request) {}
    private final PreprocessLimits bounds;
    private final SourceImageLoader loader=new SourceImageLoader();
    private final ImageSampler sampler=new ImageSampler();
    private final Map<SourceKey,SourceImageLoader.Metadata> metadata=new HashMap<>();
    private final Map<Key,ImagePointCloud> cache=new LinkedHashMap<>();
    private final long started=System.nanoTime();
    private BufferedImage decoded;
    private SourceKey decodedKey;
    private long decodedPixels,pixelVisits,peakDecodeBytes;
    private int cachedPoints;
    private boolean closed;
    public ImageAssetService() { this(PreprocessLimits.defaults()); }
    public ImageAssetService(PreprocessLimits bounds) { this.bounds=bounds; }
    public static Path resolve(Path root,String name) throws IOException {
        if(!name.matches("[A-Za-z0-9_-]+\\.png")) throw new IOException("image.file: expected local PNG filename");
        Path realRoot=root.toRealPath(),file=realRoot.resolve(name).toRealPath();
        if(!file.startsWith(realRoot)) throw new IOException("image.file: symlink escapes images/");
        return file;
    }
    private void checkpoint() {
        if(closed) throw new IllegalStateException("Image preprocessing scope closed");
        if((System.nanoTime()-started)/1000000>bounds.millis()) throw new IllegalArgumentException("Image preprocessing exceeded max-preprocess-millis");
    }
    public SourceImageLoader.Metadata metadata(Path path,Limits limits) throws IOException {
        checkpoint();var key=new SourceKey(path.toRealPath(),limits);var found=metadata.get(key);
        if(found!=null) return found;
        if(metadata.size()>=bounds.sources()) throw new IllegalArgumentException("Image preprocessing exceeded max-sources");
        var result=loader.inspect(key.path(),limits,bounds.decodedBytes());metadata.put(key,result);return result;
    }
    public ImagePointCloud sample(Path path,Limits limits,SamplingRequest request) throws IOException {
        checkpoint();var source=new SourceKey(path.toRealPath(),limits);
        // Canonicalize harmless floating-point products such as 1.5*0.8 versus 1.2.
        request=request.atScale(Math.rint(request.scale()*1e9)/1e9);
        var key=new Key(source,request);var existing=cache.get(key);if(existing!=null) return existing;
        if(cache.size()>=bounds.variants()) throw new IllegalArgumentException("Image preprocessing exceeded max-sampling-variants");
        var info=metadata(source.path(),limits);var grid=sampler.grid(info.width(),info.height(),request,bounds.gridCells());
        long work=(info.width()+2L*grid.columns())*(info.height()+2L*grid.rows());
        if(work>bounds.pixelVisits()-pixelVisits) throw new IllegalArgumentException("Image preprocessing exceeded max-pixel-visits");
        pixelVisits+=work;
        if(!source.equals(decodedKey)) {
            if(info.pixels()>bounds.decodedPixels()-decodedPixels) throw new IllegalArgumentException("Image preprocessing exceeded max-decoded-pixels");
            releaseDecoded();decodedPixels+=info.pixels();
            decoded=loader.decode(source.path(),limits,bounds.decodedBytes(),info);decodedKey=source;
            peakDecodeBytes=Math.max(peakDecodeBytes,info.pixels()*8);
        }
        var cloud=sampler.sample(decoded,request,grid,this::checkpoint);
        if(cloud.points().size()>bounds.points()-cachedPoints) throw new IllegalArgumentException("Image preprocessing exceeded max-cached-points");
        cachedPoints+=cloud.points().size();cache.put(key,cloud);checkpoint();return cloud;
    }
    /** Standalone sampling operation for tools/tests, with the same safe loader and sampler. */
    public ImagePointCloud load(Path path,Limits limits,double width,int threshold,int max,double spacing) throws IOException {
        try(var scope=new ImageAssetService(bounds)) { return scope.sample(path,limits,new SamplingRequest(width,1,spacing,threshold,max,1)); }
    }
    public Stats stats() { return new Stats(cache.size(),cachedPoints,metadata.size(),loader.decodes(),decodedPixels,pixelVisits,(System.nanoTime()-started)/1000000,peakDecodeBytes); }
    public boolean retainsDecodedImage() { return decoded!=null; }
    private void releaseDecoded() { if(decoded!=null) decoded.flush();decoded=null;decodedKey=null; }
    @Override public void close() { releaseDecoded();closed=true; }
}