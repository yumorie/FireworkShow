package com.yumorie.fireworkshow.config;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** Installs bundled defaults once; never replaces an existing server file. */
public final class BundledResources {
    public static final List<String> FILES=List.of("config.yml","shows/heart.yml","shows/alternate-heart.yml",
            "shows/multitrack.yml","shows/multi-image.yml","images/heart.png","images/text.png","images/mczjulogo.png");
    private BundledResources() {}
    public static void install(Path root) throws IOException {
        for(String resource:FILES) {
            Path target=root.resolve(resource);
            if(Files.exists(target)) continue;
            Files.createDirectories(target.getParent());
            try(var input=BundledResources.class.getResourceAsStream("/"+resource)) {
                if(input==null) throw new IOException("Missing bundled resource: "+resource);
                Files.copy(input,target);
            }
        }
    }
}
