package com.saicone.ezlib;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Ezlib {

    public static final String VERSION = "-SNAPSHOT";

    private final File folder;
    private final PublicClassLoader classLoader;
    private final Object loader;

    private String defaultRepository = "https://repo.maven.apache.org/maven2/";

    public Ezlib(File folder) {
        this(folder, null);
    }

    public Ezlib(File folder, PublicClassLoader classLoader) {
        this(folder, classLoader, null);
    }

    public Ezlib(File folder, PublicClassLoader classLoader, Object loader) {
        this.folder = folder;
        if (!this.folder.exists()) {
            this.folder.mkdirs();
        }
        this.classLoader = classLoader == null ? createClassLoader() : classLoader;
        this.loader = loader == null ? createLoader() : loader;
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

    public PublicClassLoader createClassLoader() {
        File file;
        try {
            file = download("com.saicone.ezlib:ezlib-loader:" + VERSION, "https://jitpack.io/");
        } catch (IOException e) {
            e.printStackTrace();
            throw new NullPointerException("Can't load ezlib loader from dependency");
        }

        try {
            return new PublicClassLoader(file.toURI().toURL());
        } catch (Throwable t) {
            t.printStackTrace();
            throw new NullPointerException("Can't create class loader for ezlib");
        }
    }

    public Object createLoader() {
        try {
            Class<?> loader = Class.forName("com.saicone.ezlib.EzlibLoader", true, classLoader);
            return loader.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            t.printStackTrace();
            throw new NullPointerException("Can't initialize ezlib loader");
        }
    }

    public void close() {
        try {
            classLoader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void relocate(File input, File output, String pattern, String relocated) throws Throwable {
        Map<String, String> map = new HashMap<>();
        map.put(pattern, relocated);
        relocate(input, output, map);
    }

    public void relocate(File input, File output, Map<String, String> relocations) throws Throwable {
        Method relocate = loader.getClass().getDeclaredMethod("relocate", File.class, File.class, Map.class);
        relocate.invoke(loader, input, output, relocations);
    }

    public void append(URL url) throws Throwable {
        append(url, false);
    }

    public void append(URL url, boolean parent) throws Throwable {
        if (parent) {
            append(url, Ezlib.class.getClassLoader());
        } else {
            getClassLoader().addURL(url);
        }
    }

    public void append(URL url, ClassLoader loader) throws Throwable {
        Method append = this.loader.getClass().getDeclaredMethod("append", URL.class, ClassLoader.class);
        append.invoke(this.loader, url, loader);
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
        try {
            File file = download(dependency, repository);
            if (relocations != null && !relocations.isEmpty()) {
                Path path = Files.createTempFile("[" + UUID.randomUUID() + "]" + file.getName(), ".tmp");
                path.toFile().deleteOnExit();
                relocate(file, path.toFile(), relocations);
                file = path.toFile();
            }

            append(file.toURI().toURL(), parent);
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

    public File download(String dependency, String repository) throws IOException {
        String[] split = dependency.split(":", 4);
        if (split.length < 3) {
            throw new IllegalArgumentException("Malformatted dependency");
        }

        String repo = repository.endsWith("/") ? repository : repository + "/";
        String fullVersion = split[2] + (split.length < 4 ? "" : "-" + split[3].replace(":", "-"));

        String fileName = split[1] + "-" + fullVersion;
        String url = repo + split[0].replace(".", "/") + "/" + split[1] + "/" + split[2] + "/" + fileName + ".jar";

        File file = new File(folder, fileName + ".jar");
        return file.exists() ? file : download(url, file);
    }

    public File download(String url, File output) throws IOException {
        return download(new URL(url), output);
    }

    public File download(URL url, File output) throws IOException {
        try (InputStream in = url.openStream(); OutputStream out = Files.newOutputStream(output.toPath())) {
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
