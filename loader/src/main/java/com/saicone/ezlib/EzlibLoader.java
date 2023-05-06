package com.saicone.ezlib;

import com.google.gson.Gson;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * EzlibLoader class to load &amp; apply all the needed dependencies from files
 * into class path using {@link Ezlib}.
 *
 * @author Rubenicos
 */
public class EzlibLoader {

    private static final Pattern NODE_VARIABLE = Pattern.compile("\\$\\{([^}]+)}");
    private static final boolean USE_ANNOTATIONS;

    static {
        boolean useAnnotations;
        try {
            Class.forName("com.saicone.ezlib.Repository");
            Class.forName("com.saicone.ezlib.Dependency");
            Class.forName("com.saicone.ezlib.Dependencies");
            useAnnotations = true;
        } catch (ClassNotFoundException e) {
            useAnnotations = false;
        }
        USE_ANNOTATIONS = useAnnotations;
    }

    // Loader parameters
    private final ClassLoader classLoader;
    private final File folder;
    private final String[] files;
    // Explicit initialization parameters
    private final Ezlib ezlib;
    private XmlParser xmlParser;

    // Loadable objects
    private final List<Repository> repositories = new ArrayList<>();
    private final List<Dependency> dependencies = new ArrayList<>();
    private final Map<String, String> relocations = new HashMap<>();
    private final Map<String, Predicate<String>> conditions = new HashMap<>();
    private final Set<Dependency> applied = new HashSet<>();

    // Loader options
    private BiConsumer<Integer, String> logger = (level, text) -> {};
    private final Map<String, String> replaces = new HashMap<>();
    private final Map<String, BiConsumer<Reader, EzlibLoader>> fileReaders = new HashMap<>();

    /**
     * Constructs an EzlibLoader.
     */
    public EzlibLoader() {
        this("ezlib-dependencies.json");
    }

    /**
     * Constructs an EzlibLoader.
     *
     * @param files an array of file names.
     */
    public EzlibLoader(String... files) {
        this(EzlibLoader.class.getClassLoader(), null, true, files);
    }

    /**
     * Constructs an EzlibLoader.
     *
     * @param useDefaultOptions true to initialize default options.
     * @param files             an array of file names.
     */
    public EzlibLoader(boolean useDefaultOptions, String... files) {
        this(EzlibLoader.class.getClassLoader(), null, useDefaultOptions, files);
    }

    /**
     * Constructs an EzlibLoader.
     *
     * @param folder folder to save downloaded dependencies files.
     * @param files  an array of file names.
     */
    public EzlibLoader(File folder, String... files) {
        this(EzlibLoader.class.getClassLoader(), folder, true, files);
    }

    /**
     * Constructs an EzlibLoader.
     *
     * @param folder            folder to save downloaded dependencies files.
     * @param useDefaultOptions true to initialize default options.
     * @param files             an array of file names.
     */
    public EzlibLoader(File folder, boolean useDefaultOptions, String... files) {
        this(EzlibLoader.class.getClassLoader(), folder, useDefaultOptions, files);
    }

    /**
     * Constructs an EzlibLoader.
     *
     * @param classLoader the class loader to append files.
     * @param files       an array of file names.
     */
    public EzlibLoader(ClassLoader classLoader, String... files) {
        this(classLoader, null, true, files);
    }

    /**
     * Constructs an EzlibLoader.
     *
     * @param classLoader       the class loader to append files.
     * @param useDefaultOptions true to initialize default options.
     * @param files             an array of file names.
     */
    public EzlibLoader(ClassLoader classLoader, boolean useDefaultOptions, String... files) {
        this(classLoader, null, useDefaultOptions, files);
    }

    /**
     * Constructs an EzlibLoader.
     *
     * @param classLoader the class loader to append files.
     * @param folder      folder to save downloaded dependencies files.
     * @param files       an array of file names.
     */
    public EzlibLoader(ClassLoader classLoader, File folder, String... files) {
        this(classLoader, folder, true, files);
    }

    /**
     * Constructs an EzlibLoader.
     *
     * @param classLoader       the class loader to append files.
     * @param folder            folder to save downloaded dependencies files.
     * @param useDefaultOptions true to initialize default options.
     * @param files             an array of file names.
     */
    public EzlibLoader(ClassLoader classLoader, File folder, boolean useDefaultOptions, String... files) {
        this(classLoader, folder, new Ezlib(folder), useDefaultOptions, files);
    }

    /**
     * Constructs an EzlibLoader.
     *
     * @param classLoader the class loader to append files.
     * @param folder      folder to save downloaded dependencies files.
     * @param ezlib       the ezlib instance to use for append and relocation.
     * @param files       an array of file names.
     */
    public EzlibLoader(ClassLoader classLoader, File folder, Ezlib ezlib, String... files) {
        this(classLoader, folder, ezlib, true, files);
    }

    /**
     * Constructs an EzlibLoader.
     *
     * @param classLoader       the class loader to append files.
     * @param folder            folder to save downloaded dependencies files.
     * @param ezlib             the ezlib instance to use for append and relocation.
     * @param useDefaultOptions true to initialize default options.
     * @param files             an array of file names.
     */
    public EzlibLoader(ClassLoader classLoader, File folder, Ezlib ezlib, boolean useDefaultOptions, String... files) {
        this.classLoader = classLoader;
        this.folder = folder;
        if (files == null || (files.length > 0 && files[0] == null)) {
            this.files = new String[0];
        } else {
            this.files = files.length < 1 ? new String[] {"ezlib-dependencies.json"} : files;
        }
        this.ezlib = ezlib;
        ezlib.setParentClassLoader(classLoader);
        if (useDefaultOptions) {
            initDefaultOptions();
        }
    }

    /**
     * Add a global relocation to loader.
     *
     * @param pattern     the source pattern to relocate.
     * @param destination the destination package.
     * @return            the current ezlib loader.
     */
    public EzlibLoader relocate(String pattern, String destination) {
        relocations.put(pattern, destination);
        return this;
    }

    /**
     * Add a condition to loader.
     *
     * @param key       condition key.
     * @param predicate predicate to evaluate condition parameters as String.
     * @return          the current ezlib loader.
     */
    public EzlibLoader condition(String key, Predicate<String> predicate) {
        conditions.put(key.toLowerCase(), predicate);
        return this;
    }

    /**
     * Set the loader logger.
     *
     * @param logger consumer to accept log level and message.
     * @return       the current ezlib loader.
     */
    public EzlibLoader logger(BiConsumer<Integer, String> logger) {
        this.logger = logger;
        return this;
    }

    /**
     * Add a replacement that will be applied to relocations and dependency paths.
     *
     * @param target      the sequence of char values to be replaced
     * @param replacement the replacement sequence of char value
     * @return            the current ezlib loader.
     */
    public EzlibLoader replace(String target, String replacement) {
        replaces.put(target, replacement);
        return this;
    }

    /**
     * Add a file reader for defined file type.
     *
     * @param type       file type.
     * @param fileReader consumer that accept a reader with the actual ezlib loader.
     * @return           the current ezlib loader.
     */
    public EzlibLoader fileReader(String type, BiConsumer<Reader, EzlibLoader> fileReader) {
        fileReaders.put(type, fileReader);
        return this;
    }

    /**
     * Get the actual class loader.
     *
     * @return the class loader that are used to append files.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Get the actual folder.
     *
     * @return the folder who dependencies files are saved.
     */
    public File getFolder() {
        return folder == null ? ezlib.getFolder() : folder;
    }

    /**
     * Get the actual files.
     *
     * @return the files to load dependencies information.
     */
    public String[] getFiles() {
        return files;
    }

    /**
     * Get the actual ezlib instance.
     *
     * @return the ezlib instance.
     */
    public Ezlib getEzlib() {
        return ezlib;
    }

    /**
     * Get the actual XML parser instance.
     *
     * @return the XML parser instance.
     */
    public XmlParser getXmlParser() {
        return xmlParser;
    }

    /**
     * Get a reader for the provided file name.
     *
     * @param name file name to read.
     * @return     a compatible reader for file name or null.
     * @throws IOException if an error occurs on reader initialization.
     */
    public Reader getReader(String name) throws IOException {
        final int index = name.indexOf(':');
        if (index > 0 && index + 1 < name.length()) {
            return getReader(name.substring(0, index), name.substring(index + 1));
        } else {
            return getReader("", name);
        }
    }

    /**
     * Get a reader for the provided file type and name.
     *
     * @param type file type to read.
     * @param name the file name.
     * @return     a compatible reader for file type or null.
     * @throws IOException if an error occurs on reader initialization.
     */
    public Reader getReader(String type, String name) throws IOException {
        switch (type.toLowerCase()) {
            case "file":
                final File file = new File(name);
                return file.exists() ? new FileReader(file) : null;
            case "url":
                return new BufferedReader(new InputStreamReader(new URL(name).openConnection().getInputStream()));
            case "http":
            case "https":
                return new BufferedReader(new InputStreamReader(new URL(type + ':' + name).openConnection().getInputStream()));
            case "input":
            case "inputstream":
                final InputStream in = classLoader.getResourceAsStream(name);
                return in == null ? null : new InputStreamReader(in);
            default:
                return getDefaultReader(type + (type.isEmpty() ? "" : ":") + name);
        }
    }

    /**
     * Get default reader for any type of file name.
     *
     * @param name the file name.
     * @return     a reader for the file name null.
     */
    public Reader getDefaultReader(String name) {
        final InputStream in = classLoader.getResourceAsStream(name);
        return in == null ? null : new InputStreamReader(in);
    }

    /**
     * Initialize the current ezlib loader.
     */
    public void init() {
        logger.accept(4, "Initializing EzlibLoader...");
        if (!ezlib.isInitialized()) {
            ezlib.init();
            logger.accept(4, "Successfully initialized Ezlib instance...");
        } else {
            logger.accept(3, "Ezlib is already initialized...");
        }
        if (xmlParser == null) {
            xmlParser = new XmlParser();
        }
    }

    /**
     * Initialize all default options for this instance.
     */
    public void initDefaultOptions() {
        repositories.add(new Repository().name("MavenCentral").url("https://repo.maven.apache.org/maven2/"));
        repositories.add(new Repository().name("Jitpack").url("https://jitpack.io/"));
        replaces.put("{}", ".");
        replaces.put("{package}", EzlibLoader.class.getPackage().getName());
        try {
            fileReaders.put("json", (reader, loader) -> new Gson().fromJson(reader, Dependencies.class).load(loader));
        } catch (Throwable ignored) { }
    }

    /**
     * Load all the needed information and apply to class loader using the current Ezlib instance.
     *
     * @return the current ezlib loader.
     */
    public EzlibLoader load() {
        logger.accept(4, "Executing loader...");
        // Load ezlib instance using the current class loader
        init();

        // Load static fields from classes that override the current ezlib loader
        loadClass();

        // Load files from JAR, folders or url
        loadFiles();

        // Apply all loaded dependencies using global parameters
        logger.accept(3, "Applying all dependencies...");
        int count = 0;
        // Avoid ConcurrentModificationException
        for (int i = 0; i < dependencies.size(); i++) {
            if (applyDependency(dependencies.get(i))) {
                count++;
            }
        }
        logger.accept(3, "Applied " + count + " dependenc" + (count == 1 ? "y" : "ies"));
        // Return the loader itself
        return this;
    }

    /**
     * Load all the needed information from current files.
     */
    public void loadFiles() {
        logger.accept(4, "Loading " + files.length + " file" + (files.length == 1 ? "" : "s") + ": " + String.join(", ", files));
        logger.accept(4, "Using " + fileReaders.size() + " file reader" + (fileReaders.size() == 1 ? "" : "s") + ": " + String.join(", ", fileReaders.keySet()));
        for (String name : files) {
            loadFile(name);
        }
    }

    /**
     * Load all the needed information from provided file.
     *
     * @param name the file name.
     */
    public void loadFile(String name) {
        logger.accept(4, "Loading file " + name);
        boolean removeSuffix = false;
        int index = name.lastIndexOf('.');
        if (name.lastIndexOf('?') > index) {
            removeSuffix = true;
            index = name.lastIndexOf('?');
        }
        index++;
        if (index < 2 || index >= name.length()) {
            logger.accept(2, "The file '" + name + "' doesn't have any type");
            return;
        }
        final String type = name.substring(index).trim().toLowerCase();
        final BiConsumer<Reader, EzlibLoader> fileReader = fileReaders.get(type);
        if (fileReader == null) {
            logger.accept(2, "Cannot find file reader for file type '" + type + "'");
            return;
        }
        try (Reader reader = getReader(removeSuffix ? name.substring(0, index - 1) : name)) {
            if (reader == null) {
                logger.accept(2, "Cannot find reader for file " + name);
                return;
            }
            fileReader.accept(reader, this);
        } catch (IOException e) {
            logger.accept(1, "Error while reading file " + name + "\n" + e.getMessage());
        }
    }

    /**
     * Load all the needed information from the current ezlib loader class.
     */
    public void loadClass() {
        logger.accept(4, "Loading current EzlibLoader class");
        int count = 0;
        for (Class<?> clazz = getClass(); clazz != Object.class; clazz = clazz.getSuperclass()) {
            if (loadClass(clazz)) {
                count++;
            }
        }
        logger.accept(4, "Found " + count + " class" + (count == 1 ? "" : "es") + " with loadable objects");
    }

    /**
     * Load all the needed information from provided class.
     *
     * @param clazz the class to visit.
     * @return      true if any information was loaded.
     */
    public boolean loadClass(Class<?> clazz) {
        final boolean fields = loadClassFields(clazz);
        final boolean annotations = loadClassAnnotations(clazz);
        return fields || annotations;
    }

    /**
     * Load all the compatible objects from class static fields.
     *
     * @param clazz the class to visit.
     * @return      true if any object was loaded.
     */
    @SuppressWarnings("unchecked")
    public boolean loadClassFields(Class<?> clazz) {
        boolean result = false;
        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Object object;
            try {
                field.setAccessible(true);
                object = field.get(null);
            } catch (IllegalAccessException e) {
                continue;
            }
            final String type;
            if (object instanceof Predicate && hasParams(field.getGenericType(), String.class)) {
                conditions.put(field.getName().toLowerCase(), (Predicate<String>) object);
                type = "condition";
            } else if (object instanceof BiConsumer && hasParams(field.getGenericType(), Reader.class, EzlibLoader.class)) {
                fileReaders.put(field.getName().toLowerCase().split("_")[0], (BiConsumer<Reader, EzlibLoader>) object);
                type = "file reader";
            } else if (object instanceof BiConsumer && hasParams(field.getGenericType(), Integer.class, String.class)) {
                logger = (BiConsumer<Integer, String>) object;
                type = "logger";
            } else {
                type = loadObject(object);
            }
            if (type != null) {
                logger.accept(4, "Loaded " + type + " from field '" + field.getName() + "' at class '" + clazz.getName() + "'");
                result = true;
            }
        }
        return result;
    }

    /**
     * Load all the compatible objects from class annotations.<br>
     * By default, the ezlib annotations module is used, therefore it only works
     * if annotation classes was loaded into current class path.
     *
     * @param clazz the class to visit.
     * @return      true if any object was loaded.
     */
    public boolean loadClassAnnotations(Class<?> clazz) {
        if (!USE_ANNOTATIONS) {
            return false;
        }
        boolean repository = loadRepository(Repository.ofAnnotation(clazz.getAnnotation(com.saicone.ezlib.Repository.class)));
        boolean dependency = loadDependency(Dependency.ofAnnotation(clazz.getAnnotation(com.saicone.ezlib.Dependency.class)));
        boolean dependencies = Dependencies.ofAnnotation(clazz.getAnnotation(com.saicone.ezlib.Dependencies.class)).load(this);
        return repository || dependency || dependencies;
    }

    /**
     * Load any compatible object and return the text name of loaded object.
     *
     * @param object the object to load.
     * @return       a text name if the object is compatible, null otherwise.
     */
    public String loadObject(Object object) {
        if (object instanceof Dependencies) {
            ((Dependencies) object).load(this);
        } else if (object instanceof Repository) {
            loadRepository((Repository) object);
            return "repository";
        } else if (object instanceof Dependency) {
            loadDependency((Dependency) object);
            return "dependency";
        } else if (object instanceof String[]) {
            loadRelocations((String[]) object);
            return "relocations";
        }
        return null;
    }

    /**
     * Load provided repository into ezlib loader.
     *
     * @param repository the repository to load.
     * @return           true if the repository was loaded into memory.
     */
    public boolean loadRepository(Repository repository) {
        if (repository == null) {
            return false;
        }
        if (repository.url.toLowerCase().startsWith("http:") && !repository.allowInsecureProtocol) {
            logger.accept(1, "The repository " + repository.url + " uses an insecure protocol without explicit option to allow it, so will be ignored");
            repositories.remove(repository);
            return false;
        }
        if (!repository.isValid()) {
            return false;
        }
        if (!this.repositories.contains(repository)) {
            this.repositories.add(repository);
            return true;
        }
        return false;
    }

    /**
     * Load provided repositories into ezlib loader.
     *
     * @param repositories a collection of repositories, can be null.
     * @return             true if at least one repository was loaded into memory.
     */
    public boolean loadRepositories(Collection<Repository> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            return false;
        }
        int count = 0;
        for (Repository repository : repositories) {
            if (loadRepository(repository)) {
                count++;
            }
        }
        logger.accept(4, "Loaded " + count + "/" + repositories.size() + " repositor" + (count == 1 ? "y" : "ies"));
        return count > 0;
    }

    /**
     * Load provided repositories from pom document into ezlib loader.
     *
     * @param pom the pom document to read.
     * @return    true if at least one repository was loaded into memory.
     */
    public boolean loadRepositories(Document pom) {
        // Document path: repositories.repository.url
        Element element = pom.getDocumentElement();
        int count = 0;
        for (Element repository : xmlParser.getElements(element, "repository", "repositories")) {
            String url = xmlParser.getTextContent(element, repository, "url");
            if (url != null) {
                if (loadRepository(new Repository().url(url))) {
                    count++;
                }
            }
        }
        logger.accept(4, "Loaded " + count + " repositor" + (count == 1 ? "y" : "ies") + " from pom");
        return count > 0;
    }

    /**
     * Load provided dependency into ezlib loader.
     *
     * @param dependency the dependency to load.
     * @return           true if the dependency was loaded into memory.
     */
    public boolean loadDependency(Dependency dependency) {
        if (dependency == null) {
            return false;
        }
        dependency.path = parse(dependency.path);
        // Remove invalid repository
        if (dependency.repository != null && !dependency.repository.isValid()) {
            dependency.repository = null;
        }
        dependency.relocate = parse(dependency.relocate);
        if (dependency.relocate != null) {
            // Remove duplicated relocations
            for (Map.Entry<String, String> entry : this.relocations.entrySet()) {
                if (Objects.equals(entry.getValue(), dependency.relocate.get(entry.getKey()))) {
                    dependency.relocate.remove(entry.getKey());
                }
            }
            if (dependency.relocate.isEmpty()) {
                dependency.relocate = null;
            }
        }
        // Add repository to loaded repositories
        if (dependency.repository != null && dependency.repository.url != null) {
            loadRepository(dependency.repository);
        }
        if (!this.dependencies.contains(dependency)) {
            this.dependencies.add(dependency);
            return true;
        }
        return false;
    }

    /**
     * Load provided dependencies into ezlib loader.
     *
     * @param dependencies a collection of dependencies, can be null.
     * @return             true if at least one dependency was loaded into memory.
     */
    public boolean loadDependencies(Collection<Dependency> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return false;
        }
        int count = 0;
        for (Dependency dependency : dependencies) {
            if (loadDependency(dependency)) {
                count++;
            }
        }
        logger.accept(4, "Loaded " + count + "/" + dependencies.size() + " dependenc" + (count == 1 ? "y" : "ies"));
        return count > 0;
    }

    /**
     * Load ezlib annotations dependency into ezlib loader.
     *
     * @return true if the dependency was loaded into memory.
     */
    public boolean loadAnnotationsDependency() {
        return loadDependency(Dependency.annotations());
    }

    /**
     * Load provided relocations into ezlib loader.
     *
     * @param relocations a collection of relocations.
     * @return            true if at least one relocation was loaded into memory.
     */
    public boolean loadRelocations(String... relocations) {
        return loadRelocations(parseRelocations(relocations));
    }

    /**
     * Load provided relocations into ezlib loader.
     *
     * @param relocations a collection of relocations, can be null.
     * @return            true if at least one relocation was loaded into memory.
     */
    public boolean loadRelocations(Map<String, String> relocations) {
        if (relocations == null || relocations.isEmpty()) {
            return false;
        }
        this.relocations.putAll(parse(relocations));
        logger.accept(4, "Loaded " + relocations.size() + " relocation" + (relocations.size() == 1 ? "" : "s"));
        return true;
    }

    /**
     * Apply provided dependency into class loader.
     *
     * @param dependency the dependency to apply.
     * @return           true if dependency was applied correctly.
     */
    public boolean applyDependency(Dependency dependency) {
        if (applied.contains(dependency)) {
            logger.accept(4, "The dependency " + dependency.path + " is already applied into class loader");
            return true;
        }
        logger.accept(4, "Applying " + dependency);
        // Check if dependency will be loaded using test or using custom conditions
        if (dependency.meetTest(this) || !eval(dependency.condition)) {
            logger.accept(4, "The dependency doesn't need to be loaded");
            return false;
        }
        logger.accept(3, "Loading dependency " + dependency.path);

        // Create full relocation map using global and dependency relocations
        final Map<String, String> relocations = new HashMap<>(this.relocations);
        if (dependency.relocate != null) {
            relocations.putAll(dependency.relocate);
        }

        Repository repo = null;
        try {
            // Find repository url and format checking if dependency has repository url or name to get from global repositories
            repo = dependency.mainRepository(this);
            logger.accept(4, "Using repository " + repo);
            // Try to apply dependency using explicit repository
            if (applyDependency(dependency, repo, relocations)) {
                return true;
            }

            logger.accept(4, "Cannot find dependency from repository, so will be lookup over loaded repositories");
            // Avoid ConcurrentModificationException
            for (int i = 0; i < repositories.size(); i++) {
                final Repository repository = repositories.get(i);
                // Avoid repeated repo
                if (repository.equals(repo)) {
                    continue;
                }
                if (applyDependency(dependency, repository, relocations)) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (dependency.optional) {
            logger.accept(1, "Cannot load optional dependency " + dependency.path + " from " + repo + " or loaded repositories");
            return false;
        }
        throw new RuntimeException("Cannot load dependency " + dependency.path + " from " + repo + " or loaded repositories");
    }

    /**
     * Apply the provided dependency with defined repository and map of relocations.
     *
     * @param dependency  the dependency to apply.
     * @param repository  the repository to download the dependency.
     * @param relocations the relocation map to use.
     * @return            true if dependency was applied correctly.
     */
    public boolean applyDependency(Dependency dependency, Repository repository, Map<String, String> relocations) {
        final String[] path = dependency.path.split(":");
        // Check if dependency uses version path and get it from maven metadata
        Dependency modified = null;
        if (path[2].charAt(0) == '@' && parseVersionPath(path, repository.url)) {
            modified = new Dependency().path(String.join(":", path)).relocate(dependency.relocate);
        }
        // Check if dependency is snapshot to get file version from maven metadata
        final boolean lookSnapshot = dependency.snapshot;
        final boolean hasFileVersion = path[2].indexOf('@') > 0;
        if (lookSnapshot && !hasFileVersion) {
            if (parseSnapshot(path, repository.url)) {
                modified = new Dependency().path(String.join(":", path)).relocate(dependency.relocate);
            } else {
                logger.accept(2, "Dependency is marked has snapshot, but cannot find snapshot version from repository: " + repository);
            }
        }

        // Ignore modified dependency if it was applied before
        if (modified != null && applied.contains(modified)) {
            logger.accept(4, "The dependency " + modified.path + " is already applied into class loader");
            return true;
        }

        // Download dependency jar file
        File file;
        try {
            // Try to download or use pre-downloaded JAR
            file = ezlib.download(String.join(":", path), repository.url, repository.format.replace("%fileType%", "jar"));
        } catch (IOException e) {
            if (lookSnapshot || hasFileVersion) {
                logger.accept(4, "Cannot find dependency from " + repository.url);
                return false;
            }
            // Try to find snapshot if isn't configured previously
            if (parseSnapshot(path, repository.url)) {
                modified = new Dependency().path(String.join(":", path)).relocate(dependency.relocate);
                // Ignore modified dependency if it was applied before
                if (applied.contains(modified)) {
                    logger.accept(4, "The dependency " + modified.path + " is already applied into class loader");
                    return true;
                }
                try {
                    // If snapshot is found try to re-download
                    file = ezlib.download(String.join(":", path), repository.url, repository.format.replace("%fileType%", "jar"));
                } catch (IOException ex) {
                    logger.accept(4, "Cannot find dependency from " + repository);
                    return false;
                }
            } else {
                logger.accept(4, "Cannot find dependency from " + repository + " after looking for snapshot version");
                return false;
            }
        }

        // Append dependency to inner or parent class loader
        if (!ezlib.dependency(file).relocations(relocations).parent(!dependency.inner).load()) {
            throw new RuntimeException("Cannot load dependency " + dependency.path + " into class loader after download");
        }

        // Add to applied dependencies
        applied.add(dependency);
        if (modified != null) {
            // Add modified dependency too
            applied.add(modified);
        }

        // Check if sub dependencies will be downloaded
        if (!dependency.transitive) {
            return true;
        }

        logger.accept(4, "Finding out pom information to download sub-dependencies...");
        // Download dependency pom file or use the downloaded one to read information about the current dependency version
        File pom;
        try {
            pom = ezlib.download(String.join(":", path), repository.url, repository.format.replace("%fileType%", "pom"));
        } catch (IOException e) {
            logger.accept(4, "Cannot load pom file");
            // Return true because the dependency was loaded correctly
            return true;
        }
        // Download only sub dependencies (using optional comparator and exclusions)
        applyDependency(dependency, repository, xmlParser.fromFile(pom));
        return true;
    }

    /**
     * Apply the subdependencies from provided pom document using dependency information and defined repository.
     *
     * @param dependency the parent dependency.
     * @param repository the main repository to download sub dependencies.
     * @param pom        the pom document with sub dependencies.
     */
    public void applyDependency(Dependency dependency, Repository repository, Document pom) {
        if (pom == null) {
            logger.accept(4, "Cannot parse pom file");
            return;
        }

        logger.accept(4, "Applying sub-dependencies using pom file...");
        loadRepositories(pom);

        int count = 0;
        Element element = pom.getDocumentElement();
        // Document path: dependencies.dependency[]
        for (Element eDependency : xmlParser.getElements(element, "dependency", "dependencies")) {
            // Parse dependency path
            String path = parsePath(element, eDependency, false);
            if (path == null) {
                logger.accept(4, "The sub-dependency " + (count + 1) + " contains invalid parameters");
                continue;
            }
            // Avoid invalid scopes
            String scope = xmlParser.getTextContentOrDefault(element, eDependency, "compile", "scope");
            if (!dependency.isValidScope(scope)) {
                logger.accept(4, "The sub-dependency " + path + " scope '" + scope + "' doesn't match with dependency scopes, so will be ignored");
                continue;
            }
            // Avoid excluded or optional dependencies
            if (dependency.isExcluded(path) || (!dependency.loadOptional && xmlParser.getTextContentOrDefault(element, eDependency, "false", "optional").equals("true"))) {
                logger.accept(4, "The sub-dependency " + path + " don't need to be loaded");
                continue;
            }
            // Get exclusions from sub-dependency
            Set<String> exclusions = new HashSet<>();
            for (Element exclusion : xmlParser.getElements(eDependency, "exclusion", "exclusions")) {
                String excludedPath = parsePath(element, exclusion, true);
                if (excludedPath != null) {
                    exclusions.add(excludedPath);
                }
            }
            // Build sub-dependency with relocations
            Dependency dep = new Dependency().path(path).relocate(dependency.relocate);
            if (applied.contains(dep)) {
                logger.accept(4, "The sub-dependency " + path + " is already applied into class loader");
                continue;
            }
            if (dependencies.contains(dep)) {
                logger.accept(4, "The sub-dependency " + path + " is already defined in loaded dependencies");
                continue;
            }
            // Add inherited parameters
            dep.repository(repository).inner(dependency.inner).optional(dependency.optional).scopes(dependency.scopes).exclude(dependency.exclude);
            if (dep.exclude != null) {
                dep.exclude.addAll(exclusions);
            } else if (!exclusions.isEmpty()) {
                dep.exclude(exclusions);
            }
            logger.accept(4, "Trying to apply sub-dependency " + path + " from pom");
            if (applyDependency(dep)) {
                count++;
            }
        }
        logger.accept(4, "Applied " + count + " sub-dependenc" + (count == 1 ? "y" : "ies") + " from pom");
    }

    /**
     * Apply ezlib annotations dependency with current package relocations.
     *
     * @return true if dependency was applied correctly.
     */
    public boolean applyAnnotationsDependency() {
        return applyDependency(Dependency.annotations());
    }

    /**
     * Test if the provided class exists in class loader.
     *
     * @param name  class name.
     * @param inner true for inner class loader inside ezlib instance.
     * @return      true if the class exists.
     */
    public boolean test(String name, boolean inner) {
        final String s;
        final boolean bool;
        if (name.startsWith("!")) {
            s = name.substring(1);
            bool = false;
        } else {
            s = name;
            bool = true;
        }
        try {
            if (inner) {
                Class.forName(parse(s), true, ezlib.getPublicClassLoader());
            } else {
                Class.forName(parse(s));
            }
            return bool;
        } catch (ClassNotFoundException e) {
            return !bool;
        }
    }

    /**
     * Evaluate the provided custom conditions.
     *
     * @param conditions a collection with all the custom conditions.
     * @return           true the conditions doesn't exist or was evaluated as true.
     */
    public boolean eval(Collection<String> conditions) {
        if (conditions == null) {
            return true;
        }
        for (String condition : conditions) {
            final int index = condition.indexOf('=');
            final String key;
            final String value;
            if (index > 0 && index + 1 < condition.length()) {
                key = condition.substring(0, index).toLowerCase();
                value = condition.substring(index + 1);
            } else {
                key = condition.toLowerCase();
                value = "";
            }
            if (this.conditions.containsKey(key) && !this.conditions.get(key).test(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parse the provided String using loaded text replacements.
     *
     * @param s the String to parse.
     * @return  the parsed String.
     */
    public String parse(String s) {
        if (s == null) {
            return null;
        }
        String str = s;
        for (Map.Entry<String, String> entry : replaces.entrySet()) {
            str = str.replace(entry.getKey(), entry.getValue());
        }
        return str;
    }

    private Map<String, String> parse(Map<String, String> map) {
        if (map == null) {
            return null;
        }
        final Map<String, String> finalMap = new HashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            final String key = parse(entry.getKey());
            final String value = parse(entry.getValue());
            if (!key.equals(value)) {
                finalMap.put(key, value);
            }
        }
        return finalMap;
    }

    private boolean parseVersionPath(String[] path, String repository) {
        final String url = ezlib.parseRepository(repository) + path[0].replace(".", "/") + '/' + path[1] + "/maven-metadata.xml";
        final Document ver = xmlParser.fromUrl(url);
        if (ver == null) {
            return false;
        }
        final Element element = ver.getDocumentElement();
        final Element versioning = xmlParser.getElement(element, "versioning");
        if (versioning == null) {
            logger.accept(4, "The versioning node is null from " + url);
            return false;
        }
        final String[] split = path[2].substring(1).split("\\.");
        for (int i = 0; i < split.length; i++) {
            split[i] = split[i].replace("<dot>", ".");
        }
        final String text = xmlParser.getTextContent(element, versioning, split);
        if (text == null) {
            logger.accept(2, "Cannot find versioning content at path '" + path[2].substring(1) + "' from " + url);
            return false;
        }
        path[2] = text;
        return true;
    }
    
    private boolean parseSnapshot(String[] path, String repository) {
        final String url = ezlib.parseRepository(repository) + path[0].replace(".", "/") + '/' + path[1] + '/' + path[2] + "/maven-metadata.xml";
        final Document ver = xmlParser.fromUrl(url);
        if (ver == null) {
            return false;
        }
        final Element element = ver.getDocumentElement();
        final Element versioning = xmlParser.getElement(element, "versioning");
        if (versioning == null) {
            logger.accept(2, "The versioning node is null from " + url);
            return false;
        }
        // Try to get snapshot from "snapshotVersions.snapshotVersion.value" path
        String snapshot = xmlParser.getTextContent(element, versioning, "snapshotVersions", "snapshotVersion", "value");
        if (snapshot != null) {
            path[2] = path[2] + '@' + snapshot;
            return true;
        }
        // Try to parse snapshot version using snapshot information
        final String timestamp = xmlParser.getTextContent(element, versioning, "snapshot", "timestamp");
        final String buildNumber = xmlParser.getTextContent(element, versioning, "snapshot", "buildNumber");
        if (timestamp == null || buildNumber == null) {
            logger.accept(2, "Cannot get snapshot information from " + url + " after looking for snapshot versions");
            return false;
        }
        if (path[2].endsWith("-SNAPSHOT")) {
            snapshot = path[2].substring(0, path[2].length() - 9) + '-' + timestamp + '-' + buildNumber;
        } else {
            snapshot = path[2] + '-' + timestamp + '-' + buildNumber;
        }
        path[2] = path[2] + '@' + snapshot;
        return true;
    }

    private String parsePath(Element document, Element dependency, boolean acceptInvalid) {
        String groupId = xmlParser.getTextContent(document, dependency, "groupId");
        String artifactId = xmlParser.getTextContent(document, dependency, "artifactId");
        String version = xmlParser.getTextContent(document, dependency, "version");
        if (acceptInvalid) {
            if (isInvalid(groupId)) {
                return null;
            }
            if (isInvalid(artifactId)) {
                return groupId;
            } else {
                return groupId + ":" + artifactId + (isInvalid(version) ? "" : ":" + version);
            }
        }
        if (isInvalid(groupId) || isInvalid(artifactId) || isInvalid(version)) {
            return null;
        }
        return groupId + ':' + artifactId + ':' + version;
    }

    private static Map<String, String> parseRelocations(String... relocations) {
        final Map<String, String> map = new HashMap<>();
        if (relocations.length < 2) {
            return map;
        }
        try {
            for (int i = 0; i < relocations.length; i = i + 2) {
                map.put(relocations[i], relocations[i + 1]);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            new IllegalArgumentException("Invalid relocation: [" + String.join(", ", relocations) + "]").printStackTrace();
        }
        return map;
    }

    private static boolean hasParams(Type type, Class<?>... params) {
        if (type instanceof ParameterizedType) {
            final ParameterizedType paramType = (ParameterizedType) type;
            if (paramType.getActualTypeArguments().length == params.length) {
                for (int i = 0; i < params.length; i++) {
                    final Class<?> actualParam = (Class<?>) paramType.getActualTypeArguments()[i];
                    if (!params[i].isAssignableFrom(actualParam) || params[i] != actualParam) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isInvalid(String s) {
        return s == null || s.trim().isEmpty() || s.equals("null") || s.equals("*");
    }

    /**
     * The XML parser to handle documents.
     */
    public static class XmlParser {
        private final DocumentBuilder docBuilder;

        /**
         * Constructs a XML parser with default document builder.
         */
        public XmlParser() {
            this(defaultBuilder());
        }

        /**
         * Consturcts a XML parser with provided document builder.
         *
         * @param docBuilder the document builder to use.
         */
        public XmlParser(DocumentBuilder docBuilder) {
            this.docBuilder = docBuilder;
        }

        private static DocumentBuilder defaultBuilder() {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://xml.org/sax/features/validation", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                return factory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new RuntimeException("Cannot initialize document builder", e);
            }
        }

        /**
         * Get document from URL.
         *
         * @param url the URL to connect.
         * @return     a parsed document from URL or null.
         */
        public Document fromUrl(String url) {
            try {
                URLConnection con = new URL(url).openConnection();
                con.addRequestProperty("Accept", "application/xml");
                return docBuilder.parse(con.getInputStream());
            } catch (IOException | SAXException e) {
                return null;
            }
        }

        /**
         * Get document from file.
         *
         * @param file the file to parse.
         * @return     the provided file as document or null.
         */
        public Document fromFile(File file) {
            try {
                return docBuilder.parse(file.toURI().toURL().openStream());
            } catch (IOException | SAXException e) {
                return null;
            }
        }

        /**
         * Get node from element at provided path.
         *
         * @param element the element to see.
         * @param path    the key path.
         * @return        a node from provided path or null.
         */
        public Node getNode(Element element, String... path) {
            Element node = element;
            for (int i = 0; i < path.length; i++) {
                String key = path[i];
                // Try to get from child nodes
                NodeList list = node.getChildNodes();
                boolean found = false;
                for (int i1 = 0; i1 < list.getLength(); i1++) {
                    Node n = list.item(i1);
                    // Compare name with key
                    if (n.getNodeName().equals(key)) {
                        if (n instanceof Element) {
                            node = (Element) n;
                            found = true;
                            break;
                        }
                        if (i + 1 >= path.length) {
                            return n;
                        } else {
                            return null;
                        }
                    }
                }
                if (found) {
                    continue;
                }
                // Try to get from node list
                list = node.getElementsByTagName(key);
                if (list.getLength() > 0) {
                    Node n;
                    if (i + 1 < path.length) {
                        // Try to find index from next key
                        String nextKey = path[i + 1];
                        if (nextKey.equals("-1")) {
                            // Latest index
                            n = list.item(list.getLength() - 1);
                            i++;
                        } else {
                            try {
                                // Index number
                                n = list.item(Integer.parseInt(nextKey));
                                i++;
                            } catch (NumberFormatException e) {
                                n = list.item(0);
                            }
                        }
                    } else {
                        n = list.item(0);
                    }
                    if (n instanceof Element) {
                        node = (Element) n;
                        continue;
                    }
                    if (i + 1 >= path.length) {
                        return n;
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
            return node;
        }

        /**
         * Get element inside element at provided path.
         *
         * @param element the element to see.
         * @param path    the key path.
         * @return        a element from provided path or null.
         */
        public Element getElement(Element element, String... path) {
            final Node node = getNode(element, path);
            return node instanceof Element ? (Element) node : null;
        }

        /**
         * Get element list from provided element path by tag name.
         *
         * @param element the element to see.
         * @param tag     the tag name to match elements.
         * @param path    the key path.
         * @return        a list containing all the found nodes.
         */
        public List<Element> getElements(Element element, String tag, String... path) {
            final List<Element> elements = new ArrayList<>();
            Node node = getElement(element, path);
            if (node == null) {
                return elements;
            }
            NodeList list = ((Element) node).getElementsByTagName(tag);
            for (int i = 0; i < list.getLength(); i++) {
                node = list.item(i);
                if (node instanceof Element) {
                    elements.add((Element) node);
                }
            }
            return elements;
        }

        /**
         * Get text content from element path.
         *
         * @param document the full document if the text content have any node variable to parse.
         * @param element  the element to see.
         * @param path     the key path.
         * @return         a parsed string representing the found text from node at path or null.
         */
        public String getTextContent(Element document, Element element, String... path) {
            return getTextContentOrDefault(document, element, null, path);
        }

        /**
         * Get text content from element path or use default value if node don't exists.
         *
         * @param document the full document if the text content have any node variable to parse.
         * @param element  the element to see.
         * @param def      the default text.
         * @param path     the key path.
         * @return         a parsed string representing the found text from node at path or null.
         */
        public String getTextContentOrDefault(Element document, Element element, String def, String... path) {
            Node node = getNode(element, path);
            if (node == null) {
                return def;
            }
            String s = node.getTextContent();
            if (s == null) {
                return null;
            }
            final Matcher matcher = NODE_VARIABLE.matcher(s);
            while (matcher.find()) {
                String match = matcher.group(1);
                if (!match.startsWith("project.") && !match.startsWith("pom.")) {
                    // Try to find at properties path
                    final String result = getTextContent(document, document, "properties", match);
                    if (result != null) {
                        s = matcher.replaceFirst(result);
                        continue;
                    }
                } else {
                    // Remove unused prefix
                    match = match.substring(match.indexOf('.') + 1);
                }
                // Find at path
                s = matcher.replaceFirst(String.valueOf(getTextContent(document, document, match.split("\\."))));
            }
            return s;
        }
    }

    /**
     * Repository constructor to save information for downloads.
     */
    public static class Repository {
        private String name;
        private String url;
        private String format = "%group%/%artifact%/%version%/%artifact%-%fileVersion%.%fileType%";
        private boolean allowInsecureProtocol;

        /**
         * Convert repository annotation into {@link Repository}.<br>
         * Take in count this only work if annotation classes was loaded into current class path.
         *
         * @param annotation the repository annotation.
         * @return           a repository object represented by annotation, null otherwise.
         */
        public static Repository ofAnnotation(Object annotation) {
            if (!USE_ANNOTATIONS || !(annotation instanceof com.saicone.ezlib.Repository)) {
                return null;
            }
            final com.saicone.ezlib.Repository repo = (com.saicone.ezlib.Repository) annotation;
            return new Repository()
                    .name(repo.name())
                    .url(repo.url())
                    .format(repo.format())
                    .allowInsecureProtocol(repo.allowInsecureProtocol());
        }

        /**
         * Set the repostory unique name.
         *
         * @param name the repository name to compare with other repositories.
         * @return     the current repository object.
         */
        public Repository name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the repositoru url.
         *
         * @param url the url to download dependencies from it.
         * @return    the current repository object.
         */
        public Repository url(String url) {
            this.url = url;
            return this;
        }

        /**
         * Set the repository url format.
         *
         * @param format thr url format used to download dependencies.
         * @return       the current repository object.
         */
        public Repository format(String format) {
            this.format = format;
            return this;
        }

        /**
         * Change the insecure protocol handling.
         *
         * @param allowInsecureProtocol true to allow connections to insecure url protocol.
         * @return                      the current repository object.
         */
        public Repository allowInsecureProtocol(boolean allowInsecureProtocol) {
            this.allowInsecureProtocol = allowInsecureProtocol;
            return this;
        }

        private boolean isValid() {
            return (name != null && !name.isEmpty()) || (url != null && !url.isEmpty());
        }

        @Override
        public String toString() {
            return url;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Repository repo = (Repository) o;
            return name != null && repo.name != null ? name.equalsIgnoreCase(repo.name) : Objects.equals(url, repo.url);
        }

        @Override
        public int hashCode() {
            return name != null ? name.hashCode() : (url != null ? url.hashCode() : 0);
        }
    }

    /**
     * Dependency constructor to save information to apply.
     */
    public static class Dependency {
        private String path;
        private Repository repository;
        private boolean inner;
        private boolean transitive = true;
        private boolean snapshot;
        private boolean loadOptional;
        private boolean optional;
        private Set<String> scopes;
        private Set<String> test;
        private Set<String> condition;
        private Set<String> exclude;
        private Map<String, String> relocate;

        /**
         * Get ezlib annotations dependency compatible with current package relocations.
         *
         * @return a {@link Dependency} object that represent the ezlib annotations dependency.
         */
        public static Dependency annotations() {
            return new Dependency()
                    .path(Ezlib.GROUP + ":annotations:" + Ezlib.VERSION)
                    .repository("https://jitpack.io/")
                    .transitive(false)
                    .relocate(Ezlib.GROUP, "com.saicone.ezlib");
        }

        /**
         * Convert dependency annotation into {@link Dependency}.<br>
         * Take in count this only work if annotation classes was loaded into current class path.
         *
         * @param annotation the dependency annotation.
         * @return           a {@link Dependency} object represented by annotation, null otherwise.
         */
        public static Dependency ofAnnotation(Object annotation) {
            if (!USE_ANNOTATIONS || !(annotation instanceof com.saicone.ezlib.Dependency)) {
                return null;
            }
            final com.saicone.ezlib.Dependency dep = (com.saicone.ezlib.Dependency) annotation;
            return new Dependency()
                    .path(dep.value())
                    .repository(Repository.ofAnnotation(dep.repository()))
                    .inner(dep.inner())
                    .transitive(dep.transitive())
                    .snapshot(dep.snapshot())
                    .loadOptional(dep.loadOptional())
                    .optional(dep.optional())
                    .scopes(dep.scopes())
                    .test(dep.test())
                    .condition(dep.condition())
                    .exclude(dep.exclude())
                    .relocate(dep.relocate());
        }

        /**
         * Set the dependency path.
         *
         * @param path the dependency gradle-like path.
         * @return     the current dependency object.
         */
        public Dependency path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Set the dependency main repository url.
         *
         * @param repository the repository url.
         * @return           the current dependency object.
         */
        public Dependency repository(String repository) {
            return repository(new Repository().url(repository));
        }

        /**
         * Set the dependency main repository.
         *
         * @param repository the repository object.
         * @return           the current dependency object.
         */
        public Dependency repository(Repository repository) {
            this.repository = repository;
            return this;
        }

        /**
         * Change the inner status.
         *
         * @param inner true to load dependency into child class loader.
         * @return      the current dependency object.
         */
        public Dependency inner(boolean inner) {
            this.inner = inner;
            return this;
        }

        /**
         * Change the transitive state.
         *
         * @param transitive true to download sub dependencies.
         * @return           the current dependency object.
         */
        public Dependency transitive(boolean transitive) {
            this.transitive = transitive;
            return this;
        }

        /**
         * Change the snapshot state.
         *
         * @param snapshot true to threat dependency version as snapshot.
         * @return         the current dependency object.
         */
        public Dependency snapshot(boolean snapshot) {
            this.snapshot = snapshot;
            return this;
        }

        /**
         * Change to load optional strategy.
         *
         * @param loadOptional true to load optional sub dependencies.
         * @return             the current dependency object.
         */
        public Dependency loadOptional(boolean loadOptional) {
            this.loadOptional = loadOptional;
            return this;
        }

        /**
         * Change the optional state.
         *
         * @param optional true to make dependency optional.
         * @return         the current dependency object.
         */
        public Dependency optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        /**
         * Set the used scopes to download sub dependencies.
         *
         * @param scopes an array of scopes names.
         * @return       the current dependency object.
         */
        public Dependency scopes(String... scopes) {
            return scopes(Arrays.stream(scopes).collect(Collectors.toSet()));
        }

        /**
         * Set the used scopes to download sub dependencies.
         *
         * @param scopes a set of scopes names.
         * @return       the current dependency object.
         */
        public Dependency scopes(Set<String> scopes) {
            this.scopes = scopes;
            return this;
        }

        /**
         * Set the classes test.
         *
         * @param test an array of class name test.
         * @return     the current dependency object.
         */
        public Dependency test(String... test) {
            return test(Arrays.stream(test).collect(Collectors.toSet()));
        }

        /**
         * Set the classes test.
         *
         * @param test a set of class name test.
         * @return     the current dependency object.
         */
        public Dependency test(Set<String> test) {
            this.test = test;
            return this;
        }

        /**
         * Set the custom conditions.
         *
         * @param condition conditions array.
         * @return          the current dependency object.
         */
        public Dependency condition(String[] condition) {
            return condition(Arrays.stream(condition).collect(Collectors.toSet()));
        }

        /**
         * Set the custom conditions.
         *
         * @param condition conditions set.
         * @return          the current dependency object.
         */
        public Dependency condition(Set<String> condition) {
            this.condition = condition;
            return this;
        }

        /**
         * Set the exclusions.
         *
         * @param exclude dependency paths exclusions.
         * @return        the current dependency object.
         */
        public Dependency exclude(String... exclude) {
            return exclude(Arrays.stream(exclude).collect(Collectors.toSet()));
        }

        /**
         * Set the exclusions.
         *
         * @param exclude dependency paths exclusions.
         * @return        the current dependency object.
         */
        public Dependency exclude(Set<String> exclude) {
            this.exclude = exclude;
            return this;
        }

        /**
         * Set the relocations.
         *
         * @param relocate package relocations.
         * @return         the current dependency object.
         */
        public Dependency relocate(String... relocate) {
            return relocate(parseRelocations(relocate));
        }

        /**
         * Set the relocations.
         *
         * @param relocate package relocations.
         * @return         the current dependency object.
         */
        public Dependency relocate(Map<String, String> relocate) {
            this.relocate = relocate;
            return this;
        }

        private Repository mainRepository(EzlibLoader loader) {
            // Find any valid repository
            if (repository != null) {
                // Using url
                if (repository.url != null && !repository.url.isEmpty()) {
                    return repository;
                }
                // Using name
                if (repository.name != null && !repository.name.isEmpty()) {
                    for (Repository repo : loader.repositories) {
                        if (repository.name.equalsIgnoreCase(repo.name)) {
                            return repo;
                        }
                    }
                }
            }
            // Return first repository
            for (Repository repo : loader.repositories) {
                return repo;
            }
            // Return default repository
            return new Repository().url(loader.ezlib.getDefaultRepository());
        }

        private boolean meetTest(EzlibLoader loader) {
            if (test == null || test.isEmpty()) {
                return false;
            }
            for (String name : test) {
                if (!loader.test(name, inner)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isValidScope(String name) {
            if (name == null) {
                return false;
            }
            if (scopes == null) {
                return name.equalsIgnoreCase("runtime") || name.equalsIgnoreCase("compile");
            }
            return scopes.contains(name.toLowerCase());
        }

        private boolean isExcluded(String path) {
            if (exclude != null) {
                for (String s : exclude) {
                    if (path.startsWith(s)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return "Dependency{" +
                    "path='" + path + '\'' +
                    ", repository=" + repository +
                    ", inner=" + inner +
                    ", transitive=" + transitive +
                    ", snapshot=" + snapshot +
                    ", loadOptional=" + loadOptional +
                    ", optional=" + optional +
                    ", scopes=" + scopes +
                    ", test=" + test +
                    ", condition=" + condition +
                    ", exclude=" + exclude +
                    ", relocate=" + relocate +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Dependency that = (Dependency) o;

            if (relocate == null) {
                return Objects.equals(path, that.path) && that.relocate == null;
            }
            return Objects.equals(path, that.path) && (that.relocate == null ? relocate.isEmpty() : relocate.equals(that.relocate));
        }

        @Override
        public int hashCode() {
            int result = path != null ? path.hashCode() : 0;
            result = 31 * result + (relocate != null && !relocate.isEmpty() ? relocate.hashCode() : 0);
            return result;
        }
    }

    /**
     * Dependencies constructor to load information into ezlib loader.
     */
    public static class Dependencies {
        private List<Repository> repositories;
        private List<Dependency> dependencies;
        private Map<String, String> relocations;

        /**
         * Convert dependencies annotation into {@link Dependencies}.<br>
         * Take in count this only work if annotation classes was loaded into current class path.
         *
         * @param annotation the dependencies annotation.
         * @return           a dependencies object represented by annotation, null otherwise.
         */
        public static Dependencies ofAnnotation(Object annotation) {
            if (!USE_ANNOTATIONS || !(annotation instanceof com.saicone.ezlib.Dependencies)) {
                return new Dependencies();
            }
            final com.saicone.ezlib.Dependencies deps = (com.saicone.ezlib.Dependencies) annotation;
            return new Dependencies()
                    .repositories(Arrays.stream(deps.repositories()).map(Repository::ofAnnotation).collect(Collectors.toList()))
                    .dependencies(Arrays.stream(deps.value()).map(Dependency::ofAnnotation).collect(Collectors.toList()))
                    .relocations(parseRelocations(deps.relocations()));
        }

        /**
         * Set the repositories.
         *
         * @param repositories array of repositories.
         * @return             the current dependencies object.
         */
        public Dependencies repositories(Repository... repositories) {
            return repositories(Arrays.asList(repositories));
        }

        /**
         * Set the repositories.
         *
         * @param repositories list of repositories.
         * @return             the current dependencies object.
         */
        public Dependencies repositories(List<Repository> repositories) {
            this.repositories = repositories;
            return this;
        }

        /**
         * Set the dependencies.
         *
         * @param dependencies array of dependencies.
         * @return             the current dependencies object.
         */
        public Dependencies dependencies(Dependency... dependencies) {
            return dependencies(Arrays.asList(dependencies));
        }

        /**
         * Set the dependencies.
         *
         * @param dependencies list of dependencies.
         * @return             the current dependencies object.
         */
        public Dependencies dependencies(List<Dependency> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        /**
         * Set the global relocations.
         *
         * @param relocations package relocations.
         * @return            the current dependencies object.
         */
        public Dependencies relocations(String... relocations) {
            return relocations(parseRelocations(relocations));
        }

        /**
         * Set the global relocations.
         *
         * @param relocations a package relocations.
         * @return            the current dependencies object.
         */
        public Dependencies relocations(Map<String, String> relocations) {
            this.relocations = relocations;
            return this;
        }

        /**
         * Load all the loaded information into provided ezlib loader.
         *
         * @param loader the ezlib loader to load information inside.
         * @return       true if at least one object was loaded into provided ezlib loader.
         */
        public boolean load(EzlibLoader loader) {
            loader.logger.accept(4, "Loading Dependencies into EzlibLoader");
            boolean relocation = loader.loadRelocations(relocations);
            boolean repository = loader.loadRepositories(repositories);
            boolean dependency = loader.loadDependencies(dependencies);
            return relocation || repository || dependency;
        }
    }
}
