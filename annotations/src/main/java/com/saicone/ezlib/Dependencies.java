package com.saicone.ezlib;

import java.lang.annotation.*;

/**
 * Specifies the needed dependencies to run the actual project using global values like repositories and relocations.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Dependencies {

    /**
     * Dependencies to load.
     *
     * @return an array of dependencies.
     */
    Dependency[] value();

    /**
     * Global repositories.
     *
     * @return an array of repositories.
     */
    Repository[] repositories() default {};

    /**
     * Global relocations.
     *
     * @return class pattern relocations as array.
     */
    String[] relocations() default {};

    /**
     * File name to save dependencies information.
     *
     * @return file name.
     */
    String file() default "ezlib-dependencies.json";
}
