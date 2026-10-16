package com.yumorie.fireworkshow.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Validates headers before allocating decoded pixels; never retains decoded images. */
public final class SourceImageLoader {
    public record Metadata(int width,int height) { public long pixels() { return (long)width*height; } }
    private int decodes;
    public int decodes() { return decodes; }
    private ImageReader reader(ImageInputStream input) throws IOException {
        var readers=ImageIO.getImageReaders(input);
        if(!readers.hasNext()) throw new IOException("Invalid PNG");
        var reader=readers.next();
        if(!reader.getFormatName().equalsIgnoreCase("png")) { reader.dispose();throw new IOException("Not PNG"); }
        // Pixel data is required; ancillary PNG metadata (including compressed text) is not.
        reader.setInput(input,true,true);return reader;
    }
    private void file(Path file,ImageAssetService.Limits limits) throws IOException {
        if(!Files.isRegularFile(file)||Files.size(file)>limits.bytes()) throw new IOException("PNG missing or exceeds max-file-bytes: "+file);
    }
    private Metadata dimensions(ImageReader reader,ImageAssetService.Limits limits,long memory) throws IOException {
        int w=reader.getWidth(0),h=reader.getHeight(0);long pixels=(long)w*h;
        if(w<=0||h<=0||w>limits.width()||h>limits.height()||pixels>limits.pixels()) throw new IOException("PNG dimensions exceed limits: "+w+"x"+h);
        // Reserve 8 bytes/pixel for decoder output plus ARGB/access overhead, not just the final raster.
        if(pixels>memory/8) throw new IOException("PNG estimated decode memory exceeds max-decoded-bytes");
        return new Metadata(w,h);
    }
    public Metadata inspect(Path file,ImageAssetService.Limits limits,long memory) throws IOException {
        file(file,limits);
        try(var input=ImageIO.createImageInputStream(file.toFile())) {
            var reader=reader(input);try { return dimensions(reader,limits,memory); } finally { reader.dispose(); }
        }
    }
    public BufferedImage decode(Path file,ImageAssetService.Limits limits,long memory,Metadata expected) throws IOException {
        file(file,limits);
        try(var input=ImageIO.createImageInputStream(file.toFile())) {
            var reader=reader(input);
            try {
                if(!dimensions(reader,limits,memory).equals(expected)) throw new IOException("PNG changed during preprocessing: "+file);
                decodes++;return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
}
