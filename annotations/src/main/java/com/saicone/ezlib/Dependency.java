package com.saicone.ezlib;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Dependencies.class)
public @interface Dependency {

    /**
     * Dependency path format.
     *
     * @return gradle-like path.
     */
    String value();

    /**
     * File name to save dependency information.
     *
     * @return file name.
     */
    String file() default "ezlib-dependencies.json";

    /**
     * Repository used for this dependency.<br>
     * If you already define a repository with name, you can only configure the name here.<br>
     * So, if this repository doesn't work, the dependency will be searched hover the other repositories.
     *
     * @return dependency repository.
     */
    Repository repository() default @Repository;

    /**
     * Load the dependency into child ClassLoader or not.
     *
     * @return true if this is an inner dependency.
     */
    boolean inner() default false;

    /**
     * Load all the needed dependencies defined in pom file.
     *
     * @return true if all sub dependencies will be loaded.
     */
    boolean transitive() default true;

    /**
     * Whether this dependency is a snapshot-like version.<br>
     * If true will be downloaded the last snapshot.
     *
     * @return true is this dependency uses snapshot version.
     */
    boolean snapshot() default false;

    /**
     * Load optional dependencies from pom file.
     *
     * @return true if optional sub dependencies will be loaded.
     */
    boolean loadOptional() default false;

    /**
     * Ignore this dependency if cannot be loaded.
     *
     * @return true if this dependency loading doesn't affect the current program.
     */
    boolean optional() default false;

    /**
     * Test the provided classes, if all classes exist the dependency will be ignored.<br>
     * So you can add "!" to make an inverse check for the class name.
     *
     * @return an array containing classes names.
     */
    String[] test() default {};

    /**
     * Conditional string to check the dependency need to be downloaded.
     *
     * @return an array of conditions to load the dependency.
     */
    String[] condition() default {};

    /**
     * Exclude needed dependencies using gradle-like path.<br>
     * You can use paths without version or only dependency group.
     *
     * @return an array of gradle-like paths to exclude sub dependencies.
     */
    String[] exclude() default {};

    /**
     * Apply relocations after load this dependency into class loader.
     *
     * @return class pattern relocations as array.
     */
    String[] relocate() default {};
}
