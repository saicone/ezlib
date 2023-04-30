package com.saicone.ezlib;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Repository {

    /**
     * Repository name.
     *
     * @return repository unique name.
     */
    String name() default "";

    /**
     * Repository url.
     *
     * @return repository connection url.
     */
    String url() default "";

    /**
     * Url download format.
     *
     * @return url format to download files from repository.
     */
    String format() default "%group%/%artifact%/%version%/%artifact%-%fileVersion%.%fileType%";

    /**
     * File name to save repository information.
     *
     * @return file name.
     */
    String file() default "ezlib-dependencies.json";

    /**
     * Allow downloads from repository with insecure protocol.<br>
     * Take in count this value doesn't hide the warning when insecure protocol is used.
     *
     * @return true if this repository will be used despite using an insecure protocol.
     */
    boolean allowInsecureProtocol() default false;
}
