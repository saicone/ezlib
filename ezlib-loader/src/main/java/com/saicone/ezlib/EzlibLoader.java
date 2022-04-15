package com.saicone.ezlib;

import me.lucko.jarrelocator.JarRelocator;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class EzlibLoader {

    public void relocate(File input, File output, String pattern, String relocated) throws IOException {
        Map<String, String> map = new HashMap<>();
        map.put(pattern, relocated);
        relocate(input, output, map);
    }

    public void relocate(File input, File output, Map<String, String> relocations) throws IOException {
        JarRelocator relocator = new JarRelocator(input, output, relocations);
        relocator.run();
    }

    public void append(URL url, ClassLoader loader) throws Throwable {
        EzlibAppender.add(url, loader);
    }
}
