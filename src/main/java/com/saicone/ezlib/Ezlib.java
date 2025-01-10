package com.saicone.ezlib;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * <p>Ezlib class to load, download &amp; append libraries into class path.<br>
 * Uses a gradle-like dependency format on load methods.</p>
 *
 * @author Rubenicos
 */
public class Ezlib {

    private static final String DEFAULT_FOLDER = "libs";

    /**
     * Original ezlib package group that cannot be affected with relocations.
     */
    public static final String GROUP = new String(new char[] {'c', 'o', 'm', '.', 's', 'a', 'i', 'c', 'o', 'n', 'e', '.', 'e', 'z', 'l', 'i', 'b'});
    /**
     * Current ezlib version to download ezlib loader.
     */
    public static String VERSION = "${version}";

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

    // Object parameters
    private final File folder;

    // Explicit initialization params
    private PublicClassLoader publicClassLoader;
    private Loader loader;

    // Object options
    private ClassLoader parentClassLoader = Ezlib.class.getClassLoader();
    private String defaultRepository = "https://repo.maven.apache.org/maven2/";
    private boolean pathSave = true;

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
        this.folder = folder == null ? new File(DEFAULT_FOLDER) : folder;
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
     * Get current class loader.<br>
     * Take in count this value must be initialized using {@link #init()}
     *
     * @return A public class loader who save added URLs.
     */
    public PublicClassLoader getPublicClassLoader() {
        return publicClassLoader;
    }

    /**
     * Get current loader instance.<br>
     * Take in count this value must be initialized using {@link #init()}
     *
     * @return An object representing a URLs loader and class relocator.
     */
    public Loader getLoader() {
        return loader;
    }

    /**
     * Get current parent class loader.
     *
     * @return A class loader to append files.
     */
    public ClassLoader getParentClassLoader() {
        return parentClassLoader;
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
     * Gets if the current ezlib is saving dependencies into sub folders.
     *
     * @return true if it is saving into sub folders.
     */
    public boolean isPathSave() {
        return pathSave;
    }

    /**
     * Get if the current ezlib is already initialized once.
     *
     * @return try if it was initialized.
     */
    public boolean isInitialized() {
        return publicClassLoader != null && loader != null;
    }

    /**
     * Set parent class loader who is used to append files.
     *
     * @param parentClassLoader A class loader to append files.
     */
    public void setParentClassLoader(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
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
     * Change the current method to save downloaded dependencies.
     *
     * @param pathSave true to save dependencies into sub folders.
     */
    public void setPathSave(boolean pathSave) {
        this.pathSave = pathSave;
    }

    /**
     * Initialize ezlib.
     *
     * @return the current ezlib instance.
     */
    public Ezlib init() {
        return init(null, null);
    }

    /**
     * Initialize ezlib with defined PublicClassLoader.
     *
     * @param publicClassLoader class loader to use.
     * @return                  the current ezlib instance.
     */
    public Ezlib init(PublicClassLoader publicClassLoader) {
        return init(publicClassLoader, null);
    }

    /**
     * Initialize ezlib with defined Loader.
     *
     * @param loader loader to use.
     * @return       the current ezlib instance.
     */
    public Ezlib init(Loader loader) {
        return init(null, loader);
    }

    /**
     * Initialize with defined PublicClassLoader and Loader.
     *
     * @param publicClassLoader class loader to use.
     * @param loader            loader to use.
     * @return                  the current ezlib instance.
     */
    public Ezlib init(PublicClassLoader publicClassLoader, Loader loader) {
        this.publicClassLoader = publicClassLoader == null ? createClassLoader() : publicClassLoader;
        this.loader = loader == null ? createLoader() : loader;
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
            file = download(GROUP + ":internal:" + VERSION, "https://jitpack.io/");
        } catch (IOException e) {
            throw new RuntimeException("Can't download ezlib internal classes from dependency", e);
        }

        try {
            return new PublicClassLoader(file.toURI().toURL());
        } catch (Throwable t) {
            throw new RuntimeException("Can't create public class loader for ezlib", t);
        }
    }

    /**
     * Create a loader to use it for {@link Loader#relocate(File, File, Map)} and {@link Loader#append(URL, ClassLoader)} methods.
     *
     * @return A loader object to append and relocate files.
     */
    public Loader createLoader() {
        try {
            return new Loader();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot find the required classes from PublicClassLoader", e);
        }
    }

    /**
     * Close current public class loader.
     */
    public void close() {
        if (publicClassLoader != null) {
            try {
                publicClassLoader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Create loadable dependency with provided file.
     *
     * @param file File to load has dependency.
     * @return     the dependency itself.
     */
    public LoadableDependency dependency(File file) {
        return new LoadableDependency(file);
    }

    /**
     * Create loadable dependency with provided gradle-like path.
     *
     * @param path Gradle like path to load dependency.
     * @return     the dependency itself.
     */
    public LoadableDependency dependency(String path) {
        return new LoadableDependency(path);
    }

    /**
     * Create loadable dependency with provided gradle-like path and repository url.
     *
     * @param path       Gradle like path to load dependency.
     * @param repository Repository url to download from.
     * @return           the dependency itself.
     */
    public LoadableDependency dependency(String path, String repository) {
        return new LoadableDependency(path).repository(repository);
    }

    private void load(LoadableDependency dependency) throws IllegalArgumentException {
        File file = dependency.file;
        if (dependency.file == null) {
            try {
                file = download(dependency.path, dependency.repository, dependency.urlFormat);
            } catch (IOException e) {
                throw new RuntimeException("Can't download '" + dependency + "' dependency", e);
            }
        }

        if (dependency.relocations != null && !dependency.relocations.isEmpty()) {
            Path path;
            try {
                path = Files.createTempFile(file.getName() + '.' + Math.abs(dependency.relocations.hashCode()), ".tmp");
            } catch (IOException e) {
                throw new RuntimeException("Cannot create temporary file for relocated dependency", e);
            }
            path.toFile().deleteOnExit();
            try {
                loader.relocate(file, path.toFile(), dependency.relocations);
            } catch (Throwable t) {
                throw new RuntimeException("Cannot relocate dependency");
            }
            file = path.toFile();
        }

        try {
            loader.append(file.toURI().toURL(), dependency.parent);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Cannot convert dependency file to URL");
        } catch (Throwable t) {
            throw new RuntimeException("Cannot append dependency into " + (dependency.parent ? "parent" : "child") + " class path", t);
        }
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
    public File download(String dependency, String repository) throws IOException, IllegalArgumentException {
        return download(dependency, repository, null);
    }

    /**
     * Download a dependency using gradle-like format (group:artifact:version) from repository with defined url format.
     *
     * @param dependency Dependency to load.
     * @param repository Repository to download the dependency from it.
     * @param urlFormat  Url download format.
     * @return           A file representing the downloaded dependency.
     * @throws IOException If any error occurs with the download.
     * @throws IllegalArgumentException If the dependency is not formatted correctly.
     */
    public File download(String dependency, String repository, String urlFormat) throws IOException, IllegalArgumentException {
        String path = parseUrl(dependency, urlFormat != null ? urlFormat : "%group%/%artifact%/%version%/%artifact%-%fileVersion%.jar");
        File file = findFile(path);
        return file.exists() ? file : download(parseRepository(repository != null ? repository : defaultRepository) + path, file);
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
        final URLConnection con = url.openConnection();
        con.addRequestProperty("User-Agent", "Mozilla/5.0");
        try (InputStream in = con.getInputStream(); OutputStream out = Files.newOutputStream(output.toPath())) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            return output;
        }
    }

    /**
     * Parse the provided repository to use for download dependencies.
     *
     * @param repository Repository url.
     * @return           The repository url correctly formatted.
     */
    public String parseRepository(String repository) {
        return repository.endsWith("/") ? repository : repository + "/";
    }

    /**
     * Parse the provided dependency using url format.
     *
     * @param dependency The dependency gradle-like path.
     * @param urlFormat  The url format after repository url.
     * @return           The current dependency as url format to use with repository url.
     * @throws IllegalArgumentException if the dependency is not formatted correctly.
     */
    public String parseUrl(String dependency, String urlFormat) throws IllegalArgumentException {
        final String[] split = dependency.split(":");
        if (split.length < 3) {
            throw new IllegalArgumentException("Malformed dependency");
        }
        String group = split[0].replace(".", "/");
        StringJoiner fileVersion = new StringJoiner("-");
        for (int i = 2; i < split.length; i++) {
            final String s = split[i];
            final int index = s.indexOf('@') + 1;
            if (index > 1 && index < s.length()) {
                fileVersion.add(s.substring(index));
                split[i] = s.substring(0, index - 1);
            } else {
                fileVersion.add(s);
            }
        }

        return urlFormat
                .replace("%group%", group)
                .replace("%artifact%", split[1])
                .replace("%version%", split[2])
                .replace("%fileVersion%", fileVersion.toString());
    }

    private File findFile(String path) {
        File folder = this.folder;
        int index = path.lastIndexOf('/');
        String name = path.substring(index + 1);
        if (pathSave && index > 0) {
            for (String s : path.substring(0, index).split("/")) {
                folder = new File(folder, s);
            }
        }
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return new File(folder, name);
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

    /**
     * Loader class to relocate and append files.
     */
    public class Loader {

        private final Object appender;
        private final Object relocator;
        private final Method appendMethod;
        private final Method relocateMethod;

        /**
         * Initialize loader with default parameters.
         *
         * @throws ClassNotFoundException if cannot find the required classes to initialize.
         */
        public Loader() throws ClassNotFoundException {
            this(
                    Class.forName(GROUP + ".internal." + VERSION + ".EzlibAppender", true, publicClassLoader),
                    Class.forName(GROUP + ".internal." + VERSION + ".EzlibRelocator", true, publicClassLoader)
            );
        }

        /**
         * Initialize loader with defined appender and relocator classes.
         *
         * @param appenderClass  Appender class.
         * @param relocatorClass Relocator class.
         */
        public Loader(Class<?> appenderClass, Class<?> relocatorClass) {
            try {
                this.appender = appenderClass.getDeclaredConstructor().newInstance();
                this.relocator = relocatorClass.getDeclaredConstructor().newInstance();
                this.appendMethod = appenderClass.getDeclaredMethod("append", URL.class, ClassLoader.class);
                this.relocateMethod = relocatorClass.getDeclaredMethod("relocate", File.class, File.class, Map.class);
            } catch (Exception e) {
                throw new RuntimeException("Cannot initialize Loader from Ezlib", e);
            }
        }

        /**
         * Initialize loader with defined appender and relocator with its methods.
         *
         * @param appender       Appender to use.
         * @param relocator      Relocator to use.
         * @param appendMethod   Append method.
         * @param relocateMethod Relocate method
         */
        public Loader(Object appender, Object relocator, Method appendMethod, Method relocateMethod) {
            this.appender = appender;
            this.relocator = relocator;
            this.appendMethod = appendMethod;
            this.relocateMethod = relocateMethod;
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
            relocateMethod.invoke(relocator, input, output, relocations);
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
                append(url, getParentClassLoader());
            } else {
                getPublicClassLoader().addURL(url);
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
            appendMethod.invoke(appender, url, loader);
        }
    }

    /**
     * Loadable dependency class to edit dependency after load.
     */
    public class LoadableDependency {

        private final String path;
        private final File file;

        private String repository;
        private String urlFormat;
        private Map<String, String> relocations;
        private boolean parent;

        /**
         * Constructs a loadable dependency using gradle-like path format (group:artifact:version).
         *
         * @param path Dependency path.
         */
        public LoadableDependency(String path) {
            this.path = path;
            this.file = null;
        }

        /**
         * Constructs a loadable dependency using existing file.
         *
         * @param file File to load as dependency.
         */
        public LoadableDependency(File file) {
            this.path = null;
            this.file = file;
        }

        /**
         * Set dependency repository url.
         *
         * @param repository Repository url to download dependency from.
         * @return           the current dependency object.
         */
        public LoadableDependency repository(String repository) {
            this.repository = repository;
            return this;
        }

        /**
         * Set dependency url format to download.
         *
         * @param urlFormat Url download format.
         * @return          the current dependency object.
         */
        public LoadableDependency urlFormat(String urlFormat) {
            this.urlFormat = urlFormat;
            return this;
        }

        /**
         * Set dependency relocations.
         *
         * @param relocations Relocations to apply when dependency is loaded.
         * @return            the current dependency object.
         */
        public LoadableDependency relocations(Map<String, String> relocations) {
            this.relocations = relocations;
            return this;
        }

        /**
         * Change the dependency load method.
         *
         * @param parent true to load dependency into parent class loader.
         * @return       the current dependency object.
         */
        public LoadableDependency parent(boolean parent) {
            this.parent = parent;
            return this;
        }

        /**
         * Load the current dependency.
         *
         * @throws IllegalArgumentException if the dependency is not formatted correctly.
         */
        public void load() throws IllegalArgumentException {
            Ezlib.this.load(this);
        }
    }
}
