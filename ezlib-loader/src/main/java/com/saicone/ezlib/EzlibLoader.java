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

    public void relocate(File input, File output, String pattern, String relocated) throws IOException {
        Map<String, String> map = new HashMap<>();
        map.put(pattern, relocated);
        relocate(input, output, map);
    }

    public void relocate(File input, File output, Map<String, String> relocations) throws IOException {
        JarRelocator relocator = new JarRelocator(input, output, relocations);
        relocator.run();
    }

    public void append(URL url) throws Throwable {
        append(url, EzlibLoader.class.getClassLoader());
    }

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

    public void append(URL url, Object loader, Class<?> clazz) throws Throwable {
        MethodHandle addURL = lookup.findVirtual(clazz, "addURL", MethodType.methodType(void.class, URL.class));
        append(url, loader, addURL);
    }

    public void append(URL url, Object loader, MethodHandle addURL) throws Throwable {
        addURL.invoke(loader, url);
    }

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
