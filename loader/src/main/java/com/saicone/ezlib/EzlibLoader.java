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

/**
 * EzlibLoader class to load &amp; apply all the needed dependencies from files
 * into class path using {@link Ezlib}.
 *
 * @author Rubenicos
 */
public class EzlibLoader {

    // Loader parameters
    private final ClassLoader classLoader;
    private final File folder;
    private final String[] files;
    // Explicit initialization parameters
    private final Ezlib ezlib;
    private DocumentBuilder docBuilder;

    // Loadable objects
    private final Set<Repository> repositories = new HashSet<>();
    private final Set<Dependency> dependencies = new HashSet<>();
    private final Map<String, String> relocations = new HashMap<>();
    private final Map<String, Predicate<String>> conditions = new HashMap<>();

    // Loader options
    private BiConsumer<Integer, String> logger = (level, text) -> {};
    private final Map<String, String> replaces = new HashMap<>();
    private final Map<String, BiConsumer<Reader, EzlibLoader>> fileReaders = new HashMap<>();

    /**
     * Constructs an EzlibLoader using default configuration.
     */
    public EzlibLoader() {
        this("ezlib-dependencies.json");
    }

    /**
     * Constructs an EzlibLoader with defined files to load.
     *
     * @param files an array of file names.
     */
    public EzlibLoader(String... files) {
        this(EzlibLoader.class.getClassLoader(), null, files);
    }

    /**
     * Constructs an EzlibLoader with defined folder and files to load.
     *
     * @param folder folder to save downloaded dependencies files.
     * @param files  an array of file names.
     */
    public EzlibLoader(File folder, String... files) {
        this(EzlibLoader.class.getClassLoader(), folder, files);
    }

    /**
     * Constructs an EzlibLoader with defined class loader and files to load.
     *
     * @param classLoader the class loader to append files.
     * @param files       an array of file names.
     */
    public EzlibLoader(ClassLoader classLoader, String... files) {
        this(classLoader, null, files);
    }

    /**
     * Constructs an EzlibLoader with defined class loader, folder and files to load.
     *
     * @param classLoader the class loader to append files.
     * @param folder      folder to save downloaded dependencies files.
     * @param files       an array of file names.
     */
    public EzlibLoader(ClassLoader classLoader, File folder, String... files) {
        this.classLoader = classLoader;
        this.folder = folder;
        this.files = files.length < 1 ? new String[] {"ezlib-dependencies.json"} : files;
        this.ezlib = new Ezlib(folder);
        ezlib.setParentClassLoader(classLoader);
        repositories.add(new Repository().name("MavenCentral").url("https://repo.maven.apache.org/maven2/"));
        repositories.add(new Repository().name("Jitpack").url("https://jitpack.io/"));
        replaces.put("{}", ".");
        replaces.put("{package}", EzlibLoader.class.getPackage().getName());
        fileReaders.put("json", (reader, loader) -> new Gson().fromJson(reader, Dependencies.class).load(loader));
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
        conditions.put(key, predicate);
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
                final File file = new File(ezlib.getFolder(), name);
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
        if (docBuilder == null) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://xml.org/sax/features/validation", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                docBuilder = factory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new RuntimeException("Cannot initialize document builder", e);
            }
        }
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
        for (Dependency dependency : dependencies) {
            applyDependency(dependency);
        }
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
        final int index = name.lastIndexOf('.') + 1;
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
        try (Reader reader = getReader(name)) {
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
    @SuppressWarnings("unchecked")
    public void loadClass() {
        logger.accept(4, "Loading current EzlibLoader class");
        int count = 0;
        for (Class<?> clazz = getClass(); clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object object;
                try {
                    object = field.get(null);
                } catch (IllegalAccessException e) {
                    continue;
                }
                String type = null;
                if (object instanceof Dependencies) {
                    ((Dependencies) object).load(this);
                } else if (object instanceof Repository) {
                    repositories.add((Repository) object);
                    type = "repository";
                } else if (object instanceof Dependency) {
                    dependencies.add((Dependency) object);
                    type = "dependency";
                } else if (object instanceof String[]) {
                    loadRelocations((String[]) object);
                    type = "relocations";
                } else if (object instanceof Predicate && hasParams(field.getGenericType(), String.class)) {
                    conditions.put(field.getName().toLowerCase(), (Predicate<String>) object);
                    type = "condition";
                } else if (object instanceof BiConsumer) {
                    if (hasParams(field.getGenericType(), Integer.class, String.class)) {
                        logger = (BiConsumer<Integer, String>) object;
                        type = "logger";
                    } else if (hasParams(field.getGenericType(), Reader.class, EzlibLoader.class)) {
                        fileReaders.put(field.getName().toLowerCase().split("_")[0], (BiConsumer<Reader, EzlibLoader>) object);
                        type = "file reader";
                    }
                }
                if (type != null) {
                    count++;
                    logger.accept(4, "Loaded " + type + " from field '" + field.getName() + "' at class '" + clazz.getName() + "'");
                }
            }
        }
        logger.accept(4, "Found " + count + " compatible field" + (count == 1 ? "" : "s"));
    }

    /**
     * Load provided repositories into ezlib loader.
     *
     * @param repositories a collection of repositories, can be null.
     */
    public void loadRepositories(Collection<Repository> repositories) {
        if (repositories != null) {
            this.repositories.addAll(repositories);
            logger.accept(4, "Loaded " + repositories.size() + " repositor" + (repositories.size() == 1 ? "y" : "ies"));
        }
    }

    /**
     * Load provided repositories from pom document into ezlib loader.
     *
     * @param pom the pom document to read.
     */
    public void loadRepositories(Document pom) {
        // Document path: repositories.repository.url
        NodeList list = pom.getDocumentElement().getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeName().equals("repositories")) {
                list = ((Element) node).getElementsByTagName("repository");
                for (i = 0; i < list.getLength(); i++) {
                    node = list.item(i);
                    String url = elementText((Element) node, "url");
                    if (url != null) {
                        repositories.add(new Repository().url(url));
                        logger.accept(4, "Loaded repository from pom: " + url);
                    }
                }
                break;
            }
        }
    }

    /**
     * Load provided dependencies into ezlib loader.
     *
     * @param dependencies a collection of dependencies, can be null.
     */
    public void loadDependencies(Collection<Dependency> dependencies) {
        if (dependencies != null) {
            this.dependencies.addAll(dependencies);
            logger.accept(4, "Loaded " + dependencies.size() + " dependenc" + (dependencies.size() == 1 ? "y" : "ies"));
        }
    }

    /**
     * Load provided relocations into ezlib loader.
     *
     * @param relocations a collection of relocations.
     */
    public void loadRelocations(String... relocations) {
        loadRelocations(parseRelocations(relocations));
    }

    /**
     * Load provided relocations into ezlib loader.
     *
     * @param relocations a collection of relocations, can be null.
     */
    public void loadRelocations(Map<String, String> relocations) {
        if (relocations != null) {
            for (Map.Entry<String, String> entry : relocations.entrySet()) {
                this.relocations.put(parse(entry.getKey()), parse(entry.getValue()));
            }
            logger.accept(4, "Loaded " + relocations.size() + " relocation" + (relocations.size() == 1 ? "" : "s"));
        }
    }

    /**
     * Apply provided dependency into class loader.
     *
     * @param dependency the dependency to apply.
     */
    public void applyDependency(Dependency dependency) {
        logger.accept(4, "Applying " + dependency);
        // Check if dependency will be loaded using test or using custom conditions
        if (test(dependency) || !eval(dependency.condition)) {
            logger.accept(4, "The dependency doesn't need to be loaded");
            return;
        }
        dependency.path = parse(dependency.path);
        logger.accept(3, "Loading dependency " + dependency.path);

        // Create full relocation map using global and dependency relocations
        final Map<String, String> relocations = new HashMap<>(this.relocations);
        if (dependency.relocate != null) {
            for (Map.Entry<String, String> entry : dependency.relocate.entrySet()) {
                relocations.put(parse(entry.getKey()), parse(entry.getValue()));
            }
        }

        Repository repo = null;
        boolean meet = false;
        try {
            // Find repository url and format checking if dependency has repository url or name to get from global repositories
            repo = parseRepository(dependency);
            logger.accept(4, "Using repository " + repo);
            // Try to apply dependency using explicit repository
            if (applyDependency(dependency, repo, relocations)) {
                return;
            }

            logger.accept(4, "Cannot find dependency from repository, so will be lookup over loaded repositories");
            // Use iterator to avoid ConcurrentModificationException
            final Iterator<Repository> iterator = repositories.iterator();
            while (iterator.hasNext()) {
                final Repository repository = iterator.next();
                // Avoid repeated repo
                if (repository.equals(repo)) {
                    continue;
                }
                if (applyDependency(dependency, repository, relocations)) {
                    meet = true;
                    break;
                }
            }
        } catch (RuntimeException ignored) { }

        if (!meet && !dependency.optional) {
            // Throw exception if dependency is needed
            throw new RuntimeException("Cannot load dependency " + dependency.path + " from " + repo + " or loaded repositories");
        } else {
            logger.accept(1, "Cannot load optional dependency " + dependency.path + " from " + repo + " or loaded repositories");
        }
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
        if (repository.url.toLowerCase().startsWith("http:") && !repository.allowInsecureProtocol) {
            logger.accept(1, "The repository " + repository.url + " uses an insecure protocol without explicit option to allow it, so will be removed");
            repositories.remove(repository);
            return false;
        }
        final String[] path = dependency.path.split(":");
        // Check if dependency is snapshot to get file version from maven metadata
        if (dependency.snapshot && !parseSnapshot(path, repository.url)) {
            logger.accept(2, "Dependency is marked has snapshot, but cannot find snapshot version from repository");
        }

        // Download dependency jar file
        File file;
        try {
            // Try to download or use downloaded JAR
            file = ezlib.download(String.join(":", path), repository.url, repository.format.replace("%fileType%", "jar"));
        } catch (IOException e) {
            if (dependency.snapshot) {
                logger.accept(4, "Cannot find dependency from " + repository.url);
                return false;
            }
            // Try to find snapshot if isn't configured previously
            if (parseSnapshot(path, repository.url)) {
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
        applyDependency(dependency, repository, parseDocument(pom));
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

        // Document path: dependencies.dependency[]
        NodeList list = pom.getDocumentElement().getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            // dependencies
            if (node.getNodeName().equals("dependencies")) {
                list = ((Element) node).getElementsByTagName("dependency");
                for (i = 0; i < list.getLength(); i++) {
                    node = list.item(i);
                    String path = elementText((Element) node, "groupId") + ':' + elementText((Element) node, "artifactId") + ':' + elementText((Element) node, "version");
                    if (dependency.isExcluded(path) || (!dependency.loadOptional && elementText((Element) node, "optional", "false").equals("true"))) {
                        logger.accept(4, "The sub-dependency " + path + " don't need to be loaded");
                        continue;
                    }
                    Dependency dep = new Dependency().path(path).repository(repository).relocate(dependency.relocate);
                    if (dependencies.contains(dep)) {
                        logger.accept(4, "The sub-dependency " + path + " is already loaded");
                        continue;
                    }
                    logger.accept(4, "Trying to apply sub-dependency " + path + " from pom");
                    applyDependency(dep);
                }
                break;
            }
        }
    }

    /**
     * Check if the provided dependency need to be applied testing classes.
     *
     * @param dependency the dependency to check.
     * @return           true if dependency passed the check.
     */
    public boolean test(Dependency dependency) {
        if (dependency.test == null || dependency.test.length < 1) {
            return false;
        }
        boolean meet = true;
        for (String name : dependency.test) {
            if (!test(name, dependency.inner)) {
                meet = false;
                break;
            }
        }
        return meet;
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
                Class.forName(s, true, ezlib.getPublicClassLoader());
            } else {
                Class.forName(s);
            }
            return bool;
        } catch (ClassNotFoundException e) {
            return !bool;
        }
    }

    /**
     * Evaluate the provided custom conditions.
     *
     * @param conditions an array with all the custom conditions.
     * @return           true the conditions doesn't exist or was evaluated as true.
     */
    public boolean eval(String... conditions) {
        if (conditions == null) {
            return true;
        }
        for (String condition : conditions) {
            final int index = condition.indexOf('=');
            final String key;
            final String value;
            if (index > 0 && index + 1 < condition.length()) {
                key = condition.substring(0, index);
                value = condition.substring(index + 1);
            } else {
                key = condition;
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
        String str = s;
        for (Map.Entry<String, String> entry : replaces.entrySet()) {
            str = str.replace(entry.getKey(), entry.getValue());
        }
        return str;
    }

    private Document parseDocument(String url) {
        try {
            URLConnection con = new URL(url).openConnection();
            con.addRequestProperty("Accept", "application/xml");
            return docBuilder.parse(con.getInputStream());
        } catch (IOException | SAXException e) {
            return null;
        }
    }

    private Document parseDocument(File file) {
        try {
            return docBuilder.parse(file.toURI().toURL().openStream());
        } catch (IOException | SAXException e) {
            return null;
        }
    }
    
    private boolean parseSnapshot(String[] path, String repository) {
        Document ver = parseDocument(ezlib.parseRepository(repository) + path[0] + '/' + path[1] + '/' + path[2] + "/maven-metadata.xml");
        if (ver == null) {
            return false;
        }
        String snapshot = elementText(ver.getDocumentElement(), "versioning.snapshotVersions.snapshotVersion.value");
        if (snapshot == null) {
            return false;
        }
        path[2] = path[2] + '@' + snapshot;
        return true;
    }

    private Repository parseRepository(Dependency dependency) {
        if (dependency.repository != null) {
            if (dependency.repository.url != null && !dependency.repository.url.isEmpty()) {
                return dependency.repository;
            }
            if (dependency.repository.name != null && !dependency.repository.name.isEmpty()) {
                for (Repository repository : repositories) {
                    if (repository.equals(dependency.repository)) {
                        return repository;
                    }
                }
            }
        }
        for (Repository repository : repositories) {
            return repository;
        }
        return new Repository().url("https://repo.maven.apache.org/maven2/");
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
                    if (!params[i].isAssignableFrom((Class<?>) paramType.getActualTypeArguments()[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static String elementText(Element element, String path) {
        return elementText(element, path, null);
    }

    private static String elementText(Element element, String path, String def) {
        final String[] keys = path.split("\\.");
        Element node = element;
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            NodeList list = node.getChildNodes();
            boolean found = false;
            for (int i1 = 0; i1 < list.getLength(); i1++) {
                Node n = list.item(i1);
                if (n.getNodeName().equals(key)) {
                    if (n instanceof Element) {
                        node = (Element) n;
                        found = true;
                        break;
                    }
                    if (i + 1 >= keys.length) {
                        return n.getTextContent();
                    } else {
                        return def;
                    }
                }
            }
            if (found) {
                continue;
            }
            list = node.getElementsByTagName(key);
            if (list.getLength() > 0) {
                Node n = list.item(0);
                if (n instanceof Element) {
                    node = (Element) n;
                    continue;
                }
                if (i + 1 >= keys.length) {
                    return n.getTextContent();
                } else {
                    return def;
                }
            } else {
                return def;
            }
        }
        return node.getTextContent();
    }

    /**
     * Repository constructor to save information for downloads.
     */
    public static class Repository {
        private String name;
        private String url;
        private String format = "%group%/%artifact%/%version%/%artifact%-%fileVersion%.%fileType%";
        private boolean allowInsecureProtocol;

        public Repository name(String name) {
            this.name = name;
            return this;
        }

        public Repository url(String url) {
            this.url = url;
            return this;
        }

        public Repository format(String format) {
            this.format = format;
            return this;
        }

        public Repository allowInsecureProtocol(boolean allowInsecureProtocol) {
            this.allowInsecureProtocol = allowInsecureProtocol;
            return this;
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
            return name != null ? name.equals(repo.name) : Objects.equals(url, repo.url);
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
        private String[] test;
        private String[] condition;
        private String[] exclude;
        private Map<String, String> relocate;

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
         * Set the dependency main repository main.
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
         * Set the classes test.
         *
         * @param test an array of class name test.
         * @return     the current dependency object.
         */
        public Dependency test(String... test) {
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

        /**
         * Check if the provided dependency path is excluded in this dependency information.
         *
         * @param path the dependency path to check.
         * @return     true if the path is excluded.
         */
        public boolean isExcluded(String path) {
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
                    ", test=" + Arrays.toString(test) +
                    ", condition=" + Arrays.toString(condition) +
                    ", exclude=" + Arrays.toString(exclude) +
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
    }

    /**
     * Dependencies constructor to load information into ezlib loader.
     */
    public static class Dependencies {
        private List<Repository> repositories;
        private List<Dependency> dependencies;
        private Map<String, String> relocations;

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
         */
        public void load(EzlibLoader loader) {
            loader.logger.accept(4, "Loading Dependencies into EzlibLoader");
            loader.loadRepositories(repositories);
            loader.loadDependencies(dependencies);
            loader.loadRelocations(relocations);
        }
    }
}
