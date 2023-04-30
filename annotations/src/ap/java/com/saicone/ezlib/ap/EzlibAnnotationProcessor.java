package com.saicone.ezlib.ap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.saicone.ezlib.Dependencies;
import com.saicone.ezlib.Dependency;
import com.saicone.ezlib.Repository;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Annotation processor for ezlib annotations.
 *
 * @author Rubenicos
 */
@SupportedAnnotationTypes({"com.saicone.ezlib.Repository", "com.saicone.ezlib.Dependency", "com.saicone.ezlib.Dependencies"})
public class EzlibAnnotationProcessor extends AbstractProcessor {

    private ProcessingEnvironment environment;
    private Gson gson;
    private Map<String, SerializedFile> files;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        this.environment = processingEnv;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    public Gson getGson() {
        if (gson == null) {
            gson = new GsonBuilder().setPrettyPrinting().create();
        }
        return gson;
    }

    public Map<String, SerializedFile> getFiles() {
        if (files == null) {
            files = new HashMap<>();
        }
        return files;
    }

    public SerializedFile getFile(String name) {
        if (!getFiles().containsKey(name)) {
            getFiles().put(name, new SerializedFile());
        }
        return getFiles().get(name);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(Dependencies.class)) {
            serialize(element.getAnnotation(Dependencies.class));
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(Repository.class)) {
            save(element.getAnnotation(Repository.class), null);
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(Dependency.class)) {
            save(element.getAnnotation(Dependency.class), null);
        }

        Gson gson = getGson();
        for (Map.Entry<String, SerializedFile> entry : getFiles().entrySet()) {
            if (entry.getKey().trim().isEmpty() || entry.getValue().isEmpty()) {
                continue;
            }
            entry.getValue().clearEmpty();
            try {
                final FileObject fileObject = environment.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", entry.getKey());
                try (BufferedWriter writer = new BufferedWriter(fileObject.openWriter())) {
                    gson.toJson(entry.getValue(), writer);
                }
            } catch (IOException e) {
                environment.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to generate dependencies file at " + entry.getKey() + "\nReason:" + e.getMessage());
            }
        }
        return false;
    }

    private void save(Repository repository, SerializedFile file) {
        Map<String, Object> map = serialize(repository);
        if (!map.isEmpty()) {
            (file == null ? getFile(repository.file()) : file).repositories.add(map);
        }
    }

    private void save(Dependency dependency, SerializedFile file) {
        Map<String, Object> map = serialize(dependency);
        if (!map.isEmpty()) {
            (file == null ? getFile(dependency.file()) : file).dependencies.add(map);
        }
    }

    private void serialize(Dependencies dependencies) {
        final SerializedFile file = getFile(dependencies.file());
        for (Repository repository : dependencies.repositories()) {
            save(repository, file);
        }
        for (Dependency dependency : dependencies.value()) {
            save(dependency, file);
        }
        file.relocations.putAll(parseRelocations(dependencies.relocations()));
    }

    private Map<String, Object> serialize(Repository repository) {
        Map<String, Object> map = new HashMap<>();
        if (!repository.name().isEmpty()) {
            map.put("name", repository.name());
        }
        if (!repository.url().isEmpty()) {
            map.put("url", repository.url());
        }
        if (map.isEmpty()) {
            return map;
        }
        if (!repository.format().equals("%group%/%artifact%/%version%/%artifact%-%fileVersion%.%fileType%")) {
            map.put("format", repository.format());
        }
        if (repository.allowInsecureProtocol()) {
            map.put("allowInsecureProtocol", true);
        }
        return map;
    }

    private Map<String, Object> serialize(Dependency dependency) {
        Map<String, Object> map = new HashMap<>();
        if (dependency.value().isEmpty()) {
            return map;
        }
        map.put("path", dependency.value());
        Map<String, Object> repo = serialize(dependency.repository());
        if (!repo.isEmpty()) {
            map.put("repository", repo);
        }
        if (dependency.inner()) {
            map.put("inner", true);
        }
        if (!dependency.transitive()) {
            map.put("transitive", false);
        }
        if (dependency.snapshot()) {
            map.put("snapshot", true);
        }
        if (dependency.loadOptional()) {
            map.put("loadOptional", true);
        }
        if (dependency.optional()) {
            map.put("optional", true);
        }
        if (dependency.test().length > 0) {
            map.put("test", dependency.test());
        }
        if (dependency.condition().length > 0) {
            map.put("condition", dependency.condition());
        }
        if (dependency.exclude().length > 0) {
            map.put("exclude", dependency.exclude());
        }
        if (dependency.relocate().length > 0) {
            map.put("relocate", parseRelocations(dependency.relocate()));
        }
        return map;
    }

    private Map<String, String> parseRelocations(String... relocations) {
        final Map<String, String> map = new HashMap<>();
        if (relocations.length < 2) {
            return map;
        }
        try {
            for (int i = 0; i < relocations.length; i = i + 2) {
                map.put(relocations[i], relocations[i + 1]);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            environment.getMessager().printMessage(Diagnostic.Kind.WARNING, "Invalid relocation: [" + String.join(", ", relocations) + "]");
        }
        return map;
    }

    private static class SerializedFile {
        private Set<Map<String, Object>> repositories = new HashSet<>();
        private Set<Map<String, Object>> dependencies = new HashSet<>();
        private Map<String, String> relocations = new HashMap<>();

        private boolean isEmpty() {
            return repositories.isEmpty() && dependencies.isEmpty() && relocations.isEmpty();
        }

        private void clearEmpty() {
            if (repositories.isEmpty()) {
                repositories = null;
            }
            if (dependencies.isEmpty()) {
                dependencies = null;
            }
            if (relocations.isEmpty()) {
                relocations = null;
            }
        }
    }
}
