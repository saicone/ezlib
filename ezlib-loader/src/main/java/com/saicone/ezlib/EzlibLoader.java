package com.saicone.ezlib;

import me.lucko.jarrelocator.JarRelocator;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

/**
 * EzlibLoader class to relocate and append classes.
 *
 * @author Rubenicos
 */
public class EzlibLoader {

    private static final Unsafe unsafe;
    private static final MethodHandles.Lookup lookup;

    static {
        Unsafe u = null;
        MethodHandles.Lookup l = null;
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            u = (Unsafe) field.get(null);

            Field lookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            l = (MethodHandles.Lookup) u.getObject(u.staticFieldBase(lookupField), u.staticFieldOffset(lookupField));
        } catch (Throwable t) {
            t.printStackTrace();
        }
        unsafe = u;
        lookup = l;
    }

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

    /**
     * Append a URL into loader parent class loader.
     *
     * @param url URL to append.
     * @throws Throwable If any error occurs on reflected method invoking.
     */
    public void append(URL url) throws Throwable {
        append(url, EzlibLoader.class.getClassLoader());
    }

    /**
     * Append a URL into defined class loader.
     *
     * @param url    URL to append.
     * @param loader Class loader to append.
     * @throws Throwable If any error occurs on reflected method invoking.
     */
    public void append(URL url, ClassLoader loader) throws Throwable {
        try {
            // Try to use 'addURL' method inside URLClassLoader
            append(url, loader, URLClassLoader.class);
        } catch (Throwable t) {
            // If any error occurs will be use the URLClassPath directly
            Object ucp = getLoaderUcp(loader);
            append(url, ucp, ucp.getClass());
        }
    }

    /**
     * Append a URL into defined class loader and class to find the "addURL" method.
     *
     * @param url    URL to append
     * @param loader Class loader to append.
     * @param clazz  Class to find the "addURL" method.
     * @throws Throwable If any error occurs on reflected method invoking.
     */
    public void append(URL url, Object loader, Class<?> clazz) throws Throwable {
        MethodHandle addURL = lookup.findVirtual(clazz, "addURL", MethodType.methodType(void.class, URL.class));
        append(url, loader, addURL);
    }

    /**
     * Append a URL into defined class loader and MethodHandle.
     *
     * @param url    URL to append
     * @param loader Class loader to append.
     * @param addURL Method to invoke with loader.
     * @throws Throwable If any error occurs on reflected method invoking.
     */
    public void append(URL url, Object loader, MethodHandle addURL) throws Throwable {
        addURL.invoke(loader, url);
    }

    /**
     * Get the URLClassPath from defined class loader.
     *
     * @param loader Class loader to get URLClassPath.
     * @return       An object representing URLClassPath inside class loader.
     */
    public static Object getLoaderUcp(ClassLoader loader) {
        Field field;
        try {
            field = URLClassLoader.class.getDeclaredField("ucp");
        } catch (NoSuchFieldError | NoSuchFieldException e) {
            try {
                field = loader.getClass().getDeclaredField("ucp");
            } catch (NoSuchFieldError | NoSuchFieldException ex) {
                try {
                    field = loader.getClass().getSuperclass().getDeclaredField("ucp");
                } catch (NoSuchFieldException exception) {
                    throw new NullPointerException("Can't find URLClassPath field from " + loader.getClass().getName() + " class");
                }
            }
        }
        return unsafe.getObject(loader, unsafe.objectFieldOffset(field));
    }
}
