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

/**
 * <p>Ezlib class to load, download &amp; append libraries into class path.<br>
 * Uses a gradle-like dependency format on load methods.</p>
 *
 * @author Rubenicos
 */
public class Ezlib {

    /**
     * Current ezlib version to download ezlib loader.
     */
    public static String VERSION = "-SNAPSHOT";

    /**
     * Change current ezlib version to another one, use "-SNAPSHOT" for latest commit.
     *
     * @param version Version string.
     */
    public static void setVersion(String version) {
        if (version != null) {
            VERSION = version;
        }
    }

    private final File folder;
    private final PublicClassLoader classLoader;
    private final Object loader;

    private String defaultRepository = "https://repo.maven.apache.org/maven2/";

    /**
     * Constructs an Ezlib using default libs folder at root path.
     */
    public Ezlib() {
        this(new File("libs"));
    }

    /**
     * Constructs an Ezlib with specified libs folder.
     *
     * @param folder Folder to save the downloaded files.
     */
    public Ezlib(File folder) {
        this(folder, null);
    }

    /**
     * Constructs an Ezlib with specified libs folder and class loader.
     *
     * @param folder      Folder to save the downloaded files.
     * @param classLoader Public class loader to add URLs.
     */
    public Ezlib(File folder, PublicClassLoader classLoader) {
        this(folder, classLoader, null);
    }

    /**
     * Constructs an Ezlib with all parameters.<br>
     * Take in count the "loader" will be used for {@link #relocate(File, File, Map)} and {@link #append(URL, ClassLoader)} methods.
     *
     * @param folder      Folder to save the downloaded files.
     * @param classLoader Public class loader to add URLs.
     * @param loader      Loader object to append and relocate files.
     */
    public Ezlib(File folder, PublicClassLoader classLoader, Object loader) {
        this.folder = folder;
        if (!this.folder.exists()) {
            this.folder.mkdirs();
        }
        this.classLoader = classLoader == null ? createClassLoader() : classLoader;
        this.loader = loader == null ? createLoader() : loader;
    }

    /**
     * Get current libs folder instance.
     *
     * @return A folder who save the downloaded files.
     */
    public File getFolder() {
        return folder;
    }

    /**
     * Get current class loader.
     *
     * @return A public class loader who save added URLs.
     */
    public PublicClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Get current loader instance.
     *
     * @return An object representing a URLs loader and class relocator.
     */
    public Object getLoader() {
        return loader;
    }

    /**
     * Get default repository to use when is not defined in load methods.
     *
     * @return A string that represent a URL.
     */
    public String getDefaultRepository() {
        return defaultRepository;
    }

    /**
     * Set default repository to use when is not defined in load methods.
     *
     * @param defaultRepository A string that represent a URL.
     * @return Current Ezlib instance.
     */
    public Ezlib setDefaultRepository(String defaultRepository) {
        this.defaultRepository = defaultRepository;
        return this;
    }

    /**
     * Create a public class loader, by default is created with ezlib loader dependency.
     *
     * @return A public class loader who save added URLs.
     */
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

    /**
     * Create a loader to use it for {@link #relocate(File, File, Map)} and {@link #append(URL, ClassLoader)} methods.
     *
     * @return A loader object to append and relocate files.
     */
    public Object createLoader() {
        try {
            Class<?> loader = Class.forName("com.saicone.ezlib.EzlibLoader", true, classLoader);
            return loader.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            t.printStackTrace();
            throw new NullPointerException("Can't initialize ezlib loader");
        }
    }

    /**
     * Close current public class loader.
     */
    public void close() {
        try {
            classLoader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Relocate a jar file including paths and imports and put the changes into an output file.<br>
     * If output file does not exist, it will be created.
     *
     * @param input     Input file to relocate.
     * @param output    Output file to put all the changes.
     * @param pattern   Path to relocate.
     * @param relocated Relocated path.
     * @throws Throwable If any error occurs on reflected method invoking.
     */
    public void relocate(File input, File output, String pattern, String relocated) throws Throwable {
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
     * @throws Throwable If any error occurs on reflected method invoking.
     */
    public void relocate(File input, File output, Map<String, String> relocations) throws Throwable {
        Method relocate = loader.getClass().getDeclaredMethod("relocate", File.class, File.class, Map.class);
        relocate.invoke(loader, input, output, relocations);
    }

    /**
     * Append a URL into current public class path instance.
     *
     * @param url URL to append.
     * @throws Throwable If any error occurs on reflected method invoking.
     */
    public void append(URL url) throws Throwable {
        append(url, false);
    }

    /**
     * Append a URL into class path.
     *
     * @param url    URL to append.
     * @param parent True if you want to append into parent class path.
     * @throws Throwable If any error occurs on reflected method invoking.
     */
    public void append(URL url, boolean parent) throws Throwable {
        if (parent) {
            append(url, Ezlib.class.getClassLoader());
        } else {
            getClassLoader().addURL(url);
        }
    }

    /**
     * Append a URL into defined class loader.
     *
     * @param url    URL to append.
     * @param loader Class loader to append.
     * @throws Throwable If any error occurs on reflected method invoking.
     */
    public void append(URL url, ClassLoader loader) throws Throwable {
        Method append = this.loader.getClass().getDeclaredMethod("append", URL.class, ClassLoader.class);
        append.invoke(this.loader, url, loader);
    }

    /**
     * Load a dependency using gradle-like format (group:artifact:version).
     *
     * @param dependency Dependency to load.
     * @return           True if dependency has loaded successfully.
     * @throws IllegalArgumentException If the dependency is not formatted correctly.
     */
    public boolean load(String dependency) {
        return load(dependency, false);
    }

    /**
     * Load a dependency using gradle-like format (group:artifact:version).
     *
     * @param dependency Dependency to load.
     * @param parent     True if you want to append the dependency into parent class path.
     * @return           True if dependency has loaded successfully.
     * @throws IllegalArgumentException If the dependency is not formatted correctly.
     */
    public boolean load(String dependency, boolean parent) {
        return load(dependency, defaultRepository, parent);
    }

    /**
     * Load a dependency using gradle-like format (group:artifact:version) and specified repository.
     *
     * @param dependency Dependency to load.
     * @param repository Repository to download the dependency from it.
     * @return           True if dependency has loaded successfully.
     * @throws IllegalArgumentException If the dependency is not formatted correctly.
     */
    public boolean load(String dependency, String repository) {
        return load(dependency, repository, false);
    }

    /**
     * Load a dependency using gradle-like format (group:artifact:version) and specified repository.
     *
     * @param dependency Dependency to load.
     * @param repository Repository to download the dependency from it.
     * @param parent     True if you want to append the dependency into parent class path.
     * @return           True if dependency has loaded successfully.
     * @throws IllegalArgumentException If the dependency is not formatted correctly.
     */
    public boolean load(String dependency, String repository, boolean parent) {
        return load(dependency, repository, null, parent);
    }

    /**
     * Load a dependency using gradle-like format (group:artifact:version) and specified repository
     * with class path relocations.
     *
     * @param dependency  Dependency to load.
     * @param repository  Repository to download the dependency from it.
     * @param relocations A map containing all the paths you want to relocate.
     * @return            True if dependency has loaded successfully.
     * @throws IllegalArgumentException If the dependency is not formatted correctly.
     */
    public boolean load(String dependency, String repository, Map<String, String> relocations) {
        return load(dependency, repository, relocations, false);
    }

    /**
     * Load a dependency using gradle-like format (group:artifact:version) and specified repository
     * with class path relocations.
     *
     * @param dependency  Dependency to load.
     * @param repository  Repository to download the dependency from it.
     * @param relocations A map containing all the paths you want to relocate.
     * @param parent      True if you want to append the dependency into parent class path.
     * @return            True if dependency has loaded successfully.
     * @throws IllegalArgumentException If the dependency is not formatted correctly.
     */
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

    /**
     * Load a dependency using gradle-like format (group:artifact:version) with class path relocations.
     *
     * @param dependency  Dependency to load.
     * @param relocations A map containing all the paths you want to relocate.
     * @return            True if dependency has loaded successfully.
     * @throws IllegalArgumentException If the dependency is not formatted correctly.
     */
    public boolean load(String dependency, Map<String, String> relocations) {
        return load(dependency, relocations, false);
    }

    /**
     * Load a dependency using gradle-like format (group:artifact:version) with class path relocations.
     *
     * @param dependency  Dependency to load.
     * @param relocations A map containing all the paths you want to relocate.
     * @param parent      True if you want to append the dependency into parent class path.
     * @return            True if dependency has loaded successfully.
     * @throws IllegalArgumentException If the dependency is not formatted correctly.
     */
    public boolean load(String dependency, Map<String, String> relocations, boolean parent) {
        return load(dependency, defaultRepository, relocations, parent);
    }

    /**
     * Download a dependency using gradle-like format (group:artifact:version) from repository url.
     *
     * @param dependency Dependency to load.
     * @param repository Repository to download the dependency from it.
     * @return           A file representing the downloaded dependency.
     * @throws IOException If any error occurs with the download.
     * @throws IllegalArgumentException If the dependency is not formatted correctly.
     */
    public File download(String dependency, String repository) throws IOException {
        String[] split = dependency.split(":", 4);
        if (split.length < 3) {
            throw new IllegalArgumentException("Malformatted dependency");
        }

        String repo = repository.endsWith("/") ? repository : repository + "/";
        String fullVersion = split[2] + (split.length < 4 ? "" : "-" + split[3].replace(":", "-"));

        String fileName = split[1] + "-" + fullVersion;
        String url = repo + split[0].replace(".", "/") + "/" + split[1] + "/" + split[2] + "/" + fileName + ".jar";

        if (!folder.exists()) {
            folder.mkdirs();
        }
        File file = new File(folder, fileName + ".jar");
        return file.exists() ? file : download(url, file);
    }

    /**
     * Download a dependency from URL and save into an output file.<br>
     * If output file does not exist, it will be created.
     *
     * @param url    A string that represent a URL.
     * @param output Output file to save the dependency.
     * @return       The same output file.
     * @throws IOException If any error occurs with the download.
     */
    public File download(String url, File output) throws IOException {
        return download(new URL(url), output);
    }

    /**
     * Download a dependency from URL and save into an output file.<br>
     * If output file does not exist, it will be created.
     *
     * @param url    URL to get the dependency.
     * @param output Output file to save the dependency.
     * @return       The same output file.
     * @throws IOException If any error occurs with the download.
     */
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

    /**
     * Simple PublicClassLoader class to add URLs with a public method.
     */
    public static class PublicClassLoader extends URLClassLoader {

        /**
         * Constructs an PublicClassLoader with defined URL to create the instance.
         *
         * @param url The URL to load classes and resources.
         */
        public PublicClassLoader(URL url) {
            this(new URL[]{url}, Ezlib.class.getClassLoader());
        }

        /**
         * Constructs an PublicClassLoader with defined URL to create the instance and
         * parent class loader.
         *
         * @param urls   The URLs to load classes and resources.
         * @param parent Parent class loader.
         */
        public PublicClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public void addURL(URL url) {
            super.addURL(url);
        }
    }
}
