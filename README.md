<h1 align="center">Ezlib</h1>

<h4 align="center">The easy way to load dependencies at runtime.</h4>

<p align="center">
    <a href="https://saic.one/discord">
        <img src="https://img.shields.io/discord/974288218839191612.svg?style=flat-square&label=discord&logo=discord&logoColor=white&color=7289da"/>
    </a>
    <a href="https://www.codefactor.io/repository/github/saicone/ezlib">
        <img src="https://www.codefactor.io/repository/github/saicone/ezlib/badge?style=flat-square"/>
    </a>
    <a href="https://github.com/saicone/ezlib">
        <img src="https://img.shields.io/github/languages/code-size/saicone/ezlib?style=flat-square"/>
    </a>
    <a href="https://jitpack.io/#com.saicone/ezlib">
        <img src="https://jitpack.io/v/com.saicone/ezlib.svg?style=flat-square"/>
    </a>
    <a href="https://javadoc.saicone.com/ezlib/">
        <img src="https://img.shields.io/badge/JavaDoc-Online-green?style=flat-square"/>
    </a>
    <a href="https://docs.saicone.com/ezlib/">
        <img src="https://img.shields.io/badge/Saicone-Ezlib%20Wiki-3b3bb0?logo=github&logoColor=white&style=flat-square"/>
    </a>
</p>

Ezlib provides an easy methods to load all needed dependencies at runtime into class loaders.

```java
// Create ezlib with default "libs" folder
Ezlib ezlib = new Ezlib(); // Or specify a folder with new Ezlib(folder);
// Initialize ezlib
ezlib.init();

// Load from maven repository into child class loader
ezlib.dependency("commons-io:commons-io:2.11.0").load();

// Load from maven repository into parent class loader
ezlib.dependency("commons-io:commons-io:2.11.0").parent(true).load();

// Load from specified repository
ezlib.dependency("com.saicone.rtag:rtag:1.3.0").repository("https://jitpack.io/").load();
```

## Dependency

How to implement ezlib in your project.

This library contains the following artifacts:

* `ezlib` - The main project, supports:
  1. Load dependencies from repositories.
  2. Dependency relocation.
  3. Dependency file mapper, for example, apply transformations to source code before appending it to classpath.
* `loader` - An extensive library loader (that also use `ezlib` module), supports:
  1. Transitive dependencies.
  2. Snapshot dependencies.
  3. Optional dependencies.
  4. Dependency scope filter.
  5. Testing dependency classes before loading.
  6. Dependency version provided by path, for example, use `@latest` to get the latest version from `maven-metadata.xml`.
  7. Dependency loading from configuration file (JSON and YAML are supported by default).
  8. Dependency loading from class constant declaration.
  9. Conditional functions to filter dependencies based on runtime variables, for example, use `java.version >= 17` to only load on Java +17.
* `annotations` - An annotation processor to declare dependencies and generate resources for `loader` module.
* `internal` - The internal code that actually load the classes on runtime, not made for users.

Project build examples:

<details>
  <summary>build.gradle</summary>

```groovy
plugins {
    id 'com.gradleup.shadow' version '9.3.1'
}

repositories {
    maven { url = uri('https://jitpack.io') }
}

dependencies {
    implementation 'com.saicone.ezlib:ezlib:1.3.3'
    implementation 'com.saicone.ezlib:loader:1.3.3'
    // The annotation module should be implemented as annotation processor
    compileOnly 'com.saicone.ezlib:annotations:1.3.3'
    annotationProcessor 'com.saicone.ezlib:annotations:1.3.3'
}

jar.dependsOn (shadowJar)

shadowJar {
    relocate 'com.saicone.ezlib', project.group + '.ezlib'
}
```
</details>

<details>
  <summary>build.gradle.kts</summary>

```kotlin
plugins {
    id("com.gradleup.shadow") version "9.3.1"
}

repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.saicone.ezlib:ezlib:1.3.3")
    implementation("com.saicone.ezlib:loader:1.3.3")
    // The annotation module should be implemented as annotation processor
    compileOnly("com.saicone.ezlib:annotations:1.3.3")
    annotationProcessor("com.saicone.ezlib:annotations:1.3.3")
}

tasks {
    jar {
        dependsOn(tasks.shadowJar)
    }

    shadowJar {
        relocate("com.saicone.ezlib", "${project.group}.ezlib")
    }
}
```
</details>

<details>
  <summary>pom.xml</summary>

```xml
<repositories>
    <repository>
        <id>Jitpack</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
<!-- Use ezlib -->
<dependency>
    <groupId>com.saicone.ezlib</groupId>
    <artifactId>ezlib</artifactId>
    <version>1.3.3</version>
    <scope>compile</scope>
</dependency>
<!-- Use ezlib loader -->
<dependency>
    <groupId>com.saicone.ezlib</groupId>
    <artifactId>loader</artifactId>
    <version>1.3.3</version>
    <scope>compile</scope>
</dependency>
<!-- Use annotations -->
<dependency>
    <groupId>com.saicone.ezlib</groupId>
    <artifactId>annotations</artifactId>
    <version>1.3.3</version>
    <scope>provided</scope>
</dependency>
</dependencies>

<build>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.1</version>
    <configuration>
        <artifactSet>
            <includes>
                <include>com.saicone.ezlib:ezlib</include>
                <include>com.saicone.ezlib:loader</include>
            </includes>
        </artifactSet>
        <relocations>
            <relocation>
                <pattern>com.saicone.ezlib</pattern>
                <shadedPattern>${project.groupId}.ezlib</shadedPattern>
            </relocation>
        </relocations>
    </configuration>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
        </execution>
    </executions>
</plugin>
</build>
```
</details>

## Features

### Easy dependency builder
Ezlib allow you to append dependencies into parent class loader and specify repository before load method.
```java
Ezlib ezlib = new Ezlib();
ezlib.init();

// Load from maven repository
ezlib.dependency("commons-io:commons-io:2.11.0").parent(true).load();

// Load from specified repository
ezlib.dependency("com.saicone.rtag:rtag:1.1.0")
        .repository("https://jitpack.io/")
        .parent(false)
        .load();
```

### Package relocation
Ezlib uses [jar-relocator](https://github.com/lucko/jar-relocator), so you can load dependencies with package relocation.

Here an example with Redis library and all the needed dependencies.
```java
Map<String, String> map = new HashMap<>();
map.put("com.google.gson", "myproject.path.libs.gson");
map.put("org.apache.commons.pool2", "myproject.path.libs.pool2");
map.put("org.json", "myproject.path.libs.json");
map.put("org.slf4j", "myproject.path.libs.slf4j");
map.put("redis.clients.jedis", "myproject.path.libs.jedis");

Ezlib ezlib = new Ezlib();
ezlib.init();

// Load all the needed dependencies first
ezlib.dependency("com.google.gson:gson:2.8.9").relocations(map).parent(true).load();
ezlib.dependency("org.apache.commons:commons-pool2:2.11.1").relocations(map).parent(true).load();
ezlib.dependency("org.json:json:20211205").relocations(map).parent(true).load();
ezlib.dependency("org.slf4j:slf4j-api:1.7.32").relocations(map).parent(true).load();

// Then load redis dependency
ezlib.dependency("redis.clients:jedis:4.2.2").relocations(map).parent(true).load();
```

### Dependency loader
Ezlib loader is the easier way to load dependencies and all the needed sub dependencies, so you can use it with annotations.

For example, if MyObject need the redis library:
```java
// Use plain annotation
@Dependency("redis.clients:jedis:4.2.2")
public class MyObject {
}

// Use with relocations
@Dependency(value = "redis.clients:jedis:4.2.2",
        relocations = {
                "com.google.gson", "myproject.path.libs.gson",
                "org.apache.commons.pool2", "myproject.path.libs.pool2",
                "org.json", "myproject.path.libs.json",
                "org.slf4j", "myproject.path.libs.slf4j",
                "redis.clients.jedis", "myproject.path.libs.jedis"
        }
)
public class MyObject {
}
```

Then execute ezlib loader on project initialization, all the needed dependencies will be loaded by default.
```java
public class Main {
    public static void main(String[] args) {
        new EzlibLoader().load();
    }
}
```
