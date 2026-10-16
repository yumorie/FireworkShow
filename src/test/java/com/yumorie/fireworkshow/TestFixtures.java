package com.yumorie.fireworkshow;

import com.yumorie.fireworkshow.config.BundledResources;
import java.io.*;
import java.nio.file.*;
import java.util.Objects;

/** Stable inputs for behavioral tests, separate from editable installation defaults. */
final class TestFixtures {
    private TestFixtures() {}
    static InputStream open(String name) {
        String prefix=name.startsWith("images/")?"/":"/fixtures/";
        return Objects.requireNonNull(TestFixtures.class.getResourceAsStream(prefix+name),"Missing fixture: "+name);
    }
    static void install(Path root) throws IOException {
        for(String name:BundledResources.FILES) {
            Path target=root.resolve(name);Files.createDirectories(target.getParent());
            try(var input=open(name)) { Files.copy(input,target,StandardCopyOption.REPLACE_EXISTING); }
        }
    }
}
