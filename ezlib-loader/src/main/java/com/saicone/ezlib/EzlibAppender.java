package com.saicone.ezlib;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;

public class EzlibAppender {

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
            Object lookupBase = u.staticFieldBase(lookupField);
            long lookupOffset = u.staticFieldOffset(lookupField);
            l = (MethodHandles.Lookup) u.getObject(lookupBase, lookupOffset);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        unsafe = u;
        lookup = l;
    }

    EzlibAppender() {
    }

    public static void add(URL url) throws Throwable {
        add(url, EzlibLoader.class.getClassLoader());
    }

    public static void add(URL url, ClassLoader loader) throws Throwable {
        try {
            // Try to use 'addURL' method inside URLClassLoader
            add(url, loader, URLClassLoader.class);
        } catch (Throwable t) {
            // If any error occurs will be use the URLClassPath directly
            Object ucp = getUcp(loader);
            add(url, ucp, ucp.getClass());
        }
    }

    public static void add(URL url, Object loader, Class<?> clazz) throws Throwable {
        MethodHandle addURL = lookup.findVirtual(clazz, "addURL", MethodType.methodType(void.class, URL.class));
        add(url, loader, addURL);
    }

    public static void add(URL url, Object loader, MethodHandle addURL) throws Throwable {
        addURL.invoke(loader, url);
    }

    public static Object getUcp(ClassLoader loader) {
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
