---
sidebar_position: 2
title: Usage
description: How to use Ezlib
---

:::info

Ezlib uses a gradle-like dependency format to load them.

:::

## Basic

Ezlib provides an easy method to load all needed dependencies at runtime into class loaders.

Here is an example to load dependencies into a child class loader.

```java
// Create ezlib with default "libs" folder
Ezlib ezlib = new Ezlib();
// Or specify a folder
Ezlib ezlib = new Ezlib(new File("folder/path"));

// Initialize ezlib
ezlib.init();

// Load from maven repository into child class loader
ezlib.dependency("commons-io:commons-io:2.11.0").load();

// Load from specified repository
ezlib.dependency("com.saicone.rtag:rtag:1.3.0").repository("https://jitpack.io/").load();

// You can change default repository
ezlib.setDefaultRepository("repo URL");
```

## Parent ClassLoader

Ezlib allow you to append dependencies into parent class loader and specify repository before load method.

```java
Ezlib ezlib = new Ezlib();
// Initialize ezlib
ezlib.init();

// Load from maven repository into parent class loader
ezlib.dependency("commons-io:commons-io:2.11.0").parent(true).load();

// Load from specified repository
ezlib.dependency("com.saicone.rtag:rtag:1.1.0")
        .repository("https://jitpack.io/")
        .parent(false)
        .load();
```

## Relocation

Ezlib uses [jar-relocator](https://github.com/lucko/jar-relocator), so you can load dependencies with package relocation.

Here an example with Redis library and all the needed dependencies.

```java
Map<String, String> map = new HashMap();
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

:::warning

Make sure to relocate the imports during compile time, while excluding the class that you use to load the dependencies because the strings will be relocated too.

:::