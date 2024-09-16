package com.saicone.ezlib.internal13;

import me.lucko.jarrelocator.JarRelocator;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * EzlibRelocator class to relocate jar files.
 *
 * @author Rubenicos
 */
public class EzlibRelocator {

    /**
     * Relocate a jar file including paths and imports and put the changes into an output file.<br>
     * If output file does not exist, it will be created.
     *
     * @param input     Input file to relocate.
     * @param output    Output file to put all the changes.
     * @param pattern   Path to relocate.
     * @param relocated Relocated path.
     * @throws IOException If any error occurs on relocation.
     */
    public void relocate(File input, File output, String pattern, String relocated) throws IOException {
        Map<String, String> map = new HashMap<>();
        map.put(pattern, relocated);
        relocate(input, output, map);
    }

    /**
     * Relocate a jar file including paths and imports and put the changes into an output file.<br>
     * If output file does not exist, it will be created.
     *
     * @param input       Input file to relocate.
     * @param output      Output file to put all the changes.
     * @param relocations A map containing all the paths you want to relocate.
     * @throws IOException If any error occurs on relocation.
     */
    public void relocate(File input, File output, Map<String, String> relocations) throws IOException {
        JarRelocator relocator = new JarRelocator(input, output, relocations);
        relocator.run();
    }
}
