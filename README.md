<h1 align="center">Ezlib</h1>

<h4 align="center">The easy way to load dependencies at runtime.</h4>

<p align="center">
    <a href="https://www.codefactor.io/repository/github/saicone/ezlib">
        <img src="https://www.codefactor.io/repository/github/saicone/ezlib/badge?style=flat-square"/>
    </a>
    <a href="https://github.com/saicone/ezlib">
        <img src="https://img.shields.io/github/languages/code-size/saicone/ezlib?style=flat-square"/>
    </a>
    <a href="https://github.com/saicone/ezlib">
        <img src="https://img.shields.io/tokei/lines/github/saicone/ezlib?style=flat-square"/>
    </a>
    <a href="https://jitpack.io/#com.saicone/ezlib">
        <img src="https://jitpack.io/v/com.saicone/ezlib.svg?style=flat-square"/>
    </a>
</p>

Ezlib provides an easy methods to load all needed dependencies at runtime into class loaders.

```java
// Create ezlib with default "libs" folder
Ezlib ezlib = new Ezlib(); // Or specify a folder with new Ezlib(folder);

// Load from maven repository into child class loader
ezlib.load("commons-io:commons-io:2.11.0");

// Load from maven repository into parent class loader
ezlib.load("commons-io:commons-io:2.11.0", true);

// Load from specified repository
ezlib.load("com.saicone.rtag:rtag:1.1.0", "https://jitpack.io/");
```

## Get Ezlib

### Requirements
*  Minimum Java 8

### Project build
For Gradle Groovy project (build.gradle)
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.saicone.ezlib:ezlib:VERSION'
}
```

<details>
  <summary>For Gradle Kotlin project (build.gradle.kts)</summary>
  
  ```kotlin
  repositories {
      maven("https://jitpack.io")
  }

  dependencies {
      compileOnly("com.saicone.ezlib:ezlib:VERSION")
  }
  ```
</details>

<details>
  <summary>For Maven project (pom.xml)</summary>
  
  ```xml
  <repositories>
      <repository>
          <id>Jitpack</id>
          <url>https://jitpack.io</url>
      </repository>
  </repositories>
    
  <dependencies>
      <dependency>
          <groupId>com.saicone.ezlib</groupId>
          <artifactId>ezlib</artifactId>
          <version>VERSION</version>
      </dependency>
  </dependencies>
  ```
</details>

## Features

### Append into parent
Ezlib allow you to append dependencies into parent class loader just setting "true" at the final on load method.
```java
Ezlib ezlib = new Ezlib();

// Load from maven repository
ezlib.load("commons-io:commons-io:2.11.0", true);

// Load from specified repository
ezlib.load("com.saicone.rtag:rtag:1.1.0", "https://jitpack.io/", true);
```

### Package relocation
You can load dependencies with package relocation.
Here an example with Redis library and all the needed dependencies.
```java
Map<String, String> relocations = new HashMap();
relocations.put("com.google.gson", "myproject.path.libs.gson");
relocations.put("org.apache.commons.pool2", "myproject.path.libs.pool2");
relocations.put("org.json", "myproject.path.libs.json");
relocations.put("org.slf4j", "myproject.path.libs.slf4j");
relocations.put("redis.clients.jedis", "myproject.path.libs.jedis");

Ezlib ezlib = new Ezlib();

// Load all the needed dependencies first
ezlib.load("com.google.gson:gson:2.8.9", relocations, true);
ezlib.load("org.apache.commons:commons-pool2:2.11.1", relocations, true);
ezlib.load("org.json:json:20211205", relocations, true);
ezlib.load("org.slf4j:slf4j-api:1.7.32", relocations, true);

// Then load redis dependency
ezlib.load("redis.clients:jedis:4.2.2", relocations, true);
```
