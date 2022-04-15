package com.saicone.ezlib;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public class Ezlib {

    public static final String VERSION = "-SNAPSHOT";
    public static final String LOADER_URL = "https://jitpack.io/com/saicone/ezlib/ezlib-loader/" + VERSION + "/ezlib-loader-" + VERSION + ".jar";

    private final File folder;
    private final PublicClassLoader classLoader;
    private final Object loader;

    private final Method relocate;
    private final Method append;
    private String defaultRepository = "https://repo.maven.apache.org/maven2/";

    public Ezlib(File folder) {
        this.folder = folder;
        try {
            Path path = Files.createTempFile("ezlib-loader-" + VERSION + "(" + UUID.randomUUID() +  ")", ".jar.tmp");
            path.toFile().deleteOnExit();
            File file = download(LOADER_URL, path.toFile());
            classLoader = new PublicClassLoader(file.toURI().toURL());
        } catch (Throwable t) {
            t.printStackTrace();
            throw new NullPointerException("Can't create class loader for ezlib");
        }

        try {
            Class<?> loaderClass = Class.forName("com.saicone.ezlib.EzlibLoader", true, classLoader);
            loader = loaderClass.getDeclaredConstructor().newInstance();
            relocate = loaderClass.getDeclaredMethod("relocate", File.class, File.class, Map.class);
            append = loaderClass.getDeclaredMethod("append", URL.class, ClassLoader.class);
        } catch (Throwable t) {
            t.printStackTrace();
            throw new NullPointerException("Can't initialize ezlib loader");
        }
    }

    public File getFolder() {
        return folder;
    }

    public PublicClassLoader getClassLoader() {
        return classLoader;
    }

    public Object getLoader() {
        return loader;
    }

    public String getDefaultRepository() {
        return defaultRepository;
    }

    public Ezlib setDefaultRepository(String defaultRepository) {
        this.defaultRepository = defaultRepository;
        return this;
    }

    public void close() {
        try {
            classLoader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean load(String dependency) {
        return load(dependency, false);
    }

    public boolean load(String dependency, boolean parent) {
        return load(dependency, defaultRepository, parent);
    }

    public boolean load(String dependency, String repository) {
        return load(dependency, repository, false);
    }

    public boolean load(String dependency, String repository, boolean parent) {
        return load(dependency, repository, null, parent);
    }

    public boolean load(String dependency, String repository, Map<String, String> relocations) {
        return load(dependency, repository, relocations, false);
    }

    public boolean load(String dependency, String repository, Map<String, String> relocations, boolean parent) {
        String[] split = dependency.split(":", 4);
        if (split.length < 3) {
            return false;
        }
        String repo = repository.endsWith("/") ? repository : repository + "/";
        String fullVersion = split[2] + (split.length < 4 ? "" : "-" + split[3].replace(":", "-"));

        String fileName = split[1] + "-" + fullVersion;
        String url = repo + split[0].replace(".", "/") + "/" + split[1] + "/" + split[2] + "/" + fileName + ".jar";

        try {
            File file = new File(folder, fileName + ".jar");
            if (relocations.isEmpty()) {
                if (!file.exists()) {
                    download(url, file);
                }
            } else {
                Path path = Files.createTempFile(fileName + "(" + UUID.randomUUID() + ")", ".jar.tmp");
                path.toFile().deleteOnExit();
                relocate.invoke(loader, file.exists() ? file : download(url, file), path.toFile(), relocations);
                file = path.toFile();
            }

            if (parent) {
                append.invoke(loader, file.toURI().toURL(), Ezlib.class.getClassLoader());
            } else {
                classLoader.addURL(file.toURI().toURL());
            }
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean load(String dependency, Map<String, String> relocations) {
        return load(dependency, relocations, false);
    }

    public boolean load(String dependency, Map<String, String> relocations, boolean parent) {
        return load(dependency, defaultRepository, relocations, parent);
    }

    public File download(String url, File output) throws IOException {
        return download(new URL(url), output);
    }

    public File download(URL url, File output) throws IOException {
        try (InputStream in = url.openStream(); OutputStream out = new FileOutputStream(output)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            return output;
        }
    }

    public static class PublicClassLoader extends URLClassLoader {

        public PublicClassLoader(URL url) {
            this(new URL[]{url}, Ezlib.class.getClassLoader());
        }

        public PublicClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public void addURL(URL url) {
            super.addURL(url);
        }
    }
}
